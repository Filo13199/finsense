package com.finsense.data.dao

import androidx.room.*
import com.finsense.data.entity.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("SELECT * FROM budgets ORDER BY name ASC")
    fun getAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY name ASC")
    suspend fun getAllOnce(): List<Budget>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getByCategory(categoryId: Long): Budget?
}
