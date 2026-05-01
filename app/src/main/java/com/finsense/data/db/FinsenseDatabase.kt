package com.finsense.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.finsense.data.dao.BudgetDao
import com.finsense.data.dao.CategoryDao
import com.finsense.data.dao.RecurringTransactionDao
import com.finsense.data.dao.TransactionDao
import com.finsense.data.dao.VendorDao
import com.finsense.data.entity.*

class Converters {
    @TypeConverter fun fromTransactionType(v: TransactionType): String = v.name
    @TypeConverter fun toTransactionType(v: String): TransactionType = TransactionType.valueOf(v)
    @TypeConverter fun fromBudgetPeriod(v: BudgetPeriod): String = v.name
    @TypeConverter fun toBudgetPeriod(v: String): BudgetPeriod = BudgetPeriod.valueOf(v)
    @TypeConverter fun fromRecurringFrequency(v: RecurringFrequency): String = v.name
    @TypeConverter fun toRecurringFrequency(v: String): RecurringFrequency = RecurringFrequency.valueOf(v)
}

// Sets existing rows to the user's previously-saved currency preference rather than a hardcoded default.
class Migration1To2(private val context: Context) : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'EGP'")
        val savedCurrency = context
            .getSharedPreferences("finsense_prefs", Context.MODE_PRIVATE)
            .getString("currency", "EGP") ?: "EGP"
        if (savedCurrency != "EGP") {
            database.execSQL("UPDATE transactions SET currency = ?", arrayOf(savedCurrency))
        }
    }
}

object Migration2To3 : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE transactions ADD COLUMN recurringId INTEGER DEFAULT NULL")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_recurringId ON transactions(recurringId)")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS recurring_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                amount REAL NOT NULL,
                type TEXT NOT NULL,
                vendor TEXT NOT NULL,
                description TEXT NOT NULL,
                categoryId INTEGER DEFAULT NULL,
                currency TEXT NOT NULL,
                frequency TEXT NOT NULL,
                dayOfPeriod INTEGER NOT NULL,
                lastGeneratedAt INTEGER DEFAULT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE SET NULL
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_transactions_categoryId ON recurring_transactions(categoryId)")
    }
}

@Database(
    entities = [Transaction::class, Category::class, Budget::class, Vendor::class, RecurringTransaction::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FinsenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun vendorDao(): VendorDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
}
