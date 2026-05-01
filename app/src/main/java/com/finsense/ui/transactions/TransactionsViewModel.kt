package com.finsense.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finsense.data.entity.Category
import com.finsense.data.entity.RecurringFrequency
import com.finsense.data.entity.RecurringTransaction
import com.finsense.data.entity.Transaction
import com.finsense.data.entity.TransactionType
import com.finsense.data.entity.TransactionWithCategory
import com.finsense.data.preferences.AppCurrency
import com.finsense.data.preferences.UserPreferences
import com.finsense.data.repository.CategoryRepository
import com.finsense.data.repository.RecurringTransactionRepository
import com.finsense.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<TransactionWithCategory> = emptyList(),
    val recurringTransactions: List<RecurringTransaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val currency: AppCurrency = AppCurrency.EGP
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences,
    private val recurringRepository: RecurringTransactionRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        observeTransactions()
        observeCategories()
        observeRecurring()
        userPreferences.currencyFlow
            .onEach { c -> _uiState.update { it.copy(currency = c) } }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeTransactions() {
        _query
            .debounce(300)
            .flatMapLatest { q ->
                if (q.isBlank()) transactionRepository.getAllWithCategory()
                else transactionRepository.searchWithCategory(q)
            }
            .onEach { list ->
                _uiState.update { it.copy(transactions = list, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCategories() {
        categoryRepository.getAll()
            .onEach { cats -> _uiState.update { it.copy(categories = cats) } }
            .launchIn(viewModelScope)
    }

    private fun observeRecurring() {
        recurringRepository.getAllActive()
            .onEach { rules -> _uiState.update { it.copy(recurringTransactions = rules) } }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _query.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { transactionRepository.deleteTransaction(transaction) }
    }

    fun categorizeTransaction(transaction: Transaction, categoryId: Long, cascade: Boolean) {
        viewModelScope.launch { transactionRepository.categorizeVendor(transaction, categoryId, cascade) }
    }

    fun deleteRecurring(rule: RecurringTransaction) {
        viewModelScope.launch { recurringRepository.delete(rule) }
    }

    fun updateCategory(transaction: Transaction, categoryId: Long?) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction.copy(categoryId = categoryId))
        }
    }

    fun updateDate(transaction: Transaction, newDateMs: Long) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction.copy(date = newDateMs))
        }
    }

    fun addManualTransaction(
        amount: Double,
        type: TransactionType,
        vendor: String,
        description: String,
        categoryId: Long?
    ) {
        viewModelScope.launch {
            val tx = Transaction(
                amount = amount,
                type = type,
                vendor = vendor.ifBlank { "Manual" },
                description = description,
                categoryId = categoryId,
                date = System.currentTimeMillis(),
                currency = userPreferences.currency.name,
                isManual = true
            )
            transactionRepository.addTransaction(tx)
        }
    }

    fun addRecurringTransaction(
        amount: Double,
        type: TransactionType,
        vendor: String,
        description: String,
        categoryId: Long?,
        frequency: RecurringFrequency,
        dayOfPeriod: Int
    ) {
        viewModelScope.launch {
            val rule = RecurringTransaction(
                amount = amount,
                type = type,
                vendor = vendor.ifBlank { "Recurring" },
                description = description,
                categoryId = categoryId,
                currency = userPreferences.currency.name,
                frequency = frequency,
                dayOfPeriod = dayOfPeriod
            )
            val ruleId = recurringRepository.insert(rule)
            // Generate first instance immediately so it appears in the current period
            recurringRepository.generateIfNeeded(rule.copy(id = ruleId))
        }
    }
}
