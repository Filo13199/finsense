package com.finsense.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finsense.data.entity.Category
import com.finsense.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        categoryRepository.getAll()
            .onEach { cats -> _uiState.value = CategoriesUiState(categories = cats, isLoading = false) }
            .launchIn(viewModelScope)
    }

    fun addCategory(name: String, icon: String, color: Long) {
        viewModelScope.launch {
            categoryRepository.insert(Category(name = name, icon = icon, color = color))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { categoryRepository.update(category) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { categoryRepository.delete(category) }
    }
}
