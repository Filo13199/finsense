package com.finsense.data.repository

import com.finsense.data.dao.BudgetDao
import com.finsense.data.dao.CategoryDao
import com.finsense.data.dao.TransactionDao
import com.finsense.data.entity.Budget
import com.finsense.data.entity.BudgetPeriod
import com.finsense.data.model.BudgetWithSpent
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {
    fun getAll(): Flow<List<Budget>> = budgetDao.getAll()

    suspend fun insert(budget: Budget): Long = budgetDao.insert(budget)
    suspend fun update(budget: Budget) = budgetDao.update(budget)
    suspend fun delete(budget: Budget) = budgetDao.delete(budget)

    suspend fun getBudgetsWithSpent(currency: String, monthStartDay: Int = 1): List<BudgetWithSpent> {
        val budgets = budgetDao.getAllOnce()
        return budgets.map { budget ->
            val (start, end) = periodRange(budget.period, monthStartDay)
            val spent = budget.categoryId?.let {
                transactionDao.sumDebitByCategoryAndPeriod(it, start, end, currency)
            } ?: 0.0
            val category = budget.categoryId?.let { categoryDao.getById(it) }
            BudgetWithSpent(budget = budget, category = category, spent = spent)
        }
    }

    private fun periodRange(period: BudgetPeriod, monthStartDay: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (period) {
            BudgetPeriod.DAILY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                start to cal.timeInMillis
            }
            BudgetPeriod.WEEKLY -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                start to cal.timeInMillis
            }
            BudgetPeriod.MONTHLY -> customMonthRange(monthStartDay)
        }
    }

    private fun customMonthRange(monthStartDay: Int): Pair<Long, Long> {
        val today = Calendar.getInstance()
        val start = Calendar.getInstance()
        if (today.get(Calendar.DAY_OF_MONTH) < monthStartDay) {
            start.add(Calendar.MONTH, -1)
        }
        start.set(Calendar.DAY_OF_MONTH, monthStartDay)
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0)
        val end = start.clone() as Calendar
        end.add(Calendar.MONTH, 1)
        end.add(Calendar.DAY_OF_MONTH, -1)
        end.set(Calendar.HOUR_OF_DAY, 23); end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59); end.set(Calendar.MILLISECOND, 999)
        return start.timeInMillis to end.timeInMillis
    }
}
