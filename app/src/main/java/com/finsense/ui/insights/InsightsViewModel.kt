package com.finsense.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finsense.data.dao.TransactionDao
import com.finsense.data.entity.Category
import com.finsense.data.entity.TransactionWithCategory
import com.finsense.data.preferences.AppCurrency
import com.finsense.data.preferences.UserPreferences
import com.finsense.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class InsightsPeriod(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    THREE_MONTHS("3 months"),
    SIX_MONTHS("6 months"),
    THIS_YEAR("This year")
}

enum class InsightsViewMode { CATEGORIES, VENDORS }

data class SpendingSlice(
    val label: String,
    val icon: String,
    val color: Long,
    val amount: Double,
    val percentage: Float,
    val categoryId: Long? = null,
    val vendorKey: String? = null
)

data class InsightsUiState(
    val period: InsightsPeriod = InsightsPeriod.THIS_MONTH,
    val viewMode: InsightsViewMode = InsightsViewMode.CATEGORIES,
    val excludedCategoryIds: Set<Long> = emptySet(),
    val categories: List<Category> = emptyList(),
    val slices: List<SpendingSlice> = emptyList(),
    val totalSpent: Double = 0.0,
    val currency: AppCurrency = AppCurrency.EGP,
    val isLoading: Boolean = true,
    val periodStartMs: Long = 0L,
    val periodEndMs: Long = 0L
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState(currency = userPreferences.currency))
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var cachedCategories: List<Category> = emptyList()

    init {
        combine(
            categoryRepository.getAll(),
            userPreferences.currencyFlow
        ) { cats, currency -> cats to currency }
            .onEach { (cats, currency) ->
                cachedCategories = cats
                _uiState.update { it.copy(categories = cats, currency = currency) }
                refresh()
            }
            .launchIn(viewModelScope)
    }

    fun selectPeriod(period: InsightsPeriod) {
        _uiState.update { it.copy(period = period) }
        refresh()
    }

    fun selectViewMode(mode: InsightsViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        refresh()
    }

    fun toggleExcludeCategory(categoryId: Long) {
        val current = _uiState.value.excludedCategoryIds
        _uiState.update {
            it.copy(
                excludedCategoryIds = if (categoryId in current) current - categoryId else current + categoryId
            )
        }
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val state = _uiState.value
            val (start, end) = periodRange(state.period, userPreferences.monthStartDay)
            val raw = transactionDao.getDebitTransactionsForPeriod(start, end, state.currency.name)

            val transactions = if (state.excludedCategoryIds.isEmpty()) raw
            else raw.filter { it.transaction.categoryId !in state.excludedCategoryIds }

            val total = transactions.sumOf { it.transaction.amount }
            val slices = when (state.viewMode) {
                InsightsViewMode.CATEGORIES -> buildCategorySlices(transactions, cachedCategories, total)
                InsightsViewMode.VENDORS -> buildVendorSlices(transactions, total)
            }
            _uiState.update { it.copy(slices = slices, totalSpent = total, isLoading = false, periodStartMs = start, periodEndMs = end) }
        }
    }

    private fun buildCategorySlices(
        transactions: List<TransactionWithCategory>,
        categories: List<Category>,
        total: Double
    ): List<SpendingSlice> {
        if (total == 0.0) return emptyList()
        return transactions
            .groupBy { it.transaction.categoryId }
            .map { (categoryId, txcs) ->
                val cat = categories.find { it.id == categoryId }
                val amount = txcs.sumOf { it.transaction.amount }
                SpendingSlice(
                    label = cat?.name ?: "Uncategorized",
                    icon = cat?.icon ?: "📦",
                    color = cat?.color ?: 0xFF90A4AEL,
                    amount = amount,
                    percentage = (amount / total).toFloat(),
                    categoryId = categoryId
                )
            }
            .sortedByDescending { it.amount }
    }

    private fun buildVendorSlices(
        transactions: List<TransactionWithCategory>,
        total: Double
    ): List<SpendingSlice> {
        if (total == 0.0) return emptyList()
        return transactions
            .groupBy { it.transaction.normalizedVendorName ?: it.transaction.vendor }
            .map { (vendor, txcs) ->
                SpendingSlice(
                    label = vendor,
                    icon = "",
                    color = 0L,
                    amount = txcs.sumOf { it.transaction.amount },
                    percentage = (txcs.sumOf { it.transaction.amount } / total).toFloat(),
                    vendorKey = vendor
                )
            }
            .sortedByDescending { it.amount }
            .mapIndexed { i, slice -> slice.copy(color = VENDOR_PALETTE[i % VENDOR_PALETTE.size]) }
    }

    private fun periodRange(period: InsightsPeriod, monthStartDay: Int): Pair<Long, Long> {
        val now = Calendar.getInstance()
        return when (period) {
            InsightsPeriod.THIS_MONTH -> customMonthRange(monthStartDay, 0)
            InsightsPeriod.LAST_MONTH -> customMonthRange(monthStartDay, -1)
            InsightsPeriod.THREE_MONTHS ->
                Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis to now.timeInMillis
            InsightsPeriod.SIX_MONTHS ->
                Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.timeInMillis to now.timeInMillis
            InsightsPeriod.THIS_YEAR ->
                Calendar.getInstance().apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis to now.timeInMillis
        }
    }

    private fun customMonthRange(monthStartDay: Int, monthOffset: Int): Pair<Long, Long> {
        val today = Calendar.getInstance()
        val start = Calendar.getInstance().also {
            if (today.get(Calendar.DAY_OF_MONTH) < monthStartDay) it.add(Calendar.MONTH, -1)
            it.add(Calendar.MONTH, monthOffset)
            it.set(Calendar.DAY_OF_MONTH, monthStartDay)
            it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0); it.set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).also {
            it.add(Calendar.MONTH, 1)
            it.add(Calendar.DAY_OF_MONTH, -1)
            it.set(Calendar.HOUR_OF_DAY, 23); it.set(Calendar.MINUTE, 59)
            it.set(Calendar.SECOND, 59); it.set(Calendar.MILLISECOND, 999)
        }
        return start.timeInMillis to end.timeInMillis
    }

    companion object {
        private val VENDOR_PALETTE = listOf(
            0xFF6200EEL, 0xFF018786L, 0xFFB00020L, 0xFF3700B3L,
            0xFF00BCD4L, 0xFF4CAF50L, 0xFFFF9800L, 0xFFE91E63L,
            0xFF795548L, 0xFF607D8BL, 0xFFCDDC39L, 0xFF9C27B0L
        )
    }
}
