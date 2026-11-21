package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.repository.MarketAnalysisBundle
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.UserSettings
import com.example.ui.components.DecimalNumberField
import com.example.ui.components.WeltradeNoticeBanner
import com.example.ui.theme.LocalTradingColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    bundle: MarketAnalysisBundle?,
    onSelectSymbol: (String) -> Unit,
    onUpdateRisk: (Double, Double) -> Unit,
    onUpdateCosts: (Double, Double) -> Unit,
    onUpdateAutoRefresh: (Int) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val supportedPairs = listOf("EURUSD", "GBPUSD", "USDJPY", "XAUUSD")
    val refreshOptions = listOf(15 to "15s", 30 to "30s", 60 to "60s", 300 to "5 min")

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Paramètres & Sécurité",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Configuration de la paire, gestion du risque, coûts estimés et transparence des données.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Pair Selection (EURUSD default priority)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Marché analysé (EURUSD prioritaire)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        supportedPairs.forEach { pair ->
                            FilterChip(
                                selected = settings.selectedSymbol == pair,
                                onClick = { onSelectSymbol(pair) },
                                label = {
                                    Text(if (pair == "EURUSD") "EURUSD (Prioritaire)" else pair)
                                },
                                modifier = Modifier.testTag("settings_pair_$pair")
                            )
                        }
                    }
                }
            }
        }

        // Risk & Backtest Cost Defaults
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Gestion du risque & coûts par défaut",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DecimalNumberField(
                            value = settings.accountCapital,
                            onValueCommitted = { onUpdateRisk(it, settings.riskPercent) },
                            label = "Capital par défaut",
                            isIntegerHint = true,
                            modifier = Modifier.weight(1f)
                        )
                        DecimalNumberField(
                            value = settings.riskPercent,
                            onValueCommitted = { onUpdateRisk(settings.accountCapital, it) },
                            label = "Risque (%)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DecimalNumberField(
                            value = settings.spreadPips,
                            onValueCommitted = { onUpdateCosts(it, settings.commissionPips) },
                            label = "Spread estimé (pips)",
                            modifier = Modifier.weight(1f)
                        )
                        DecimalNumberField(
                            value = settings.commissionPips,
                            onValueCommitted = { onUpdateCosts(settings.spreadPips, it) },
                            label = "Commission (pips)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Refresh & Theme
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Actualisation & Affichage",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fréquence d'interrogation de l'API Biquote",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        refreshOptions.forEach { (sec, label) ->
                            FilterChip(
                                selected = settings.autoRefreshSeconds == sec,
                                onClick = { onUpdateAutoRefresh(sec) },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(color = tradingColors.border)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Thème sombre professionnel",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Contraste élevé pour l'analyse de graphiques",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.darkMode,
                            onCheckedChange = onToggleDarkMode,
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }
            }
        }

        // Diagnostic API Biquote & Guarantees
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Diagnostic API Biquote & Engagements",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Point d'accès : https://biquote.io/api/ (${bundle?.freshnessStatus?.labelFr ?: "En attente"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Bougies chargées : M15 = ${bundle?.m15ClosedCandles?.size ?: 0} clôturées, H1 = ${bundle?.h1ClosedCandles?.size ?: 0} clôturées",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bundle != null) {
                        Text(
                            text = "• Dernière mise à jour : ${TimeUtils.formatLocalDisplay(bundle.fetchedAtMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = tradingColors.border)
                    Text(
                        text = "• Aucun abonnement payant obligatoire.\n" +
                            "• Aucune connexion au compte bancaire ou MT5.\n" +
                            "• Aucun ordre automatique exécuté.\n" +
                            "• Aucune promesse de gain : aide technique uniquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            WeltradeNoticeBanner()
        }
    }
}
