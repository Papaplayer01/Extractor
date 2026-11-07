package com.example.expensetracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TokenStore(app)
    private val repo = Repo(AppDb.build(app).dao(), Net.api(store), store)

    val loggedIn = MutableStateFlow(store.token != null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val expenses = repo.expenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun auth(user: String, pass: String, register: Boolean) = viewModelScope.launch {
        busy.value = true
        try {
            if (register) repo.register(user, pass)
            repo.login(user, pass)
            loggedIn.value = true
        } catch (e: Exception) {
            message.value = if (e is HttpException && e.code() == 401) "Wrong username or password"
            else "Could not reach server: ${e.message}"
        } finally {
            busy.value = false
        }
    }

    fun refresh() = viewModelScope.launch {
        try {
            repo.sync()
        } catch (e: HttpException) {
            if (e.code() == 401) logout() else message.value = "Server error ${e.code()}"
        } catch (e: Exception) {
            message.value = "Offline. Showing saved data"
        }
    }

    fun save(e: ExpenseEntity) = viewModelScope.launch { repo.save(e) }
    fun delete(e: ExpenseEntity) = viewModelScope.launch { repo.remove(e) }
    fun logout() = viewModelScope.launch { repo.logout(); loggedIn.value = false }
}
