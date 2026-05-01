package com.finsense.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finsense.data.entity.Category
import com.finsense.ui.vendors.AddEditVendorSheet
import com.finsense.ui.vendors.VendorsContent
import com.finsense.ui.vendors.VendorsViewModel
import com.finsense.data.entity.Vendor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    contentPadding: PaddingValues,
    vm: CategoriesViewModel = hiltViewModel(),
    vendorsVm: VendorsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val vendorsState by vendorsVm.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showVendorSheet by remember { mutableStateOf(false) }
    var editingVendor by remember { mutableStateOf<Vendor?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Categories") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Vendors") }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = contentPadding.calculateBottomPadding() + 80.dp,
                            start = 8.dp,
                            end = 8.dp
                        ),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.categories, key = { it.id }) { cat ->
                            CategoryCard(category = cat, onDelete = { vm.deleteCategory(cat) })
                        }
                    }
                    FloatingActionButton(
                        onClick = { showCategorySheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add category")
                    }
                }
                1 -> {
                    VendorsContent(
                        vendors = vendorsState.vendors,
                        categories = vendorsState.categories,
                        contentPadding = contentPadding,
                        onAdd = { editingVendor = null; showVendorSheet = true },
                        onEdit = { vendor -> editingVendor = vendor; showVendorSheet = true },
                        onDelete = { vendorsVm.deleteVendor(it) }
                    )
                }
            }
        }
    }

    if (showCategorySheet) {
        AddCategorySheet(
            onDismiss = { showCategorySheet = false },
            onAdd = { name, icon, color ->
                vm.addCategory(name, icon, color)
                showCategorySheet = false
            }
        )
    }

    if (showVendorSheet) {
        AddEditVendorSheet(
            vendor = editingVendor,
            categories = vendorsState.categories,
            onDismiss = { showVendorSheet = false; editingVendor = null },
            onSave = { name, aliases, catId ->
                val current = editingVendor
                if (current != null) {
                    vendorsVm.updateVendor(current.copy(name = name, aliases = aliases, categoryId = catId))
                } else {
                    vendorsVm.addVendor(name, aliases, catId)
                }
                showVendorSheet = false
                editingVendor = null
            }
        )
    }
}

@Composable
private fun CategoryCard(category: Category, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(category.color).copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.icon, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(category.name, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategorySheet(
    onDismiss: () -> Unit,
    onAdd: (String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New Category", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = icon,
                onValueChange = { icon = it },
                label = { Text("Emoji") },
                placeholder = { Text("📦") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onAdd(name, icon.ifBlank { "📦" }, 0xFF90A4AEL) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Create Category")
            }
        }
    }
}
