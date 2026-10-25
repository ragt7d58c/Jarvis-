package com.example.ui.hud

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrightCyan
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepNavyBackground
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlowCyan
import com.example.ui.theme.HologramCyanBright
import com.example.ui.theme.HologramCyanDim
import com.example.ui.theme.HologramCyanMedium
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextCyanPrimary
import com.example.ui.theme.TextCyanSecondary
import com.example.voice.JarvisVoiceState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun JarvisHud(
    state: JarvisVoiceState,
    audioRms: Float,
    onHudTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_rotations")

    // Dynamic rotation speeds based on state
    val isProcessing = state is JarvisVoiceState.Processing
    val isSpeaking = state is JarvisVoiceState.Speaking
    val isWaking = state is JarvisVoiceState.Waking

    val rotationSlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProcessing) 4000 else 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_slow"
    )

    val rotationReverse by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProcessing) 3000 else 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_reverse"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWaking || isSpeaking) 500 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val scanSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProcessing) 1500 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_sweep"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        DarkNavySurface,
                        Color(0xFF040E1B),
                        DeepNavyBackground
                    )
                )
            )
            .testTag("jarvis_hud_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Sci-Fi Telemetry Top Bar (Minimalist peripheral indicators, strictly no chat/subtitles)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state is JarvisVoiceState.Error) Color(0xFFFF5252) else StatusGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SYSTEM // ONLINE",
                    color = TextCyanSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "CORE: 100%",
                    color = BrightCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Minimalist Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.statusLabel,
                    color = when (state) {
                        is JarvisVoiceState.Waking -> ElectricCyan
                        is JarvisVoiceState.ListeningCommand -> GlowCyan
                        is JarvisVoiceState.Processing -> BrightCyan
                        is JarvisVoiceState.Speaking -> ElectricCyan
                        is JarvisVoiceState.Error -> Color(0xFFFF5252)
                        else -> TextCyanSecondary
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "TAP HUD: MANUAL WAKE",
                    color = TextCyanSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        // Circular Interactive HUD Area
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onHudTap
                ),
            contentAlignment = Alignment.Center
        ) {
            val canvasSizePx = min(constraints.maxWidth, constraints.maxHeight).toFloat()
            val baseRadius = (canvasSizePx * 0.44f).coerceAtLeast(140f)

            Canvas(
                modifier = Modifier
                    .size((canvasSizePx / 2.7f).dp)
                    .fillMaxSize()
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // 1. Radar Scanning Sweep (Active in Processing and Standby)
                rotate(scanSweepAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.75f to Color.Transparent,
                            1.0f to if (isProcessing) HologramCyanMedium else HologramCyanDim
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                        size = Size(baseRadius * 2f, baseRadius * 2f)
                    )
                }

                // 2. Outermost Segmented Arc Ring (Rotates slowly clockwise)
                rotate(rotationSlow, pivot = center) {
                    drawOutermostSegmentedRing(center, baseRadius, audioRms)
                }

                // 3. Middle Dash and Dot Array Ring (Rotates counter-clockwise)
                rotate(rotationReverse, pivot = center) {
                    drawMiddleDottedRing(center, baseRadius * 0.86f, audioRms)
                }

                // 4. Tachometer Radial Tick Ring (120 fine radial ticks)
                drawTachometerTicks(center, baseRadius * 0.72f, baseRadius * 0.77f)

                // 5. Dynamic Energy Arc Gauge (Reacts directly to real-time audio RMS / speech volume)
                rotate(-rotationSlow * 1.5f, pivot = center) {
                    drawDynamicEnergyGauge(center, baseRadius * 0.65f, audioRms, isSpeaking)
                }

                // 6. Glowing Inner Ring Surrounding "J.A.R.V.I.S"
                val innerRadius = baseRadius * 0.44f * (if (isWaking || isSpeaking) corePulse else 1.0f)
                drawInnerCoreRings(center, innerRadius, isProcessing, isSpeaking, audioRms)
            }

            // Central "J.A.R.V.I.S" typography centered inside glowing core
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = "J.A.R.V.I.S",
                    color = when (state) {
                        is JarvisVoiceState.Waking -> Color.White
                        is JarvisVoiceState.Speaking -> GlowCyan
                        is JarvisVoiceState.ListeningCommand -> ElectricCyan
                        else -> BrightCyan
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Draws the outermost segmented rings and peripheral tick brackets.
 */
private fun DrawScope.drawOutermostSegmentedRing(
    center: Offset,
    radius: Float,
    audioRms: Float
) {
    val strokeWidth = 2.5f
    val glowColor = ElectricCyan.copy(alpha = (0.5f + audioRms * 0.5f).coerceIn(0.4f, 1f))

    // Broken arc segments
    val segments = 8
    val arcAngle = 32f
    val gap = (360f - (segments * arcAngle)) / segments

    for (i in 0 until segments) {
        val start = i * (arcAngle + gap)
        drawArc(
            color = glowColor,
            startAngle = start,
            sweepAngle = arcAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }

    // Outer corner bracket markers
    val bracketRadius = radius * 1.06f
    for (angle in listOf(45f, 135f, 225f, 315f)) {
        val rad = Math.toRadians(angle.toDouble())
        val x = (center.x + bracketRadius * cos(rad)).toFloat()
        val y = (center.y + bracketRadius * sin(rad)).toFloat()
        drawCircle(
            color = GlowCyan,
            radius = 3.5f,
            center = Offset(x, y)
        )
    }
}

/**
 * Draws the middle dashed ring and groups of futuristic telemetry dots (like 0000000000 in reference image).
 */
private fun DrawScope.drawMiddleDottedRing(
    center: Offset,
    radius: Float,
    audioRms: Float
) {
    val strokeWidth = 2.0f

    // Concentric dashed ring
    drawCircle(
        color = BrightCyan.copy(alpha = 0.55f),
        radius = radius,
        center = center,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 14f), 0f)
        )
    )

    // Telemetry dot arrays along the perimeter
    val dotCount = 14
    val dotArcSpan = 40.0
    val startAngle = 160.0
    for (i in 0 until dotCount) {
        val angleDeg = startAngle + (i.toDouble() / (dotCount - 1)) * dotArcSpan
        val rad = Math.toRadians(angleDeg)
        val x = (center.x + (radius * 0.94f) * cos(rad)).toFloat()
        val y = (center.y + (radius * 0.94f) * sin(rad)).toFloat()
        drawCircle(
            color = if (i % 2 == 0) GlowCyan else BrightCyan.copy(alpha = 0.6f),
            radius = 2.2f,
            center = Offset(x, y)
        )
    }

    // Opposite telemetry dot array
    val startAngle2 = 340.0
    for (i in 0 until dotCount) {
        val angleDeg = startAngle2 + (i.toDouble() / (dotCount - 1)) * dotArcSpan
        val rad = Math.toRadians(angleDeg)
        val x = (center.x + (radius * 0.94f) * cos(rad)).toFloat()
        val y = (center.y + (radius * 0.94f) * sin(rad)).toFloat()
        drawCircle(
            color = if (i % 2 == 0) GlowCyan else BrightCyan.copy(alpha = 0.6f),
            radius = 2.2f,
            center = Offset(x, y)
        )
    }
}

