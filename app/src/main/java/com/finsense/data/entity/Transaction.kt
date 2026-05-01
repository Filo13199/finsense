package com.finsense.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { DEBIT, CREDIT }

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["smsId"], unique = true),
        Index(value = ["recurringId"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val vendor: String,
    val description: String,
    val categoryId: Long?,
    val date: Long,
    val currency: String = "EGP",
    val smsId: String? = null,
    val smsBody: String? = null,
    val isManual: Boolean = false,
    val recurringId: Long? = null,
    @ColumnInfo(name = "normalized_vendor_name") val normalizedVendorName: String? = null
)
