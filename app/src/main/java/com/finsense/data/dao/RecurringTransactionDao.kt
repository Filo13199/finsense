package com.finsense.data.dao

import androidx.room.*
import com.finsense.data.entity.RecurringTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringTransaction): Long

    @Update
    suspend fun update(rule: RecurringTransaction)

    @Delete
    suspend fun delete(rule: RecurringTransaction)

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY id ASC")
    fun getAllActive(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1")
    suspend fun getAllActiveOnce(): List<RecurringTransaction>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM transactions
            WHERE recurringId = :recurringId
            AND date >= :periodStart AND date <= :periodEnd
        )
    """)
    suspend fun hasTransactionInPeriod(
        recurringId: Long,
        periodStart: Long,
        periodEnd: Long
    ): Boolean
}