/**
 * Draws the 120 radial tick marks resembling a tachometer / compass HUD.
 */
private fun DrawScope.drawTachometerTicks(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float
) {
    val totalTicks = 96
    val angleStep = 360f / totalTicks

    for (i in 0 until totalTicks) {
        val isMajor = (i % 8 == 0)
        val isMedium = (i % 4 == 0)
        val rIn = if (isMajor) innerRadius - 4f else if (isMedium) innerRadius - 1f else innerRadius
        val rad = Math.toRadians((i * angleStep).toDouble())

        val p1 = Offset(
            (center.x + rIn * cos(rad)).toFloat(),
            (center.y + rIn * sin(rad)).toFloat()
        )
        val p2 = Offset(
            (center.x + outerRadius * cos(rad)).toFloat(),
            (center.y + outerRadius * sin(rad)).toFloat()
        )

        val tickColor = if (isMajor) ElectricCyan else if (isMedium) BrightCyan.copy(alpha = 0.7f) else TextCyanSecondary.copy(alpha = 0.35f)
        val tickWidth = if (isMajor) 2.2f else 1.2f

        drawLine(
            color = tickColor,
            start = p1,
            end = p2,
            strokeWidth = tickWidth
        )
    }
}

/**
 * Draws dynamic curved energy gauges reacting directly to voice amplitude.
 */
private fun DrawScope.drawDynamicEnergyGauge(
    center: Offset,
    radius: Float,
    audioRms: Float,
    isSpeaking: Boolean
) {
    val baseSweep = if (isSpeaking) 110f else 60f + (audioRms * 90f)

    // Left dynamic arc
    drawArc(
        brush = Brush.sweepGradient(
            0.0f to ElectricCyan,
            0.5f to GlowCyan,
            1.0f to ElectricCyan
        ),
        startAngle = 140f,
        sweepAngle = baseSweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )

    // Right dynamic arc
    drawArc(
        color = BrightCyan.copy(alpha = 0.85f),
        startAngle = 320f,
        sweepAngle = baseSweep * 0.8f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
}

/**
 * Draws the glowing inner circle surrounding the J.A.R.V.I.S logo.
 */
private fun DrawScope.drawInnerCoreRings(
    center: Offset,
    radius: Float,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    audioRms: Float
) {
    // Soft outer glow halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ElectricCyan.copy(alpha = 0.25f + audioRms * 0.25f),
                BrightCyan.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = center,
            radius = radius * 1.55f
        ),
        radius = radius * 1.55f,
        center = center
    )

    // Glowing bright core ring
    drawCircle(
        color = ElectricCyan,
        radius = radius,
        center = center,
        style = Stroke(width = if (isSpeaking) 4.5f else 3.5f)
    )

    // Fine concentric ring just inside
    drawCircle(
        color = GlowCyan.copy(alpha = 0.6f),
        radius = radius * 0.92f,
        center = center,
        style = Stroke(width = 1.2f)
    )

    // Radial acoustic wave ripples when speaking or high mic level
    if (isSpeaking || audioRms > 0.4f) {
        val rippleRadius = radius * (1.15f + audioRms * 0.45f)
        drawCircle(
            color = GlowCyan.copy(alpha = (0.7f - (audioRms * 0.3f)).coerceIn(0.1f, 0.7f)),
            radius = rippleRadius,
            center = center,
            style = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            )
        )
    }
}
