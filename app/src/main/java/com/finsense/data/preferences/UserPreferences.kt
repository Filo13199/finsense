package com.finsense.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("finsense_prefs", Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(
        AppCurrency.entries.firstOrNull { it.name == prefs.getString(KEY_CURRENCY, null) }
            ?: AppCurrency.EGP
    )
    val currencyFlow: StateFlow<AppCurrency> = _currency.asStateFlow()

    val currency: AppCurrency get() = _currency.value

    fun setCurrency(currency: AppCurrency) {
        prefs.edit().putString(KEY_CURRENCY, currency.name).apply()
        _currency.value = currency
    }

    private val _monthStartDay = MutableStateFlow(
        prefs.getInt(KEY_MONTH_START_DAY, 1).coerceIn(1, 28)
    )
    val monthStartDayFlow: StateFlow<Int> = _monthStartDay.asStateFlow()
    val monthStartDay: Int get() = _monthStartDay.value

    fun setMonthStartDay(day: Int) {
        val clamped = day.coerceIn(1, 28)
        prefs.edit().putInt(KEY_MONTH_START_DAY, clamped).apply()
        _monthStartDay.value = clamped
    }

    companion object {
        private const val KEY_CURRENCY = "currency"
        private const val KEY_MONTH_START_DAY = "month_start_day"
    }
}
