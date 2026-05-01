package com.finsense.ui.vendors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.finsense.data.entity.Category
import com.finsense.data.entity.Vendor

@Composable
fun VendorsContent(
    vendors: List<Vendor>,
    categories: List<Category>,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (Vendor) -> Unit,
    onDelete: (Vendor) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (vendors.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))
                Text("No vendors yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap + to add a vendor with keywords.\nAny SMS vendor matching a keyword will be normalized to the canonical name.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 80.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vendors, key = { it.id }) { vendor ->
                    VendorCard(
                        vendor = vendor,
                        category = categories.find { it.id == vendor.categoryId },
                        onEdit = { onEdit(vendor) },
                        onDelete = { onDelete(vendor) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add vendor")
        }
    }
}

@Composable
private fun VendorCard(
    vendor: Vendor,
    category: Category?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val keywords = remember(vendor.aliases) {
        vendor.aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    vendor.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (keywords.isNotEmpty()) {
                    Text(
                        keywords.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (category != null) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${category.icon} ${category.name}", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete vendor",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditVendorSheet(
    vendor: Vendor?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, aliases: String, categoryId: Long?) -> Unit
) {
    var name by remember(vendor) { mutableStateOf(vendor?.name ?: "") }
    var keywords by remember(vendor) {
        mutableStateOf(
            vendor?.aliases?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        )
    }
    var keywordInput by remember { mutableStateOf("") }
    var selectedCategory by remember(vendor) {
        mutableStateOf(categories.find { it.id == vendor?.categoryId })
    }
    var categoryExpanded by remember { mutableStateOf(false) }

    fun addKeyword() {
        val kw = keywordInput.trim().lowercase()
        if (kw.isNotEmpty() && kw !in keywords) {
            keywords = keywords + kw
        }
        keywordInput = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (vendor == null) "New Vendor" else "Edit Vendor",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Canonical name") },
                supportingText = { Text("The name that will be shown for matched transactions") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Keywords",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Any keyword found (case-insensitive) in the SMS vendor text will map to the canonical name above",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (keywords.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        keywords.forEach { kw ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(kw) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { keywords = keywords - kw },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove keyword",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        label = { Text("Add keyword") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addKeyword() })
                    )
                    IconButton(
                        onClick = { addKeyword() },
                        enabled = keywordInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add keyword")
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.let { "${it.icon} ${it.name}" } ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category (optional)") },
                    supportingText = { Text("Auto-assigned when this vendor is matched") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = { selectedCategory = null; categoryExpanded = false }
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.icon} ${cat.name}") },
                            onClick = { selectedCategory = cat; categoryExpanded = false }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        keywords.joinToString(","),
                        selectedCategory?.id
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text(if (vendor == null) "Add Vendor" else "Save Changes")
            }
        }
    }
}
