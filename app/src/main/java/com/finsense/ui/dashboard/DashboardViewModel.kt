package com.finsense.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finsense.data.entity.TransactionWithCategory
import com.finsense.data.model.BudgetWithSpent
import com.finsense.data.preferences.AppCurrency
import com.finsense.data.preferences.UserPreferences
import com.finsense.data.repository.BudgetRepository
import com.finsense.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val monthlyExpense: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val recentTransactions: List<TransactionWithCategory> = emptyList(),
    val budgets: List<BudgetWithSpent> = emptyList(),
    val isLoading: Boolean = true,
    val hasMoreTransactions: Boolean = false,
    val currency: AppCurrency = AppCurrency.EGP
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _limit = MutableStateFlow(PAGE_SIZE)

    init {
        observeRecentTransactions()
        observeBudgetChanges()
        userPreferences.currencyFlow
            .onEach { c ->
                _uiState.update { it.copy(currency = c) }
                refreshTotalsAndBudgets(c.name)
            }
            .launchIn(viewModelScope)
        userPreferences.monthStartDayFlow
            .onEach { refreshTotalsAndBudgets(userPreferences.currency.name) }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRecentTransactions() {
        _limit
            .flatMapLatest { limit ->
                transactionRepository.getLatestWithCategory(limit)
                    .map { list -> list to limit }
            }
            .onEach { (list, limit) ->
                _uiState.update {
                    it.copy(
                        recentTransactions = list,
                        isLoading = false,
                        hasMoreTransactions = list.size >= limit
                    )
                }
                refreshTotalsAndBudgets(userPreferences.currency.name)
            }
            .launchIn(viewModelScope)
    }

    private fun observeBudgetChanges() {
        budgetRepository.getAll()
            .onEach { refreshTotalsAndBudgets(userPreferences.currency.name) }
            .launchIn(viewModelScope)
    }

    private suspend fun refreshTotalsAndBudgets(currency: String) {
        val monthStartDay = userPreferences.monthStartDay
        val (expense, income) = transactionRepository.monthlyTotals(currency, monthStartDay)
        val withSpent = budgetRepository.getBudgetsWithSpent(currency, monthStartDay)
        _uiState.update { it.copy(monthlyExpense = expense, monthlyIncome = income, budgets = withSpent) }
    }

    fun loadMoreTransactions() {
        _limit.value += PAGE_SIZE
    }

    fun refresh() {
        viewModelScope.launch { refreshTotalsAndBudgets(userPreferences.currency.name) }
    }

    companion object {
        private const val PAGE_SIZE = 15
    }
}
