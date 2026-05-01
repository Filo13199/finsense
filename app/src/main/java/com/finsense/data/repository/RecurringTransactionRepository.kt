package com.finsense.data.repository

import com.finsense.data.dao.RecurringTransactionDao
import com.finsense.data.entity.RecurringFrequency
import com.finsense.data.entity.RecurringTransaction
import com.finsense.data.entity.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringTransactionRepository @Inject constructor(
    private val recurringDao: RecurringTransactionDao,
    private val transactionRepository: TransactionRepository
) {

    fun getAllActive(): Flow<List<RecurringTransaction>> = recurringDao.getAllActive()

    suspend fun getAllActiveOnce(): List<RecurringTransaction> = recurringDao.getAllActiveOnce()

    suspend fun insert(rule: RecurringTransaction): Long = recurringDao.insert(rule)

    suspend fun update(rule: RecurringTransaction) = recurringDao.update(rule)

    suspend fun delete(rule: RecurringTransaction) = recurringDao.delete(rule)

    /**
     * Creates a Transaction for [rule] in the period containing [forDate] if one does
     * not already exist. Idempotent — safe to call multiple times for the same period.
     * Returns the inserted transaction id, or null if generation was skipped.
     */
    suspend fun generateIfNeeded(
        rule: RecurringTransaction,
        forDate: Long = System.currentTimeMillis()
    ): Long? {
        val targetDate = resolveTargetDate(rule, forDate) ?: return null
        val (periodStart, periodEnd) = periodBounds(rule.frequency, targetDate)

        if (recurringDao.hasTransactionInPeriod(rule.id, periodStart, periodEnd)) return null

        val tx = Transaction(
            amount = rule.amount,
            type = rule.type,
            vendor = rule.vendor,
            description = rule.description,
            categoryId = rule.categoryId,
            date = targetDate,
            currency = rule.currency,
            isManual = false,
            recurringId = rule.id
        )
        val txId = transactionRepository.addTransaction(tx)
        recurringDao.update(rule.copy(lastGeneratedAt = targetDate))
        return txId
    }

    private fun resolveTargetDate(rule: RecurringTransaction, referenceTime: Long): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTime }
        return when (rule.frequency) {
            RecurringFrequency.MONTHLY -> {
                val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, rule.dayOfPeriod.coerceAtMost(lastDay))
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            RecurringFrequency.WEEKLY -> {
                val calDow = when (rule.dayOfPeriod) {
                    1 -> Calendar.MONDAY; 2 -> Calendar.TUESDAY; 3 -> Calendar.WEDNESDAY
                    4 -> Calendar.THURSDAY; 5 -> Calendar.FRIDAY; 6 -> Calendar.SATURDAY
                    else -> Calendar.SUNDAY
                }
                cal.set(Calendar.DAY_OF_WEEK, calDow)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
    }

    private fun periodBounds(frequency: RecurringFrequency, epochInPeriod: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = epochInPeriod }
        return when (frequency) {
            RecurringFrequency.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                start to cal.timeInMillis
            }
            RecurringFrequency.WEEKLY -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                start to cal.timeInMillis
            }
        }
    }
}
