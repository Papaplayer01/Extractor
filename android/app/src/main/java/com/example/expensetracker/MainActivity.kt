package com.example.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { App() } }
    }
}

private fun blank() = ExpenseEntity(title = "", amount = 0.0, category = "", date = LocalDate.now().toString())

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val items by vm.expenses.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    var showEdit by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf(blank()) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(loggedIn) { if (loggedIn) vm.refresh() else showEdit = false }
    BackHandler(enabled = showEdit) { showEdit = false }

    // 0 = login, 1 = list, 2 = edit. Going "deeper" slides up; going back eases down.
    val screen = when { !loggedIn -> 0; showEdit -> 2; else -> 1 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        AnimatedContent(
            screen, Modifier.padding(pad).fillMaxSize(), label = "screen",
            transitionSpec = {
                if (targetState > initialState)
                    (fadeIn(tween(500, 120, Expo)) + slideInVertically(tween(700, easing = Expo)) { it / 8 } +
                        scaleIn(tween(700, easing = Expo), initialScale = 0.96f)) togetherWith
                        (fadeOut(tween(250)) + scaleOut(tween(600, easing = Expo), targetScale = 0.94f))
                else
                    (fadeIn(tween(450, 100, Expo)) + scaleIn(tween(600, easing = Expo), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(250)) + slideOutVertically(tween(600, easing = Expo)) { it / 8 })
            }
        ) { s ->
            when (s) {
                0 -> LoginScreen(busy, vm::auth)
                1 -> ListScreen(
                    items,
                    onAdd = { editTarget = blank(); showEdit = true },
                    onEdit = { editTarget = it; showEdit = true },
                    onDelete = { vm.delete(it) },
                    onRefresh = { vm.refresh() },
                    onLogout = { vm.logout() }
                )
                else -> EditScreen(editTarget, onSave = { vm.save(it); showEdit = false }, onCancel = { showEdit = false })
            }
        }
    }
}

@Composable
fun LoginScreen(busy: Boolean, onSubmit: (String, String, Boolean) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().ambient().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Staggered(0) {
            Text(
                "Expense\nTracker",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Blue, Indigo, Purple)),
                    fontSize = 52.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp
                )
            )
        }
        Staggered(1) {
            Text(
                "Track every rupee. Works offline.", fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 32.dp)
            )
        }
        Staggered(2) { AppField(user, { user = it }, "Username") }
        Spacer(Modifier.height(12.dp))
        Staggered(3) { AppField(pass, { pass = it }, "Password", password = true) }
        Spacer(Modifier.height(28.dp))
        Staggered(4) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Log in", Modifier.weight(1f), busy = busy) { onSubmit(user, pass, false) }
                PillButton("Register", Modifier.weight(1f), filled = false) { onSubmit(user, pass, true) }
            }
        }
    }
}

@Composable
fun ListScreen(
    items: List<ExpenseEntity>, onAdd: () -> Unit, onEdit: (ExpenseEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit, onRefresh: () -> Unit, onLogout: () -> Unit
) {
    // The total counts up smoothly whenever data changes.
    val total by animateFloatAsState(items.sumOf { it.amount }.toFloat(), tween(1000, easing = Expo), label = "total")
    val pending = items.count { !it.synced }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Staggered(0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Expenses", Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                        SmallChip("Sync", onRefresh)
                        Spacer(Modifier.width(8.dp))
                        SmallChip("Logout", onLogout)
                    }
                }
            }
            item { HeroCard(total, items.size, pending) }
            if (items.isEmpty()) item { EmptyState() }
            itemsIndexed(items, key = { _, e -> e.id }) { i, e ->
                ExpenseRow(i, e, onEdit, onDelete, Modifier.animateItem())
            }
        }
        AddButton(Modifier.align(Alignment.BottomEnd).padding(24.dp), onAdd)
    }
}

