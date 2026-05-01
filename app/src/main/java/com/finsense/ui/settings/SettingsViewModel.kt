package com.finsense.ui.settings

import androidx.lifecycle.ViewModel
import com.finsense.data.preferences.AppCurrency
import com.finsense.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val currency: StateFlow<AppCurrency> = userPreferences.currencyFlow
    val monthStartDay: StateFlow<Int> = userPreferences.monthStartDayFlow

    fun setCurrency(currency: AppCurrency) {
        userPreferences.setCurrency(currency)
    }

    fun setMonthStartDay(day: Int) {
        userPreferences.setMonthStartDay(day)
    }
}
