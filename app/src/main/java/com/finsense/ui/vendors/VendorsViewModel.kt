package com.finsense.ui.vendors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finsense.data.dao.VendorDao
import com.finsense.data.entity.Category
import com.finsense.data.entity.Vendor
import com.finsense.data.repository.CategoryRepository
import com.finsense.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VendorsUiState(
    val vendors: List<Vendor> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class VendorsViewModel @Inject constructor(
    private val vendorDao: VendorDao,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VendorsUiState())
    val uiState: StateFlow<VendorsUiState> = _uiState.asStateFlow()

    init {
        combine(vendorDao.getAll(), categoryRepository.getAll()) { vendors, categories ->
            VendorsUiState(vendors = vendors, categories = categories, isLoading = false)
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    fun addVendor(name: String, aliases: String, categoryId: Long?) {
        viewModelScope.launch {
            val id = vendorDao.insert(Vendor(name = name, aliases = aliases, categoryId = categoryId))
            transactionRepository.applyVendorNormalization(
                Vendor(id = id, name = name, aliases = aliases, categoryId = categoryId)
            )
        }
    }

    fun updateVendor(vendor: Vendor) {
        viewModelScope.launch {
            vendorDao.update(vendor)
            transactionRepository.applyVendorNormalization(vendor)
        }
    }

    fun deleteVendor(vendor: Vendor) {
        viewModelScope.launch { vendorDao.delete(vendor) }
    }
}
