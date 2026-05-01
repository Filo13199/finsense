package com.finsense.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction
import androidx.room.Update
import com.finsense.data.entity.Transaction
import com.finsense.data.entity.TransactionWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @RoomTransaction
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllWithCategory(): Flow<List<TransactionWithCategory>>

    @RoomTransaction
    @Query("""
        SELECT * FROM transactions
        WHERE date >= :startDate AND date <= :endDate
        ORDER BY date DESC
    """)
    fun getByDateRangeWithCategory(startDate: Long, endDate: Long): Flow<List<TransactionWithCategory>>

    @RoomTransaction
    @Query("""
        SELECT * FROM transactions
        WHERE (vendor LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR normalized_vendor_name LIKE '%' || :query || '%')
        ORDER BY date DESC
    """)
    fun searchWithCategory(query: String): Flow<List<TransactionWithCategory>>

    @RoomTransaction
    @Query("SELECT * FROM transactions WHERE date >= :startDate ORDER BY date DESC LIMIT :limit")
    fun getRecentWithCategory(startDate: Long, limit: Int = 15): Flow<List<TransactionWithCategory>>

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM transactions
        WHERE categoryId = :categoryId AND type = 'DEBIT'
        AND date >= :startDate AND date <= :endDate
        AND currency = :currency
    """)
    suspend fun sumDebitByCategoryAndPeriod(
        categoryId: Long, startDate: Long, endDate: Long, currency: String
    ): Double

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM transactions
        WHERE type = 'DEBIT' AND date >= :startDate AND date <= :endDate
        AND currency = :currency
    """)
    suspend fun totalDebitForPeriod(startDate: Long, endDate: Long, currency: String): Double

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM transactions
        WHERE type = 'DEBIT' AND date >= :startDate AND date <= :endDate
        AND currency = :currency
        AND (categoryId IS NULL OR categoryId NOT IN (:excludedIds))
    """)
    suspend fun totalDebitForPeriodExcluding(
        startDate: Long, endDate: Long, currency: String, excludedIds: List<Long>
    ): Double

    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM transactions
        WHERE type = 'CREDIT' AND date >= :startDate AND date <= :endDate
        AND currency = :currency
    """)
    suspend fun totalCreditForPeriod(startDate: Long, endDate: Long, currency: String): Double

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsId = :smsId)")
    suspend fun existsBySmsId(smsId: String): Boolean

    @Query("""
        UPDATE transactions SET categoryId = :categoryId
        WHERE categoryId IS NULL
        AND (vendor = :vendor OR normalized_vendor_name = :normalizedName)
    """)
    suspend fun updateCategoryForVendorUncategorized(vendor: String, normalizedName: String, categoryId: Long)

    @Query("""
        UPDATE transactions SET normalized_vendor_name = :normalizedName
        WHERE lower(vendor) LIKE '%' || lower(:keyword) || '%'
          AND normalized_vendor_name IS NULL
    """)
    suspend fun applyNormalizedVendorForKeyword(keyword: String, normalizedName: String)

    @RoomTransaction
    @Query("""
        SELECT * FROM transactions
        WHERE type = 'DEBIT'
        AND date >= :startDate AND date <= :endDate
        AND currency = :currency
        ORDER BY amount DESC
    """)
    suspend fun getDebitTransactionsForPeriod(
        startDate: Long, endDate: Long, currency: String
    ): List<TransactionWithCategory>

    @RoomTransaction
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    fun getLatestWithCategory(limit: Int): Flow<List<TransactionWithCategory>>
}
