package com.finsense.data.model

import com.finsense.data.entity.Budget
import com.finsense.data.entity.Category
import java.util.Calendar

data class BudgetWithSpent(
    val budget: Budget,
    val category: Category?,
    val excludedCategories: List<Category> = emptyList(),
    val spent: Double,
    val periodEndMs: Long
) {
    val percentage: Float get() = (spent / budget.amount).toFloat().coerceIn(0f, 1f)
    val remaining: Double get() = (budget.amount - spent).coerceAtLeast(0.0)
    val isOverBudget: Boolean get() = spent > budget.amount

    val daysRemainingInPeriod: Int get() {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diff = periodEndMs - todayStart
        return if (diff <= 0) 1 else (diff / 86_400_000L).toInt() + 1
    }

    val dailyAllowance: Double get() =
        if (!isOverBudget) remaining / daysRemainingInPeriod else 0.0

    val dailyOverspend: Double get() =
        if (isOverBudget) (spent - budget.amount) / daysRemainingInPeriod else 0.0
}
