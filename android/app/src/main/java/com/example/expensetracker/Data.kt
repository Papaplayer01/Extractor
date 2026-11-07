package com.example.expensetracker

import android.content.Context
import androidx.room.*
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// 10.0.2.2 = your computer's localhost as seen from the Android emulator.
// On a real phone, use your computer's LAN IP, e.g. "http://192.168.1.5:8000/".
const val BASE_URL = "http://10.0.2.2:8000/"

// ---------- Room (local cache) ----------
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Long? = null,
    val title: String,
    val amount: Double,
    val category: String,
    val date: String,
    val synced: Boolean = true,   // false = waiting to be pushed to the server
    val deleted: Boolean = false  // true = deleted offline, waiting to be pushed
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY date DESC, id DESC")
    fun observe(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE synced = 0")
    suspend fun pending(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: ExpenseEntity): Long

    @Insert
    suspend fun insertAll(list: List<ExpenseEntity>)

    @Delete
    suspend fun delete(e: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE synced = 1")
    suspend fun deleteSynced()

    @Query("DELETE FROM expenses")
    suspend fun clear()
}

@Database(entities = [ExpenseEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): ExpenseDao

    companion object {
        fun build(c: Context) = Room.databaseBuilder(c, AppDb::class.java, "expenses.db").build()
    }
}

// ---------- Retrofit (REST API) ----------
data class ExpenseDto(
    val id: Long? = null,
    val title: String,
    val amount: Double,
    val category: String,
    val date: String
)
data class LoginBody(val username: String, val password: String)
data class Tokens(val access: String)

interface Api {
    @POST("api/auth/login/") suspend fun login(@Body b: LoginBody): Tokens
    @POST("api/auth/register/") suspend fun register(@Body b: LoginBody): Response<Unit>
    @GET("api/expenses/") suspend fun list(): List<ExpenseDto>
    @POST("api/expenses/") suspend fun create(@Body d: ExpenseDto): ExpenseDto
    @PUT("api/expenses/{id}/") suspend fun update(@Path("id") id: Long, @Body d: ExpenseDto): ExpenseDto
    @DELETE("api/expenses/{id}/") suspend fun delete(@Path("id") id: Long): Response<Unit>
}

class TokenStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)
    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()
}

object Net {
    fun api(store: TokenStore): Api {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request().newBuilder()
            store.token?.let { req.header("Authorization", "Bearer $it") }
            chain.proceed(req.build())
        }.build()
        return Retrofit.Builder().baseUrl(BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(Api::class.java)
    }
}

// ---------- Repository: single source of truth is Room ----------
class Repo(private val dao: ExpenseDao, private val api: Api, private val store: TokenStore) {
    val expenses: Flow<List<ExpenseEntity>> = dao.observe()

    suspend fun login(u: String, p: String) {
        store.token = null
        store.token = api.login(LoginBody(u, p)).access
    }

    suspend fun register(u: String, p: String) {
        store.token = null
        if (!api.register(LoginBody(u, p)).isSuccessful) error("Registration failed (username taken?)")
    }

    suspend fun logout() {
        store.token = null
        dao.clear()
    }

    /** Writes always hit Room first so the app works offline, then we try to sync. */
    suspend fun save(e: ExpenseEntity) {
        dao.upsert(e.copy(synced = false))
        runCatching { sync() }
    }

    suspend fun remove(e: ExpenseEntity) {
        if (e.remoteId == null) dao.delete(e) else dao.upsert(e.copy(deleted = true, synced = false))
        runCatching { sync() }
    }

    /** Push offline changes, then pull the latest from the server. */
    suspend fun sync() {
        for (e in dao.pending()) {
            if (e.deleted) {
                e.remoteId?.let { api.delete(it) }
                dao.delete(e)
            } else {
                val dto = ExpenseDto(e.remoteId, e.title, e.amount, e.category, e.date)
                val saved = if (e.remoteId == null) api.create(dto) else api.update(e.remoteId, dto)
                dao.upsert(e.copy(remoteId = saved.id, synced = true))
            }
        }
        val remote = api.list()
        dao.deleteSynced()
        dao.insertAll(remote.map {
            ExpenseEntity(remoteId = it.id, title = it.title, amount = it.amount,
                category = it.category, date = it.date)
        })
    }
}
