package com.finsense.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecurringFrequency { MONTHLY, WEEKLY }

@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val vendor: String,
    val description: String,
    val categoryId: Long?,
    val currency: String,
    val frequency: RecurringFrequency,
    // MONTHLY: 1–31 (capped at last day of month). WEEKLY: 1=Mon … 7=Sun.
    val dayOfPeriod: Int,
    val lastGeneratedAt: Long? = null,
    val isActive: Boolean = true
)
