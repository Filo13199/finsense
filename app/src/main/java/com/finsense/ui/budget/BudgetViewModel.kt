package com.finsense.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finsense.data.entity.Budget
import com.finsense.data.entity.BudgetPeriod
import com.finsense.data.entity.Category
import com.finsense.data.model.BudgetWithSpent
import com.finsense.data.preferences.AppCurrency
import com.finsense.data.preferences.UserPreferences
import com.finsense.data.repository.BudgetRepository
import com.finsense.data.repository.CategoryRepository
import com.finsense.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetUiState(
    val budgets: List<BudgetWithSpent> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val currency: AppCurrency = AppCurrency.EGP
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        observeBudgets()
        observeTransactionChanges()
        observeCategories()
        userPreferences.currencyFlow
            .onEach { c ->
                _uiState.update { it.copy(currency = c) }
                refreshBudgets(c.name)
            }
            .launchIn(viewModelScope)
        userPreferences.monthStartDayFlow
            .onEach { refreshBudgets(userPreferences.currency.name) }
            .launchIn(viewModelScope)
    }

    private fun observeBudgets() {
        budgetRepository.getAll()
            .onEach { refreshBudgets(userPreferences.currency.name) }
            .launchIn(viewModelScope)
    }

    private fun observeTransactionChanges() {
        transactionRepository.getRecentWithCategory()
            .onEach { refreshBudgets(userPreferences.currency.name) }
            .launchIn(viewModelScope)
    }

    private suspend fun refreshBudgets(currency: String) {
        val withSpent = budgetRepository.getBudgetsWithSpent(currency, userPreferences.monthStartDay)
        _uiState.update { it.copy(budgets = withSpent, isLoading = false) }
    }

    private fun observeCategories() {
        categoryRepository.getAll()
            .onEach { cats -> _uiState.update { it.copy(categories = cats) } }
            .launchIn(viewModelScope)
    }

    fun addBudget(name: String, categoryId: Long?, amount: Double, period: BudgetPeriod) {
        viewModelScope.launch {
            budgetRepository.insert(
                Budget(name = name, categoryId = categoryId, amount = amount, period = period)
            )
        }
    }

    fun updateBudget(budget: Budget) {
        viewModelScope.launch { budgetRepository.update(budget) }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { budgetRepository.delete(budget) }
    }
}
