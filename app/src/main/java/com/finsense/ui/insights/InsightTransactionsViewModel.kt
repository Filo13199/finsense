package com.finsense.ui.insights

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finsense.data.dao.TransactionDao
import com.finsense.data.entity.TransactionWithCategory
import com.finsense.data.preferences.AppCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightTransactionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val startMs: Long = checkNotNull(savedStateHandle["startMs"])
    private val endMs: Long = checkNotNull(savedStateHandle["endMs"])
    private val filterType: String = checkNotNull(savedStateHandle["filterType"])
    private val filterValue: String = Uri.decode(checkNotNull(savedStateHandle["filterValue"]))
    private val currencyName: String = checkNotNull(savedStateHandle["currency"])

    val currency: AppCurrency = AppCurrency.valueOf(currencyName)

    private val _transactions = MutableStateFlow<List<TransactionWithCategory>>(emptyList())
    val transactions: StateFlow<List<TransactionWithCategory>> = _transactions.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = transactionDao.getDebitTransactionsForPeriod(startMs, endMs, currencyName)
            _transactions.value = filter(raw).sortedByDescending { it.transaction.date }
        }
    }

    private fun filter(raw: List<TransactionWithCategory>): List<TransactionWithCategory> {
        return when (filterType) {
            "category" -> when (filterValue) {
                "__uncategorized__" -> raw.filter { it.transaction.categoryId == null }
                else -> raw.filter { it.transaction.categoryId == filterValue.toLongOrNull() }
            }
            "vendor" -> raw.filter {
                (it.transaction.normalizedVendorName ?: it.transaction.vendor) == filterValue
            }
            else -> raw
        }
    }
}
