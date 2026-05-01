package com.finsense.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.finsense.data.dao.BudgetDao
import com.finsense.data.dao.CategoryDao
import com.finsense.data.dao.RecurringTransactionDao
import com.finsense.data.dao.TransactionDao
import com.finsense.data.dao.VendorDao
import com.finsense.data.db.FinsenseDatabase
import com.finsense.data.db.Migration1To2
import com.finsense.data.db.Migration2To3
import com.finsense.data.db.Migration3To4
import com.finsense.data.db.Migration4To5
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FinsenseDatabase {
        return Room.databaseBuilder(context, FinsenseDatabase::class.java, "finsense.db")
            .addMigrations(Migration1To2(context), Migration2To3, Migration3To4, Migration4To5)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaultCategories(db)
                }
            })
            .build()
    }

    private fun seedDefaultCategories(db: SupportSQLiteDatabase) {
        val categories = listOf(
            Triple("Food & Dining",  "🍔", Pair(0xFFE57373L, "zomato,swiggy,restaurant,food,cafe,pizza,burger,hotel,eat,dining")),
            Triple("Groceries",      "🛒", Pair(0xFF81C784L, "bigbasket,grofers,dmart,supermarket,grocery,vegetables,fruits,blinkit,zepto,nature")),
            Triple("Transport",      "🚗", Pair(0xFF64B5F6L, "uber,ola,rapido,metro,bus,auto,petrol,fuel,irctc,train,flight,indigo,makemytrip")),
            Triple("Shopping",       "🛍️", Pair(0xFFBA68C8L, "amazon,flipkart,myntra,ajio,meesho,snapdeal,nykaa,shop,mall")),
            Triple("Entertainment",  "🎬", Pair(0xFFFFB74DL, "netflix,prime,hotstar,spotify,bookmyshow,pvr,inox,movie,game")),
            Triple("Healthcare",     "💊", Pair(0xFF4DB6ACL, "pharmacy,hospital,medical,clinic,doctor,medicine,health,apollo,1mg,practo,chemist")),
            Triple("Utilities",      "💡", Pair(0xFFFFD54FL, "electricity,water,gas,internet,broadband,wifi,mobile,recharge,jio,airtel,bsnl,vodafone")),
            Triple("EMI & Loans",    "🏦", Pair(0xFFEF9A9AL, "emi,loan,mortgage,insurance,premium,lic")),
            Triple("Education",      "📚", Pair(0xFF4FC3F7L, "school,college,tuition,course,udemy,coursera,byju,unacademy,book")),
            Triple("Transfers",      "💸", Pair(0xFF90A4AEL, "transfer,upi,neft,imps,rtgs")),
            Triple("Other",          "📦", Pair(0xFFBCAAA4L, ""))
        )
        categories.forEach { (name, icon, colorKw) ->
            val (color, keywords) = colorKw
            db.execSQL(
                "INSERT OR IGNORE INTO categories (name, icon, color, keywords) VALUES (?, ?, ?, ?)",
                arrayOf(name, icon, color, keywords)
            )
        }
    }

    @Provides fun provideTransactionDao(db: FinsenseDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCategoryDao(db: FinsenseDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideBudgetDao(db: FinsenseDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideVendorDao(db: FinsenseDatabase): VendorDao = db.vendorDao()
    @Provides fun provideRecurringTransactionDao(db: FinsenseDatabase): RecurringTransactionDao = db.recurringTransactionDao()
}
