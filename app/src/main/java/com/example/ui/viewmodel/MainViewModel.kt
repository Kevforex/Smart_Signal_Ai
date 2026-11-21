package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.SignalHistoryEntity
import com.example.data.remote.NetworkModule
import com.example.data.repository.MarketAnalysisBundle
import com.example.data.repository.MarketRepository
import com.example.domain.analysis.BacktestEngine
import com.example.domain.analysis.RiskCalculator
import com.example.domain.analysis.SignalEngine
import com.example.domain.model.BacktestConfig
import com.example.domain.model.BacktestResult
import com.example.domain.model.FreshnessStatus
import com.example.domain.model.RiskCalculation
import com.example.domain.model.Timeframe
import com.example.domain.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Ready(val bundle: MarketAnalysisBundle, val isRefreshing: Boolean = false) : MarketUiState
    data class Error(val message: String, val lastBundle: MarketAnalysisBundle? = null) : MarketUiState
}

data class ChartUiState(
    val selectedTimeframe: Timeframe = Timeframe.M15,
    val showEma50: Boolean = true,
    val showEma200: Boolean = true,
    val showSupportResistance: Boolean = true,
    val showSignalLevels: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val settingsStore = SettingsDataStore(application)
    private val repository = MarketRepository(
        apiService = NetworkModule.biquoteApiService,
        signalHistoryDao = database.signalHistoryDao()
    )

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings()
    )

    val signalHistory: StateFlow<List<SignalHistoryEntity>> = repository.signalHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _marketState = MutableStateFlow<MarketUiState>(MarketUiState.Loading)
    val marketState: StateFlow<MarketUiState> = _marketState.asStateFlow()

    private val _chartState = MutableStateFlow(ChartUiState())
    val chartState: StateFlow<ChartUiState> = _chartState.asStateFlow()

    private val _backtestConfig = MutableStateFlow(BacktestConfig())
    val backtestConfig: StateFlow<BacktestConfig> = _backtestConfig.asStateFlow()

    private val _backtestResult = MutableStateFlow<BacktestResult?>(null)
    val backtestResult: StateFlow<BacktestResult?> = _backtestResult.asStateFlow()

    private val _isBacktesting = MutableStateFlow(false)
    val isBacktesting: StateFlow<Boolean> = _isBacktesting.asStateFlow()

    private val _customCalcEntry = MutableStateFlow<String>("")
    val customCalcEntry: StateFlow<String> = _customCalcEntry.asStateFlow()

    private val _customCalcSl = MutableStateFlow<String>("")
    val customCalcSl: StateFlow<String> = _customCalcSl.asStateFlow()

    private var pollingJob: Job? = null
    private var lastLoadedBundle: MarketAnalysisBundle? = null

    init {
        viewModelScope.launch {
            val initial = settingsStore.settingsFlow.first()
            _backtestConfig.update {
                it.copy(
                    symbol = initial.selectedSymbol,
                    initialCapital = initial.accountCapital,
                    riskPercent = initial.riskPercent,
                    spreadPips = initial.spreadPips,
                    commissionPips = initial.commissionPips
                )
            }
        }

        viewModelScope.launch {
            settings
                .map {
                    listOf(
                        it.selectedSymbol,
                        it.accountCapital,
                        it.riskPercent,
                        it.spreadPips,
                        it.commissionPips
                    )
                }
                .distinctUntilChanged()
                .collectLatest { _ ->
                    val s = settings.value
                    _backtestConfig.update {
                        it.copy(
                            symbol = s.selectedSymbol,
                            initialCapital = s.accountCapital,
                            riskPercent = s.riskPercent,
                            spreadPips = s.spreadPips,
                            commissionPips = s.commissionPips
                        )
                    }
                }
        }

        viewModelScope.launch {
            settings
                .map { it.selectedSymbol to it.autoRefreshSeconds }
                .distinctUntilChanged()
                .collectLatest { (symbol, refreshSeconds) ->
                    startPolling(symbol, refreshSeconds)
                }
        }
    }

    private fun startPolling(symbol: String, refreshSeconds: Int) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshNowInternal(symbol)
                val intervalMs = (refreshSeconds.coerceAtLeast(15)) * 1000L
                delay(intervalMs)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshNowInternal(settings.value.selectedSymbol)
        }
    }

    private suspend fun refreshNowInternal(symbol: String) {
        val current = lastLoadedBundle
        if (current != null && current.symbol == symbol) {
            _marketState.value = MarketUiState.Ready(current, isRefreshing = true)
        } else {
            _marketState.value = MarketUiState.Loading
        }

        val bundle = repository.fetchAndAnalyze(symbol)
        if (bundle.errorMessage != null) {
            val cachedSameSymbol = if (current?.symbol == symbol) current else null
            val safeCached = cachedSameSymbol?.let { prev ->
                val ageMs = System.currentTimeMillis() - prev.fetchedAtMs
                if (ageMs > 180_000L) {
                    val staleSignal = SignalEngine.evaluate(
                        symbol = prev.symbol,
                        closedM15 = prev.m15ClosedCandles,
                        closedH1 = prev.h1ClosedCandles,
                        freshnessStatus = FreshnessStatus.STALE,
                        m15Snapshot = prev.m15Snapshot,
                        h1Snapshot = prev.h1Snapshot
                    )
                    prev.copy(
                        freshnessStatus = FreshnessStatus.STALE,
                        activeSignal = staleSignal
                    )
                } else {
                    prev
                }
            }
            _marketState.value = MarketUiState.Error(
                message = bundle.errorMessage,
                lastBundle = safeCached
            )
        } else {
            lastLoadedBundle = bundle
            _marketState.value = MarketUiState.Ready(bundle, isRefreshing = false)
            // Seed calculator inputs if empty
            val sig = bundle.activeSignal
            if (_customCalcEntry.value.isBlank() && sig.entryPrice != null && sig.stopLoss != null) {
                _customCalcEntry.value = sig.entryPrice.toString()
                _customCalcSl.value = sig.stopLoss.toString()
            }
        }
    }

    fun setChartTimeframe(timeframe: Timeframe) {
        _chartState.update { it.copy(selectedTimeframe = timeframe) }
    }

    fun toggleEma50() {
        _chartState.update { it.copy(showEma50 = !it.showEma50) }
    }

    fun toggleEma200() {
        _chartState.update { it.copy(showEma200 = !it.showEma200) }
    }

    fun toggleSupportResistance() {
        _chartState.update { it.copy(showSupportResistance = !it.showSupportResistance) }
    }

    fun toggleSignalLevels() {
        _chartState.update { it.copy(showSignalLevels = !it.showSignalLevels) }
    }

    fun updateRiskSettings(capital: Double, riskPercent: Double) {
        viewModelScope.launch {
            settingsStore.updateRiskParams(capital, riskPercent)
        }
    }

    fun updateBacktestCosts(spreadPips: Double, commissionPips: Double) {
        viewModelScope.launch {
            settingsStore.updateBacktestCosts(spreadPips, commissionPips)
        }
    }

    fun updateSelectedSymbol(symbol: String) {
        viewModelScope.launch {
            _customCalcEntry.value = ""
            _customCalcSl.value = ""
            _backtestResult.value = null
            settingsStore.updateSelectedSymbol(symbol)
        }
    }

    fun updateAutoRefresh(seconds: Int) {
        viewModelScope.launch {
            settingsStore.updateAutoRefresh(seconds)
        }
    }

    fun updateDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateDarkMode(enabled)
        }
    }

    fun updateCustomCalcInputs(entry: String, sl: String) {
        _customCalcEntry.value = entry
        _customCalcSl.value = sl
    }

    fun calculateRisk(
        symbol: String,
        capital: Double,
        riskPct: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        tp1Price: Double?,
        tp2Price: Double?,
        spreadPips: Double
    ): RiskCalculation {
        return RiskCalculator.calculate(
            symbol = symbol,
            accountCapital = capital,
            riskPercent = riskPct,
            entryPrice = entryPrice,
            stopLossPrice = stopLossPrice,
            takeProfit1Price = tp1Price,
            takeProfit2Price = tp2Price,
            spreadPips = spreadPips
        )
    }

    fun updateBacktestConfig(
        minScore: Int = _backtestConfig.value.minScore,
        spreadPips: Double = _backtestConfig.value.spreadPips,
        commissionPips: Double = _backtestConfig.value.commissionPips,
        initialCapital: Double = _backtestConfig.value.initialCapital,
        riskPercent: Double = _backtestConfig.value.riskPercent,
        targetTp: BacktestConfig.BacktestTarget = _backtestConfig.value.targetTp
    ) {
        _backtestConfig.update {
            it.copy(
                minScore = minScore,
                spreadPips = spreadPips,
                commissionPips = commissionPips,
                initialCapital = initialCapital,
                riskPercent = riskPercent,
                targetTp = targetTp
            )
        }
    }

    fun runBacktest() {
        val bundle = lastLoadedBundle ?: return
        val cfg = _backtestConfig.value.copy(symbol = bundle.symbol)
        viewModelScope.launch {
            _isBacktesting.value = true
            val result = withContext(Dispatchers.Default) {
                BacktestEngine.run(
                    config = cfg,
                    closedM15 = bundle.m15ClosedCandles,
                    closedH1 = bundle.h1ClosedCandles
                )
            }
            _backtestResult.value = result
            _isBacktesting.value = false
        }
    }
}
