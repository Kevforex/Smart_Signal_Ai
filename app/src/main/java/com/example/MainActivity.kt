package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.MarketAnalysisBundle
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
import com.example.ui.screens.BacktestScreen
import com.example.ui.screens.ChartScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignalsScreen
import com.example.ui.screens.TechnicalAnalysisScreen
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.SmartSignalTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MarketUiState

object Routes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_CHART = "chart"
    const val ROUTE_ANALYSIS = "analysis"
    const val ROUTE_SIGNALS = "signals"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_BACKTEST = "backtest"
    const val ROUTE_SETTINGS = "settings"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            SmartSignalTheme(darkTheme = settings.darkMode) {
                SmartSignalApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSignalApp(viewModel: MainViewModel) {
    val marketState by viewModel.marketState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val chartState by viewModel.chartState.collectAsStateWithLifecycle()
    val history by viewModel.signalHistory.collectAsStateWithLifecycle()
    val backtestConfig by viewModel.backtestConfig.collectAsStateWithLifecycle()
    val backtestResult by viewModel.backtestResult.collectAsStateWithLifecycle()
    val isBacktesting by viewModel.isBacktesting.collectAsStateWithLifecycle()
    val calcEntry by viewModel.customCalcEntry.collectAsStateWithLifecycle()
    val calcSl by viewModel.customCalcSl.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.ROUTE_DASHBOARD

    val bottomDestinations = remember {
        listOf(
            NavDestination(Routes.ROUTE_DASHBOARD, "Tableau", Icons.Default.Dashboard),
            NavDestination(Routes.ROUTE_CHART, "Graphique", Icons.Default.CandlestickChart),
            NavDestination(Routes.ROUTE_SIGNALS, "Signaux", Icons.AutoMirrored.Filled.TrendingUp),
            NavDestination(Routes.ROUTE_HISTORY, "Historique", Icons.Default.History),
            NavDestination(Routes.ROUTE_BACKTEST, "Backtest", Icons.Default.Assessment)
        )
    }

    val railDestinations = remember {
        listOf(
            NavDestination(Routes.ROUTE_DASHBOARD, "Tableau", Icons.Default.Dashboard),
            NavDestination(Routes.ROUTE_CHART, "Graphique", Icons.Default.CandlestickChart),
            NavDestination(Routes.ROUTE_ANALYSIS, "Analyse", Icons.Default.Analytics),
            NavDestination(Routes.ROUTE_SIGNALS, "Signaux", Icons.AutoMirrored.Filled.TrendingUp),
            NavDestination(Routes.ROUTE_HISTORY, "Historique", Icons.Default.History),
            NavDestination(Routes.ROUTE_BACKTEST, "Backtest", Icons.Default.Assessment),
            NavDestination(Routes.ROUTE_SETTINGS, "Paramètres", Icons.Default.Settings)
        )
    }

    fun navigateSingleTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val activeBundle: MarketAnalysisBundle? = when (val s = marketState) {
        is MarketUiState.Ready -> s.bundle
        is MarketUiState.Error -> s.lastBundle
        MarketUiState.Loading -> null
    }
    val isRefreshing = (marketState as? MarketUiState.Ready)?.isRefreshing == true
    val activeSignal = activeBundle?.activeSignal
    val hasActionableSignal = activeSignal != null && activeSignal.direction != SignalDirection.NO_TRADE

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "SMART SIGNAL AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${settings.selectedSymbol} • M15 / H1 • Biquote",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { navigateSingleTop(Routes.ROUTE_ANALYSIS) },
                            modifier = Modifier.testTag("topbar_analysis_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Analyse technique",
                                tint = if (currentRoute == Routes.ROUTE_ANALYSIS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refreshNow() },
                            enabled = !isRefreshing,
                            modifier = Modifier.testTag("topbar_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser les cotations"
                            )
                        }
                        IconButton(
                            onClick = { navigateSingleTop(Routes.ROUTE_SETTINGS) },
                            modifier = Modifier.testTag("topbar_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Paramètres",
                                tint = if (currentRoute == Routes.ROUTE_SETTINGS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        bottomDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateSingleTop(dest.route) },
                                icon = {
                                    if (dest.route == Routes.ROUTE_SIGNALS && hasActionableSignal) {
                                        val tradingColors = LocalTradingColors.current
                                        val badgeColor = if (activeSignal.status == SignalStatus.CONFIRMED) {
                                            if (activeSignal.direction == SignalDirection.BUY) tradingColors.bull else tradingColors.bear
                                        } else {
                                            tradingColors.pending
                                        }
                                        BadgedBox(
                                            badge = { Badge(containerColor = badgeColor) }
                                        ) {
                                            Icon(dest.icon, contentDescription = dest.label)
                                        }
                                    } else {
                                        Icon(dest.icon, contentDescription = dest.label)
                                    }
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        railDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateSingleTop(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        val errorMsg = (marketState as? MarketUiState.Error)?.message
                        if (errorMsg != null && activeBundle != null) {
                            ConnectionErrorBanner(
                                message = errorMsg,
                                onRetry = { viewModel.refreshNow() }
                            )
                        }

                        NavHost(
                            navController = navController,
                            startDestination = Routes.ROUTE_DASHBOARD,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(Routes.ROUTE_DASHBOARD) {
                                MarketDataGate(
                                    marketState = marketState,
                                    onRetry = { viewModel.refreshNow() }
                                ) { bundle ->
                                    DashboardScreen(
                                        bundle = bundle,
                                        isRefreshing = isRefreshing,
                                        recordedSignalsCount = history.size,
                                        onRefresh = { viewModel.refreshNow() },
                                        onNavigateToChart = { navigateSingleTop(Routes.ROUTE_CHART) },
                                        onNavigateToAnalysis = { navigateSingleTop(Routes.ROUTE_ANALYSIS) },
                                        onNavigateToSignals = { navigateSingleTop(Routes.ROUTE_SIGNALS) },
                                        onNavigateToHistory = { navigateSingleTop(Routes.ROUTE_HISTORY) },
                                        onNavigateToBacktest = { navigateSingleTop(Routes.ROUTE_BACKTEST) },
                                        onNavigateToSettings = { navigateSingleTop(Routes.ROUTE_SETTINGS) }
                                    )
                                }
                            }

                            composable(Routes.ROUTE_CHART) {
                                MarketDataGate(
                                    marketState = marketState,
                                    onRetry = { viewModel.refreshNow() }
                                ) { bundle ->
                                    ChartScreen(
                                        bundle = bundle,
                                        chartState = chartState,
                                        onSelectTimeframe = { viewModel.setChartTimeframe(it) },
                                        onToggleEma50 = { viewModel.toggleEma50() },
                                        onToggleEma200 = { viewModel.toggleEma200() },
                                        onToggleSupportResistance = { viewModel.toggleSupportResistance() },
                                        onToggleSignalLevels = { viewModel.toggleSignalLevels() },
                                        onNavigateToAnalysis = { navigateSingleTop(Routes.ROUTE_ANALYSIS) }
                                    )
                                }
                            }

                            composable(Routes.ROUTE_ANALYSIS) {
                                MarketDataGate(
                                    marketState = marketState,
                                    onRetry = { viewModel.refreshNow() }
                                ) { bundle ->
                                    TechnicalAnalysisScreen(bundle = bundle)
                                }
                            }

                            composable(Routes.ROUTE_SIGNALS) {
                                MarketDataGate(
                                    marketState = marketState,
                                    onRetry = { viewModel.refreshNow() }
                                ) { bundle ->
                                    val sig = bundle.activeSignal
                                    val entryD = calcEntry.toDoubleOrNull() ?: sig.entryPrice
                                    val slD = calcSl.toDoubleOrNull() ?: sig.stopLoss
                                    val calc = if (entryD != null && slD != null && entryD > 0.0 && slD > 0.0) {
                                        viewModel.calculateRisk(
                                            symbol = bundle.symbol,
                                            capital = settings.accountCapital,
                                            riskPct = settings.riskPercent,
                                            entryPrice = entryD,
                                            stopLossPrice = slD,
                                            tp1Price = sig.takeProfit1,
                                            tp2Price = sig.takeProfit2,
                                            spreadPips = settings.spreadPips
                                        )
                                    } else {
                                        null
                                    }
                                    SignalsScreen(
                                        bundle = bundle,
                                        isRefreshing = isRefreshing,
                                        settings = settings,
                                        entryInput = calcEntry,
                                        slInput = calcSl,
                                        riskCalculation = calc,
                                        onRefresh = { viewModel.refreshNow() },
                                        onUpdateRisk = { cap, pct -> viewModel.updateRiskSettings(cap, pct) },
                                        onUpdateEntrySl = { e, sl -> viewModel.updateCustomCalcInputs(e, sl) },
                                        onSyncSignalLevels = {
                                            if (sig.entryPrice != null && sig.stopLoss != null) {
                                                viewModel.updateCustomCalcInputs(
                                                    TimeUtils.formatPrice(bundle.symbol, sig.entryPrice),
                                                    TimeUtils.formatPrice(bundle.symbol, sig.stopLoss)
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            composable(Routes.ROUTE_HISTORY) {
                                HistoryScreen(signals = history)
                            }

                            composable(Routes.ROUTE_BACKTEST) {
                                MarketDataGate(
                                    marketState = marketState,
                                    onRetry = { viewModel.refreshNow() }
                                ) { bundle ->
                                    BacktestScreen(
                                        bundle = bundle,
                                        config = backtestConfig,
                                        result = backtestResult,
                                        isRunning = isBacktesting,
                                        onUpdateConfig = { minScore, spread, comm, cap, risk, tgt ->
                                            viewModel.updateBacktestConfig(
                                                minScore = minScore,
                                                spreadPips = spread,
                                                commissionPips = comm,
                                                initialCapital = cap,
                                                riskPercent = risk,
                                                targetTp = tgt
                                            )
                                        },
                                        onRunBacktest = { viewModel.runBacktest() }
                                    )
                                }
                            }

                            composable(Routes.ROUTE_SETTINGS) {
                                SettingsScreen(
                                    settings = settings,
                                    bundle = activeBundle,
                                    onSelectSymbol = { viewModel.updateSelectedSymbol(it) },
                                    onUpdateRisk = { cap, pct -> viewModel.updateRiskSettings(cap, pct) },
                                    onUpdateCosts = { sp, com -> viewModel.updateBacktestCosts(sp, com) },
                                    onUpdateAutoRefresh = { viewModel.updateAutoRefresh(it) },
                                    onToggleDarkMode = { viewModel.updateDarkMode(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketDataGate(
    marketState: MarketUiState,
    onRetry: () -> Unit,
    content: @Composable (MarketAnalysisBundle) -> Unit
) {
    when (marketState) {
        MarketUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Connexion à l'API Biquote et analyse M15 / H1...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is MarketUiState.Error -> {
            if (marketState.lastBundle != null) {
                content(marketState.lastBundle)
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Erreur de connexion",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Erreur de connexion Biquote",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = marketState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag("retry_connection_button")
                        ) {
                            Text("Réessayer la connexion")
                        }
                    }
                }
            }
        }

        is MarketUiState.Ready -> {
            content(marketState.bundle)
        }
    }
}

@Composable
private fun ConnectionErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    val tradingColors = LocalTradingColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = tradingColors.bearContainer,
        border = BorderStroke(1.dp, tradingColors.bear)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Réessayer",
                    tint = tradingColors.bear
                )
            }
        }
    }
}
