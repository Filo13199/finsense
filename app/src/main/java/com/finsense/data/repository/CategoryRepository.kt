package com.finsense.data.repository

import com.finsense.data.dao.CategoryDao
import com.finsense.data.entity.Category
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun getAll(): Flow<List<Category>> = categoryDao.getAll()
    suspend fun getAllOnce(): List<Category> = categoryDao.getAllOnce()
    suspend fun getById(id: Long): Category? = categoryDao.getById(id)
    suspend fun insert(category: Category): Long = categoryDao.insert(category)
    suspend fun update(category: Category) = categoryDao.update(category)
    suspend fun delete(category: Category) = categoryDao.delete(category)
}