@Composable
fun HeroCard(total: Float, count: Int, pending: Int) {
    val t = rememberInfiniteTransition(label = "hero")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    Staggered(1) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .drawBehind {
                    drawRect(Brush.linearGradient(
                        listOf(Blue, Indigo, Purple),
                        start = Offset.Zero,
                        end = Offset(size.width * (0.6f + 0.8f * shift), size.height * 1.3f)
                    ))
                }
                .padding(24.dp)
        ) {
            Text("Total spent", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
            Text("₹" + "%,.2f".format(total), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "$count expenses  •  " + if (pending > 0) "$pending waiting to sync" else "all synced",
                color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseRow(index: Int, e: ExpenseEntity, onEdit: (ExpenseEntity) -> Unit, onDelete: (ExpenseEntity) -> Unit, modifier: Modifier) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(e); true } else false
    })
    Staggered(minOf(index, 8) + 2, modifier) {
        SwipeToDismissBox(
            state, enableDismissFromStartToEnd = false,
            backgroundContent = {
                if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Color(0xFFFF3B30)).padding(end = 24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) { Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
        ) {
            val color = categoryColor(e.category)
            Row(
                Modifier.fillMaxWidth().pressScale { onEdit(e) }
                    .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text(e.category.trim().firstOrNull()?.uppercase() ?: "•", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.category.ifBlank { "General" }} • ${e.date}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!e.synced) PendingDot()
                    }
                }
                Text("₹" + "%,.2f".format(e.amount), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PendingDot() {
    val t = rememberInfiniteTransition(label = "pending")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Spacer(Modifier.width(8.dp))
    Box(Modifier.size(7.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Color(0xFFFF9F0A)))
    Spacer(Modifier.width(4.dp))
    Text("Pending", fontSize = 12.sp, color = Color(0xFFFF9F0A))
}

@Composable
fun EmptyState() {
    val t = rememberInfiniteTransition(label = "float")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    Staggered(2) {
        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).graphicsLayer { translationY = bob * 10.dp.toPx() }
                    .clip(CircleShape).background(Brush.linearGradient(listOf(Blue, Purple))),
                contentAlignment = Alignment.Center
            ) { Text("₹", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(20.dp))
            Text("Start your first expense", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Tap + to add one. It saves even without internet.", textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun AddButton(modifier: Modifier, onClick: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(450); shown = true }
    val s by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "fab"
    )
    Box(
        modifier.graphicsLayer { scaleX = s; scaleY = s; rotationZ = (1f - s) * -90f }
            .shadow(14.dp, CircleShape)
            .pressScale(onClick).size(64.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Blue, Indigo))),
        contentAlignment = Alignment.Center
    ) { Text("+", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light) }
}

@Composable
fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) categoryColor(name) else scheme.surfaceVariant, tween(350), label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else scheme.onSurface, tween(350), label = "chipFg")
    Box(Modifier.pressScale(onClick).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(name, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EditScreen(initial: ExpenseEntity, onSave: (ExpenseEntity) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var amount by remember { mutableStateOf(if (initial.amount == 0.0) "" else initial.amount.toString()) }
    var category by remember { mutableStateOf(initial.category) }
    var date by remember { mutableStateOf(initial.date) }
    val amt = amount.toDoubleOrNull()
    val valid = title.isNotBlank() && amt != null

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Staggered(0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (initial.id == 0L) "New expense" else "Edit expense", Modifier.weight(1f),
                    fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp
                )
                SmallChip("Cancel", onCancel)
            }
        }
        Staggered(1) { AppField(title, { title = it }, "Title") }
        Staggered(2) { AppField(amount, { amount = it }, "Amount (₹)", keyboardType = KeyboardType.Decimal) }
        Staggered(3) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Food", "Travel", "Shopping", "Bills", "Fun").forEach { c ->
                    CategoryChip(c, category.equals(c, ignoreCase = true)) { category = c }
                }
            }
        }
        Staggered(4) { AppField(category, { category = it }, "Category") }
        Staggered(5) { AppField(date, { date = it }, "Date (YYYY-MM-DD)") }
        Spacer(Modifier.height(8.dp))
        Staggered(6) {
            PillButton("Save", Modifier.fillMaxWidth(), enabled = valid) {
                onSave(initial.copy(title = title.trim(), amount = amt!!, category = category.trim(), date = date.trim()))
            }
        }
    }
}
