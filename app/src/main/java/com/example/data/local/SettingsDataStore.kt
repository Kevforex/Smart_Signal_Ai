package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "smart_signal_settings"
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val SELECTED_SYMBOL = stringPreferencesKey("selected_symbol")
        val ACCOUNT_CAPITAL = doublePreferencesKey("account_capital")
        val RISK_PERCENT = doublePreferencesKey("risk_percent")
        val SPREAD_PIPS = doublePreferencesKey("spread_pips")
        val COMMISSION_PIPS = doublePreferencesKey("commission_pips")
        val AUTO_REFRESH_SECONDS = intPreferencesKey("auto_refresh_seconds")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            selectedSymbol = prefs[Keys.SELECTED_SYMBOL] ?: "EURUSD",
            accountCapital = prefs[Keys.ACCOUNT_CAPITAL] ?: 1000.0,
            riskPercent = prefs[Keys.RISK_PERCENT] ?: 1.0,
            spreadPips = prefs[Keys.SPREAD_PIPS] ?: 1.2,
            commissionPips = prefs[Keys.COMMISSION_PIPS] ?: 0.4,
            autoRefreshSeconds = prefs[Keys.AUTO_REFRESH_SECONDS] ?: 30,
            darkMode = prefs[Keys.DARK_MODE] ?: true
        )
    }

    suspend fun updateSelectedSymbol(symbol: String) {
        context.settingsDataStore.edit { it[Keys.SELECTED_SYMBOL] = symbol }
    }

    suspend fun updateRiskParams(capital: Double, riskPercent: Double) {
        context.settingsDataStore.edit {
            it[Keys.ACCOUNT_CAPITAL] = capital.coerceAtLeast(1.0)
            it[Keys.RISK_PERCENT] = riskPercent.coerceIn(0.05, 10.0)
        }
    }

    suspend fun updateBacktestCosts(spreadPips: Double, commissionPips: Double) {
        context.settingsDataStore.edit {
            it[Keys.SPREAD_PIPS] = spreadPips.coerceIn(0.0, 20.0)
            it[Keys.COMMISSION_PIPS] = commissionPips.coerceIn(0.0, 20.0)
        }
    }

    suspend fun updateAutoRefresh(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.AUTO_REFRESH_SECONDS] = seconds }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }
}
