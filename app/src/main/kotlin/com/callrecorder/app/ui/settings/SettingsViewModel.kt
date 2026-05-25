package com.callrecorder.app.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val autoRecord: Boolean       = true,
    val recordVoip: Boolean       = true,
    val quality: Int              = 1,
    val pinEnabled: Boolean       = false,
    val darkTheme: Boolean        = false,
    val autoDeleteDays: Int       = 0,
    val hiddenMode: Boolean       = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<androidx.datastore.preferences.core.Preferences>
) : ViewModel() {

    val state: StateFlow<SettingsState> = dataStore.data.map { prefs ->
        SettingsState(
            autoRecord    = prefs[booleanPreferencesKey(Constants.PREF_AUTO_RECORD)]    ?: true,
            recordVoip    = prefs[booleanPreferencesKey(Constants.PREF_RECORD_VOIP)]    ?: true,
            quality       = prefs[intPreferencesKey(Constants.PREF_RECORDING_QUALITY)]  ?: 1,
            pinEnabled    = prefs[booleanPreferencesKey(Constants.PREF_PIN_ENABLED)]    ?: false,
            darkTheme     = prefs[booleanPreferencesKey(Constants.PREF_DARK_THEME)]     ?: false,
            autoDeleteDays = prefs[intPreferencesKey(Constants.PREF_AUTO_DELETE_DAYS)]  ?: 0,
            hiddenMode    = prefs[booleanPreferencesKey(Constants.PREF_HIDDEN_MODE)]    ?: false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setAutoRecord(v: Boolean)       = save(booleanPreferencesKey(Constants.PREF_AUTO_RECORD), v)
    fun setRecordVoip(v: Boolean)       = save(booleanPreferencesKey(Constants.PREF_RECORD_VOIP), v)
    fun setQuality(v: Int)              = save(intPreferencesKey(Constants.PREF_RECORDING_QUALITY), v)
    fun setPinEnabled(v: Boolean)       = save(booleanPreferencesKey(Constants.PREF_PIN_ENABLED), v)
    fun setDarkTheme(v: Boolean)        = save(booleanPreferencesKey(Constants.PREF_DARK_THEME), v)
    fun setAutoDeleteDays(v: Int)       = save(intPreferencesKey(Constants.PREF_AUTO_DELETE_DAYS), v)
    fun setHiddenMode(v: Boolean)       = save(booleanPreferencesKey(Constants.PREF_HIDDEN_MODE), v)

    private fun <T> save(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }
}
