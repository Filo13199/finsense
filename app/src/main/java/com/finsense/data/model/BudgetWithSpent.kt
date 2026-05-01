package com.finsense.data.model

import com.finsense.data.entity.Budget
import com.finsense.data.entity.Category

data class BudgetWithSpent(
    val budget: Budget,
    val category: Category?,
    val spent: Double
) {
    val percentage: Float get() = (spent / budget.amount).toFloat().coerceIn(0f, 1f)
    val remaining: Double get() = (budget.amount - spent).coerceAtLeast(0.0)
    val isOverBudget: Boolean get() = spent > budget.amount
}
