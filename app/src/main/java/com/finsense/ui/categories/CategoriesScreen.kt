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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    contentPadding: PaddingValues,
    vm: CategoriesViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add category")
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(8.dp)) }
            items(state.categories, key = { it.id }) { cat ->
                CategoryCard(
                    category = cat,
                    onDelete = { vm.deleteCategory(cat) }
                )
            }
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showSheet) {
        AddCategorySheet(
            onDismiss = { showSheet = false },
            onAdd = { name, icon, color ->
                vm.addCategory(name, icon, color)
                showSheet = false
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
                Icon(Icons.Default.Delete, contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddCategorySheet(
    onDismiss: () -> Unit,
    onAdd: (String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📦") }

    val presetIcons = listOf("🍔","🛒","🚗","🛍️","🎬","💊","💡","🏦","📚","💸","📦","✈️","🏠","💻","🎮","🌿")
    val presetColors = listOf(
        0xFFE57373L, 0xFF81C784L, 0xFF64B5F6L, 0xFFBA68C8L,
        0xFFFFB74DL, 0xFF4DB6ACL, 0xFFFFD54FL, 0xFF90A4AEL
    )
    var selectedColor by remember { mutableLongStateOf(presetColors[0]) }

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

            Text("Icon", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetIcons.forEach { emoji ->
                    FilterChip(
                        selected = icon == emoji,
                        onClick = { icon = emoji },
                        label = { Text(emoji, style = MaterialTheme.typography.titleMedium) }
                    )
                }
            }

            Text("Color", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetColors.forEach { colorValue ->
                    val color = Color(colorValue)
                    FilterChip(
                        selected = selectedColor == colorValue,
                        onClick = { selectedColor = colorValue },
                        label = {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = MaterialTheme.shapes.small,
                                    color = color
                                ) {}
                            }
                        }
                    )
                }
            }

            Button(
                onClick = { onAdd(name, icon, selectedColor) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Create Category")
            }
        }
    }
}
