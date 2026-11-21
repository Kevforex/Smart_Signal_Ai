package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoSmallStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun EquityCurveChart(
    equityCurve: List<Double>,
    initialCapital: Double,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val finalEquity = equityCurve.lastOrNull() ?: initialCapital
    val curveColor = if (finalEquity >= initialCapital) tradingColors.bull else tradingColors.bear

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tradingColors.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Courbe d'équité (après coûts estimés)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format(Locale.FRANCE, "%.2f", finalEquity),
                    style = MonoSmallStyle,
                    color = curveColor
                )
            }

            if (equityCurve.size < 2) {
                Text(
                    text = "Pas assez de transactions pour tracer la courbe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val rawMin = min(equityCurve.min(), initialCapital)
                val rawMax = max(equityCurve.max(), initialCapital)
                val isFlat = (rawMax - rawMin) < 1e-6
                val minEq = if (isFlat) rawMin - 1.0 else rawMin
                val maxEq = if (isFlat) rawMax + 1.0 else rawMax
                val span = (maxEq - minEq).coerceAtLeast(1.0)
                val baselineColor = tradingColors.border

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (equityCurve.size - 1).coerceAtLeast(1)

                    fun yFor(eq: Double): Float {
                        val norm = ((eq - minEq) / span).toFloat()
                        return h * (1f - norm * 0.85f - 0.075f)
                    }

                    // Initial capital baseline
                    val baseY = yFor(initialCapital)
                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, baseY),
                        end = Offset(w, baseY),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    val strokePath = Path()
                    val fillPath = Path()
                    equityCurve.forEachIndexed { idx, value ->
                        val x = idx * stepX
                        val y = yFor(value)
                        if (idx == 0) {
                            strokePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            strokePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(w, h)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(curveColor.copy(alpha = 0.28f), curveColor.copy(alpha = 0.0f))
                        )
                    )
                    drawPath(
                        path = strokePath,
                        color = curveColor,
                        style = Stroke(width = 3f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Min : ${String.format(Locale.FRANCE, "%.2f", rawMin)}",
                        style = MonoSmallStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Départ : ${String.format(Locale.FRANCE, "%.2f", initialCapital)}",
                        style = MonoSmallStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Max : ${String.format(Locale.FRANCE, "%.2f", rawMax)}",
                        style = MonoSmallStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
