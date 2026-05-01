package com.finsense.data.repository

import com.finsense.data.dao.CategoryDao
import com.finsense.data.dao.TransactionDao
import com.finsense.data.dao.VendorDao
import com.finsense.data.entity.Transaction
import com.finsense.data.entity.TransactionType
import com.finsense.data.entity.TransactionWithCategory
import com.finsense.data.entity.Vendor
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val vendorDao: VendorDao
) {

    fun getAllWithCategory(): Flow<List<TransactionWithCategory>> =
        transactionDao.getAllWithCategory()

    fun getByDateRangeWithCategory(startDate: Long, endDate: Long): Flow<List<TransactionWithCategory>> =
        transactionDao.getByDateRangeWithCategory(startDate, endDate)

    fun searchWithCategory(query: String): Flow<List<TransactionWithCategory>> =
        transactionDao.searchWithCategory(query)

    fun getRecentWithCategory(limit: Int = 15): Flow<List<TransactionWithCategory>> {
        val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
        return transactionDao.getRecentWithCategory(thirtyDaysAgo, limit)
    }

    fun getLatestWithCategory(limit: Int): Flow<List<TransactionWithCategory>> =
        transactionDao.getLatestWithCategory(limit)

    suspend fun existsBySmsId(smsId: String): Boolean =
        transactionDao.existsBySmsId(smsId)

    suspend fun addTransaction(transaction: Transaction): Long {
        val categorized = autoCategorize(transaction)
        return transactionDao.insert(categorized)
    }

    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: Transaction) =
        transactionDao.delete(transaction)

    suspend fun categorizeVendor(transaction: Transaction, categoryId: Long, cascade: Boolean) {
        transactionDao.update(transaction.copy(categoryId = categoryId))
        val vendorKey = transaction.normalizedVendorName ?: transaction.vendor
        val existing = vendorDao.findByName(vendorKey)
        if (existing != null) {
            vendorDao.update(existing.copy(categoryId = categoryId))
        } else {
            vendorDao.insert(Vendor(name = vendorKey, categoryId = categoryId))
        }
        if (cascade) {
            transactionDao.updateCategoryForVendorUncategorized(
                transaction.vendor,
                transaction.normalizedVendorName ?: transaction.vendor,
                categoryId
            )
        }
    }

    suspend fun applyVendorNormalization(vendor: Vendor) {
        val keywords = buildList {
            add(vendor.name)
            vendor.aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
        }
        keywords.forEach { keyword ->
            transactionDao.applyNormalizedVendorForKeyword(keyword, vendor.name)
        }
    }

    suspend fun monthlyTotals(currency: String, monthStartDay: Int = 1): Pair<Double, Double> {
        val (start, end) = periodMonthRange(monthStartDay)
        val debits = transactionDao.totalDebitForPeriod(start, end, currency)
        val credits = transactionDao.totalCreditForPeriod(start, end, currency)
        return debits to credits
    }

    suspend fun spentInCategoryForPeriod(
        categoryId: Long, startDate: Long, endDate: Long, currency: String
    ): Double = transactionDao.sumDebitByCategoryAndPeriod(categoryId, startDate, endDate, currency)

    private suspend fun autoCategorize(transaction: Transaction): Transaction {
        val vendor = transaction.vendor.trim()

        val matchedVendor = findVendorByKeyword(vendor)
        if (matchedVendor != null) {
            return transaction.copy(
                normalizedVendorName = matchedVendor.name,
                categoryId = transaction.categoryId ?: matchedVendor.categoryId
            )
        }

        if (transaction.categoryId != null) return transaction

        val searchText = (vendor + " " + transaction.description).lowercase()
        categoryDao.getAllOnce().forEach { category ->
            val keywords = category.keywords.split(",").map { it.trim().lowercase() }
            if (keywords.any { it.isNotEmpty() && searchText.contains(it) }) {
                return transaction.copy(categoryId = category.id)
            }
        }

        return transaction
    }

    private suspend fun findVendorByKeyword(extractedVendor: String): Vendor? {
        val lower = extractedVendor.lowercase()
        return vendorDao.getAllOnce().firstOrNull { v ->
            val keywords = buildList {
                add(v.name)
                v.aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
            }
            keywords.any { kw -> lower.contains(kw.lowercase()) }
        }
    }

    fun periodMonthRange(monthStartDay: Int): Pair<Long, Long> {
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
