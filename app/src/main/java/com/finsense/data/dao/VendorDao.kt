package com.finsense.data.dao

import androidx.room.*
import com.finsense.data.entity.Vendor
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vendor: Vendor): Long

    @Update
    suspend fun update(vendor: Vendor)

    @Delete
    suspend fun delete(vendor: Vendor)

    @Query("SELECT * FROM vendors ORDER BY name ASC")
    fun getAll(): Flow<List<Vendor>>

    @Query("""
        SELECT * FROM vendors
        WHERE lower(name) LIKE '%' || lower(:name) || '%'
           OR lower(aliases) LIKE '%' || lower(:name) || '%'
        LIMIT 1
    """)
    suspend fun findByName(name: String): Vendor?
}
