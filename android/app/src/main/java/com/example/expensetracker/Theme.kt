package com.example.expensetracker

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// The long, soft "ease out expo" curve you see on apple.com
val Expo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

val Blue = Color(0xFF0A84FF)
val Indigo = Color(0xFF5E5CE6)
val Purple = Color(0xFFBF5AF2)
private val CategoryPalette = listOf(
    Color(0xFF0A84FF), Color(0xFF30D158), Color(0xFFFF9F0A),
    Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFF64D2FF)
)
fun categoryColor(c: String): Color =
    if (c.isBlank()) Color(0xFF8E8E93) else CategoryPalette[(c.trim().lowercase().hashCode() and 0x7fffffff) % CategoryPalette.size]

private val LightColors = lightColorScheme(
    primary = Color(0xFF0071E3), onPrimary = Color.White,
    background = Color(0xFFF5F5F7), onBackground = Color(0xFF1D1D1F),
    surface = Color.White, onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFEAEAEE), onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFD2D2D7)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF2997FF), onPrimary = Color.White,
    background = Color(0xFF000000), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989D),
    outline = Color(0xFF3A3A3C)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Springy press feedback: shrinks on touch, bounces back on release. */
fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press"
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Fades and slides content up after a small delay, so lists and forms "build in". */
@Composable
fun Staggered(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(70L * index); visible = true }
    AnimatedVisibility(
        visible, modifier,
        enter = fadeIn(tween(600, easing = Expo)) + slideInVertically(tween(800, easing = Expo)) { it / 3 }
    ) { content() }
}

/** Slowly drifting blue and purple glow, like the hero sections on apple.com. */
@Composable
fun Modifier.ambient(): Modifier {
    val t = rememberInfiniteTransition(label = "ambient")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0f, infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    return drawBehind {
        val w = size.width; val h = size.height
        val c1 = Offset(w * (0.1f + 0.6f * a), h * (0.12f + 0.12f * b))
        val c2 = Offset(w * (0.9f - 0.6f * b), h * (0.85f - 0.12f * a))
        drawCircle(Brush.radialGradient(listOf(Blue.copy(alpha = 0.30f), Color.Transparent), center = c1, radius = w * 0.85f), w * 0.85f, c1)
        drawCircle(Brush.radialGradient(listOf(Purple.copy(alpha = 0.26f), Color.Transparent), center = c2, radius = w * 0.9f), w * 0.9f, c2)
    }
}

@Composable
fun PillButton(
    text: String, modifier: Modifier = Modifier, filled: Boolean = true,
    busy: Boolean = false, enabled: Boolean = true, onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (filled) scheme.primary else scheme.surfaceVariant
    val fg = if (filled) Color.White else scheme.onSurface
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(300), label = "alpha")
    Box(
        modifier.graphicsLayer { this.alpha = alpha }
            .pressScale { if (enabled && !busy) onClick() }
            .clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(busy, label = "busy", transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }) { b ->
            if (b) CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.dp)
            else Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun SmallChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.pressScale(onClick).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
fun AppField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    password: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text
) {
    val s = MaterialTheme.colorScheme
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = s.surface, unfocusedContainerColor = s.surface,
            unfocusedBorderColor = Color.Transparent, focusedBorderColor = s.primary
        )
    )
}
