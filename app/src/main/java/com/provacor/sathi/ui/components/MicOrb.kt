package com.provacor.sathi.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.provacor.sathi.R

/**
 * The microphone. Ripples follow the voice level while listening; a teal arc
 * turns while the agent works. Animation runs only in those two states, and
 * not at all when the system has animations switched off.
 */
@Composable
fun MicOrb(
    listening: Boolean,
    working: Boolean,
    level: Float,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 232.dp,
) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = rememberReducedMotion()
    val smoothLevel by animateFloatAsState(if (listening) level else 0f, tween(120), label = "level")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(
                Brush.radialGradient(
                    listOf(colors.primary.copy(alpha = if (listening) 0.30f else 0.16f), Color.Transparent),
                    center = center,
                    radius = this.size.minDimension / 2,
                ),
            )
        }
        if ((listening || working) && !reduceMotion) {
            ActiveRings(listening, working, smoothLevel, size)
        } else if (working) {
            Canvas(Modifier.size(size)) {
                val r = this.size.minDimension * 0.36f
                drawArc(
                    colors.secondary, -90f, 100f, false,
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        val scale = 1f + smoothLevel * 0.10f
        Box(
            Modifier
                .size(size * 0.56f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(lerp(colors.primary, Color.White, 0.22f), colors.primary)))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(size * 0.22f),
            )
        }
    }
}

@Composable
private fun ActiveRings(listening: Boolean, working: Boolean, level: Float, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "orb")
    val ripple by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ripple")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")

    Canvas(Modifier.size(size)) {
        val base = this.size.minDimension * 0.28f
        if (listening) {
            for (i in 0 until 3) {
                val p = (ripple + i / 3f) % 1f
                drawCircle(
                    color = colors.primary.copy(alpha = (1f - p) * 0.5f),
                    radius = base * (1f + p * (0.45f + level * 0.55f)),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
        if (working) {
            val r = this.size.minDimension * 0.36f
            drawArc(
                colors.secondary, spin, 100f, false,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
