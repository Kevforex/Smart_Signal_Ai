package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.FreshnessStatus
import com.example.domain.model.QuoteSnapshot
import com.example.domain.model.RiskCalculation
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
import com.example.domain.model.TradingSignal
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import java.util.Locale

@Composable
fun WeltradeNoticeBanner(modifier: Modifier = Modifier) {
    val tradingColors = LocalTradingColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, tradingColors.border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Information cotations",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Source publique Biquote — Cotations indicatives",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Les prix affichés proviennent de l'API publique Biquote et peuvent différer des cotations de Weltrade (spread et exécution). Aucun ordre automatique, aucune promesse de profit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ConnectionFreshnessBar(
    symbol: String,
    quote: QuoteSnapshot?,
    freshness: FreshnessStatus,
    isRefreshing: Boolean,
    lastFetchedMs: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val (dotColor, containerColor) = when (freshness) {
        FreshnessStatus.FRESH -> tradingColors.bull to tradingColors.bullContainer
        FreshnessStatus.MARKET_CLOSED, FreshnessStatus.INSUFFICIENT_DATA -> tradingColors.pending to tradingColors.pendingContainer
        FreshnessStatus.STALE, FreshnessStatus.ERROR -> tradingColors.bear to tradingColors.bearContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Column {
                    Text(
                        text = "$symbol • ${freshness.labelFr}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val ageText = if (quote != null) {
                        "Âge tick: ${quote.quoteAgeSeconds}s • Marché: ${quote.marketState.uppercase()} • MàJ: ${TimeUtils.formatShortTime(lastFetchedMs)}"
                    } else {
                        "Dernière tentative : ${TimeUtils.formatShortTime(lastFetchedMs)}"
                    }
                    Text(
                        text = ageText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("refresh_button")
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = dotColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualiser les données",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SignalDetailCard(
    signal: TradingSignal,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val (accentColor, bgColor, icon) = when (signal.direction) {
        SignalDirection.BUY -> Triple(
            if (signal.status == SignalStatus.CONFIRMED) tradingColors.bull else tradingColors.pending,
            if (signal.status == SignalStatus.CONFIRMED) tradingColors.bullContainer else tradingColors.pendingContainer,
            Icons.AutoMirrored.Filled.TrendingUp
        )
        SignalDirection.SELL -> Triple(
            if (signal.status == SignalStatus.CONFIRMED) tradingColors.bear else tradingColors.pending,
            if (signal.status == SignalStatus.CONFIRMED) tradingColors.bearContainer else tradingColors.pendingContainer,
            Icons.AutoMirrored.Filled.TrendingDown
        )
        SignalDirection.NO_TRADE -> Triple(
            tradingColors.mutedText,
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Default.Shield
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Direction + Status Pill + Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = signal.direction.labelFr,
                            tint = accentColor,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(26.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = signal.direction.labelFr,
                                style = MaterialTheme.typography.headlineMedium,
                                color = accentColor
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = bgColor
                            ) {
                                Text(
                                    text = signal.status.labelFr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "${signal.symbol} • Bougies clôturées M15 / H1",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Technical Score Pill
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${signal.technicalScore}/100",
                        style = MonoMediumStyle,
                        color = accentColor
                    )
                    Text(
                        text = "Score technique",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Score Progress & Mandatory disclaimer
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { (signal.technicalScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = signal.scoreLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "H1 : ${signal.h1TrendState.labelFr}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Note : Le score mesure la confluence technique, jamais une probabilité de réussite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (signal.direction != SignalDirection.NO_TRADE) {
                HorizontalDivider(color = tradingColors.border)

                // Entry / SL / TP1 / TP2 / R:R grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceMetricTile(
                        label = "Entrée indicative",
                        value = TimeUtils.formatPrice(signal.symbol, signal.entryPrice),
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    PriceMetricTile(
                        label = "Stop Loss",
                        value = TimeUtils.formatPrice(signal.symbol, signal.stopLoss),
                        accent = tradingColors.bear,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceMetricTile(
                        label = "Take Profit 1",
                        value = TimeUtils.formatPrice(signal.symbol, signal.takeProfit1),
                        accent = tradingColors.bull,
                        modifier = Modifier.weight(1f)
                    )
                    PriceMetricTile(
                        label = "Take Profit 2",
                        value = TimeUtils.formatPrice(signal.symbol, signal.takeProfit2),
                        accent = tradingColors.bull,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceMetricTile(
                        label = "Ratio R:R (TP1 / TP2)",
                        value = "1:${String.format(Locale.US, "%.2f", signal.riskRewardTp1 ?: 0.0)} / 1:${String.format(Locale.US, "%.2f", signal.riskRewardTp2 ?: 0.0)}",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    PriceMetricTile(
                        label = "Horodatage signal",
                        value = TimeUtils.formatLocalDisplay(signal.signalTimestampMs),
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (signal.reasons.isNotEmpty()) {
                    HorizontalDivider(color = tradingColors.border)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Raisons précises de la configuration",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        signal.reasons.forEach { reason ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(15.dp)
                                )
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                HorizontalDivider(color = tradingColors.border)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = tradingColors.pending,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "État sécurisé NO TRADE — Aucune entrée recommandée",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    signal.blockingReasons.forEach { block ->
                        Text(
                            text = "• $block",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Dernière évaluation : ${TimeUtils.formatLocalDisplay(signal.signalTimestampMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PriceMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, tradingColors.border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MonoSmallStyle.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }
}

@Composable
fun RiskCalculatorCard(
    symbol: String,
    capital: Double,
    riskPercent: Double,
    spreadPips: Double,
    entryInput: String,
    slInput: String,
    onCapitalChange: (Double) -> Unit,
    onRiskChange: (Double) -> Unit,
    onEntrySlChange: (String, String) -> Unit,
    calculation: RiskCalculation?,
    onSyncSignalLevels: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, tradingColors.border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calculateur de risque configurable",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Aucun lot minimum imposé — taille brute exacte",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (onSyncSignalLevels != null) {
                    Button(
                        onClick = onSyncSignalLevels,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("sync_calc_signal_button")
                    ) {
                        Text("Niveaux signal", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DecimalNumberField(
                    value = capital,
                    onValueCommitted = onCapitalChange,
                    label = "Capital (€/$)",
                    isIntegerHint = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_capital_input")
                )
                DecimalNumberField(
                    value = riskPercent,
                    onValueCommitted = onRiskChange,
                    label = "Risque (%)",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_risk_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = entryInput,
                    onValueChange = { onEntrySlChange(it, slInput) },
                    label = { Text("Entrée ($symbol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_entry_input")
                )
                OutlinedTextField(
                    value = slInput,
                    onValueChange = { onEntrySlChange(entryInput, it) },
                    label = { Text("Stop Loss") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_sl_input")
                )
            }

            if (calculation != null && calculation.stopLossPips > 0.0) {
                HorizontalDivider(color = tradingColors.border)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceMetricTile(
                        label = "Taille brute (lots)",
                        value = String.format(Locale.US, "%.4f lot", calculation.positionLotsRaw),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    PriceMetricTile(
                        label = "Volume (unités)",
                        value = "${calculation.positionUnits.toLong()} u.",
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceMetricTile(
                        label = "Risque max",
                        value = String.format(Locale.US, "%.2f", calculation.riskAmountCurrency),
                        accent = tradingColors.bear,
                        modifier = Modifier.weight(1f)
                    )
                    PriceMetricTile(
                        label = "Distance SL",
                        value = "${String.format(Locale.US, "%.1f", calculation.stopLossPips)} pips",
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "Spread pris en compte : ${String.format(Locale.US, "%.1f", spreadPips)} pips • R:R effectif TP1 : 1:${String.format(Locale.US, "%.2f", calculation.effectiveRiskRewardAfterSpread)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Saisissez un prix d'entrée et un Stop Loss différents pour calculer la taille exacte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DecimalNumberField(
    value: Double,
    onValueCommitted: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isIntegerHint: Boolean = false
) {
    fun formatInitial(v: Double): String = if (isIntegerHint && v == v.toLong().toDouble()) {
        v.toLong().toString()
    } else {
        v.toString()
    }

    var text by remember { mutableStateOf(formatInitial(value)) }

    LaunchedEffect(value) {
        val currentParsed = text.replace(',', '.').toDoubleOrNull()
        if (currentParsed == null || kotlin.math.abs(currentParsed - value) > 1e-9) {
            text = formatInitial(value)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val sanitized = raw.replace(',', '.')
            text = raw
            sanitized.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { parsed ->
                onValueCommitted(parsed)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}
