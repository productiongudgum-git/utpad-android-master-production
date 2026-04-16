package com.example.gudgum_prod_flow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen success overlay shown after any wizard submission.
 *
 * Animation sequence (total visible time = 2 000 ms):
 *   t=0     : blue circle scales in from 0 → 1 (spring with medium bounce)
 *   t=300ms : white tick draws itself along the path (stroke animation, 450 ms)
 *   t=2000ms: [onDismiss] is called — caller should clear submit state + reset wizard
 */
@Composable
fun SuccessOverlay(onDismiss: () -> Unit) {
    val circleScale = remember { Animatable(0f) }
    val tickProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Circle and tick run in parallel; the parent coroutine drives the 2-second timer.
        launch {
            circleScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
        launch {
            delay(300)
            tickProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450),
            )
        }
        delay(2000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val cs = circleScale.value   // 0 → 1 (with overshoot)
                val tp = tickProgress.value  // 0 → 1

                val radius = size.minDimension / 2f

                // ── Blue circle — radius scaled so it grows from the centre ──
                drawCircle(color = UtpadPrimary, radius = radius * cs)

                // ── White tick — draws itself incrementally along the path ──
                if (tp > 0f && cs > 0f) {
                    val strokeWidth = radius * 0.14f

                    // Three keypoints of the checkmark, relative to canvas centre.
                    // p1 → p2 : short downward stroke (left side of tick)
                    // p2 → p3 : longer upward stroke  (right side of tick)
                    val p1 = Offset(center.x - radius * 0.32f, center.y + radius * 0.04f)
                    val p2 = Offset(center.x - radius * 0.04f, center.y + radius * 0.34f)
                    val p3 = Offset(center.x + radius * 0.38f, center.y - radius * 0.24f)

                    val firstLen  = (p2 - p1).getDistance()
                    val secondLen = (p3 - p2).getDistance()
                    val totalLen  = firstLen + secondLen
                    val drawn     = tp * totalLen

                    val path = Path()
                    path.moveTo(p1.x, p1.y)
                    if (drawn <= firstLen) {
                        val t = drawn / firstLen
                        path.lineTo(
                            p1.x + (p2.x - p1.x) * t,
                            p1.y + (p2.y - p1.y) * t,
                        )
                    } else {
                        path.lineTo(p2.x, p2.y)
                        val t = (drawn - firstLen) / secondLen
                        path.lineTo(
                            p2.x + (p3.x - p2.x) * t,
                            p2.y + (p3.y - p2.y) * t,
                        )
                    }

                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }

            Text(
                text = "Submitted Successfully!",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
