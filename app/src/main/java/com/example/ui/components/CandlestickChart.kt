package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.Candle
import com.example.domain.model.IndicatorSnapshot
import com.example.domain.model.SignalDirection
import com.example.domain.model.TradingSignal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoSmallStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CandlestickChart(
    symbol: String,
    candles: List<Candle>,
    snapshot: IndicatorSnapshot,
    activeSignal: TradingSignal?,
    showEma50: Boolean,
    showEma200: Boolean,
    showSupportResistance: Boolean,
    showSignalLevels: Boolean,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val textMeasurer = rememberTextMeasurer()

    var visibleCount by remember(symbol, candles.size) { mutableIntStateOf(72) }
    var endOffsetBars by remember(symbol, candles.size) { mutableFloatStateOf(0f) }
    var selectedLocalIndex by remember(symbol, candles.size) { mutableIntStateOf(-1) }

    val total = candles.size
    val safeVisible = visibleCount.coerceIn(24, max(24, min(220, total)))
    val maxOffset = max(0, total - safeVisible).toFloat()
    val clampedOffset = endOffsetBars.coerceIn(0f, maxOffset).roundToInt()

    val endIndex = (total - clampedOffset).coerceIn(0, total)
    val startIndex = (endIndex - safeVisible).coerceAtLeast(0)
    val visibleCandles = if (total > 0) candles.subList(startIndex, endIndex) else emptyList()

    // Map closed candle EMA series onto visible indices (open candle has null at tail if appended)
    val ema50Full = snapshot.ema50Series
    val ema200Full = snapshot.ema200Series

    val selectedCandle = if (selectedLocalIndex in visibleCandles.indices) {
        visibleCandles[selectedLocalIndex]
    } else {
        visibleCandles.lastOrNull()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Selected / Latest OHLC Crosshair Strip + Zoom Controls
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, tradingColors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedCandle != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        val tag = if (selectedCandle.isOpen) "BOUGIE OUVERTE (non confirmée)" else "Clôturée"
                        Text(
                            text = "${TimeUtils.formatLocalDisplay(selectedCandle.openTimeMs)} • $tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedCandle.isOpen) tradingColors.pending else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "O:${TimeUtils.formatPrice(symbol, selectedCandle.open)}  " +
                                "H:${TimeUtils.formatPrice(symbol, selectedCandle.high)}  " +
                                "L:${TimeUtils.formatPrice(symbol, selectedCandle.low)}  " +
                                "C:${TimeUtils.formatPrice(symbol, selectedCandle.close)}",
                            style = MonoSmallStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = "Aucune bougie disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { visibleCount = (visibleCount - 16).coerceAtLeast(24) }) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom avant", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { visibleCount = (visibleCount + 16).coerceAtMost(220) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom arrière", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = {
                        endOffsetBars = 0f
                        visibleCount = 72
                        selectedLocalIndex = -1
                    }) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Réinitialiser la vue", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Chart Canvas
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, tradingColors.border),
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
        ) {
            if (visibleCandles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "En attente des données OHLC Biquote...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val gridColor = tradingColors.border.copy(alpha = 0.55f)
                val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                val bullColor = tradingColors.bull
                val bearColor = tradingColors.bear
                val ema50Color = tradingColors.ema50
                val ema200Color = tradingColors.ema200
                val pendingColor = tradingColors.pending

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .pointerInput(visibleCandles.size, maxOffset) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val barPx = (size.width * 0.84f) / max(1, visibleCandles.size)
                                val deltaBars = dragAmount.x / max(4f, barPx)
                                endOffsetBars = (endOffsetBars + deltaBars).coerceIn(0f, maxOffset)
                            }
                        }
                        .pointerInput(visibleCandles.size) {
                            detectTapGestures { offset ->
                                val plotWidth = size.width * 0.84f
                                if (offset.x <= plotWidth && visibleCandles.isNotEmpty()) {
                                    val step = plotWidth / visibleCandles.size
                                    val idx = (offset.x / step).toInt().coerceIn(0, visibleCandles.lastIndex)
                                    selectedLocalIndex = idx
                                }
                            }
                        }
                ) {
                    val axisWidth = 64.dp.toPx()
                    val plotWidth = (size.width - axisWidth).coerceAtLeast(100f)
                    val plotHeight = size.height

                    var minPrice = visibleCandles.minOf { it.low }
                    var maxPrice = visibleCandles.maxOf { it.high }

                    if (showEma50) {
                        for (idx in startIndex until endIndex) {
                            ema50Full.getOrNull(idx)?.let {
                                minPrice = min(minPrice, it)
                                maxPrice = max(maxPrice, it)
                            }
                        }
                    }
                    if (showEma200) {
                        for (idx in startIndex until endIndex) {
                            ema200Full.getOrNull(idx)?.let {
                                minPrice = min(minPrice, it)
                                maxPrice = max(maxPrice, it)
                            }
                        }
                    }
                    if (showSignalLevels && activeSignal != null && activeSignal.direction != SignalDirection.NO_TRADE) {
                        listOfNotNull(
                            activeSignal.entryPrice,
                            activeSignal.stopLoss,
                            activeSignal.takeProfit1
                        ).forEach { lvl ->
                            // Include SL/TP1 if reasonably close so chart stays proportional
                            if (absRatio(lvl, visibleCandles.last().close) < 0.03) {
                                minPrice = min(minPrice, lvl)
                                maxPrice = max(maxPrice, lvl)
                            }
                        }
                    }

                    val pad = (maxPrice - minPrice).coerceAtLeast(0.0005) * 0.12
                    minPrice -= pad
                    maxPrice += pad
                    val range = (maxPrice - minPrice).coerceAtLeast(0.0001)

                    fun priceToY(p: Double): Float {
                        val norm = ((p - minPrice) / range).toFloat()
                        return plotHeight * (1f - norm)
                    }

                    // Horizontal Grid + Price Labels
                    val gridRows = 5
                    for (g in 0..gridRows) {
                        val frac = g.toFloat() / gridRows
                        val y = plotHeight * frac
                        val priceVal = maxPrice - frac * range
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(plotWidth, y),
                            strokeWidth = 1f
                        )
                        drawAxisLabel(
                            textMeasurer = textMeasurer,
                            text = TimeUtils.formatPrice(symbol, priceVal),
                            x = plotWidth + 4.dp.toPx(),
                            y = (y - 7.dp.toPx()).coerceIn(0f, plotHeight - 16.dp.toPx()),
                            color = labelTextColor
                        )
                    }

                    // Support & Resistance Lines
                    if (showSupportResistance) {
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        snapshot.supports.take(2).forEach { sup ->
                            if (sup.price in minPrice..maxPrice) {
                                val y = priceToY(sup.price)
                                drawLine(
                                    color = bullColor.copy(alpha = 0.45f),
                                    start = Offset(0f, y),
                                    end = Offset(plotWidth, y),
                                    strokeWidth = 1.5f,
                                    pathEffect = dashEffect
                                )
                            }
                        }
                        snapshot.resistances.take(2).forEach { res ->
                            if (res.price in minPrice..maxPrice) {
                                val y = priceToY(res.price)
                                drawLine(
                                    color = bearColor.copy(alpha = 0.45f),
                                    start = Offset(0f, y),
                                    end = Offset(plotWidth, y),
                                    strokeWidth = 1.5f,
                                    pathEffect = dashEffect
                                )
                            }
                        }
                    }

                    // Active Signal Levels (Entry, SL, TP1, TP2)
                    if (showSignalLevels && activeSignal != null && activeSignal.direction != SignalDirection.NO_TRADE) {
                        val levelLines = listOfNotNull(
                            activeSignal.entryPrice?.let { it to Color(0xFFF8FAFC) },
                            activeSignal.stopLoss?.let { it to bearColor },
                            activeSignal.takeProfit1?.let { it to bullColor },
                            activeSignal.takeProfit2?.let { it to bullColor.copy(alpha = 0.7f) }
                        )
                        val sigDash = PathEffect.dashPathEffect(floatArrayOf(14f, 6f), 0f)
                        levelLines.forEach { (lvl, col) ->
                            if (lvl in minPrice..maxPrice) {
                                val y = priceToY(lvl)
                                drawLine(
                                    color = col,
                                    start = Offset(0f, y),
                                    end = Offset(plotWidth, y),
                                    strokeWidth = 2f,
                                    pathEffect = sigDash
                                )
                            }
                        }
                    }

                    // Candlesticks
                    val stepX = plotWidth / visibleCandles.size
                    val bodyWidth = (stepX * 0.64f).coerceIn(2f, 16f)

                    visibleCandles.forEachIndexed { localIdx, candle ->
                        val centerX = localIdx * stepX + stepX / 2f
                        val isBull = candle.close >= candle.open
                        val baseColor = if (isBull) bullColor else bearColor
                        val candleColor = if (candle.isOpen) baseColor.copy(alpha = 0.55f) else baseColor

                        val highY = priceToY(candle.high)
                        val lowY = priceToY(candle.low)
                        val openY = priceToY(candle.open)
                        val closeY = priceToY(candle.close)

                        // Wick
                        drawLine(
                            color = candleColor,
                            start = Offset(centerX, highY),
                            end = Offset(centerX, lowY),
                            strokeWidth = max(1.2f, bodyWidth * 0.16f)
                        )

                        // Body
                        val topY = min(openY, closeY)
                        val bodyH = max(2f, kotlin.math.abs(closeY - openY))
                        drawRect(
                            color = candleColor,
                            topLeft = Offset(centerX - bodyWidth / 2f, topY),
                            size = Size(bodyWidth, bodyH)
                        )

                        if (localIdx == selectedLocalIndex) {
                            drawLine(
                                color = pendingColor.copy(alpha = 0.7f),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, plotHeight),
                                strokeWidth = 1.5f
                            )
                        }
                    }

                    // EMA 50 & EMA 200 Paths
                    if (showEma50) {
                        drawEmaPath(
                            series = ema50Full,
                            startIndex = startIndex,
                            count = visibleCandles.size,
                            stepX = stepX,
                            color = ema50Color,
                            priceToY = ::priceToY
                        )
                    }
                    if (showEma200) {
                        drawEmaPath(
                            series = ema200Full,
                            startIndex = startIndex,
                            count = visibleCandles.size,
                            stepX = stepX,
                            color = ema200Color,
                            priceToY = ::priceToY
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmaPath(
    series: List<Double?>,
    startIndex: Int,
    count: Int,
    stepX: Float,
    color: Color,
    priceToY: (Double) -> Float
) {
    val path = Path()
    var started = false
    for (localIdx in 0 until count) {
        val globalIdx = startIndex + localIdx
        val valPrice = series.getOrNull(globalIdx)
        if (valPrice != null) {
            val x = localIdx * stepX + stepX / 2f
            val y = priceToY(valPrice)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
    }
    if (started) {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.4f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxisLabel(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color
) {
    val result = textMeasurer.measure(
        text = text,
        style = TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            color = color
        )
    )
    drawText(result, topLeft = Offset(x, y))
}

private fun absRatio(a: Double, b: Double): Double {
    if (b == 0.0) return 0.0
    return kotlin.math.abs(a - b) / kotlin.math.abs(b)
}
