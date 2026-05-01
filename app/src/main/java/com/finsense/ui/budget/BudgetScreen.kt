package com.finsense.ui.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finsense.data.entity.BudgetPeriod
import com.finsense.data.entity.Category
import com.finsense.data.model.BudgetWithSpent
import com.finsense.data.preferences.AppCurrency
import com.finsense.ui.theme.CreditGreen
import com.finsense.ui.theme.DebitRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    contentPadding: PaddingValues,
    vm: BudgetViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 80.dp,
                start = 16.dp,
                end = 16.dp
            ),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.budgets.isEmpty() && !state.isLoading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No budgets yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap + to create your first budget.\nTransactions will automatically deduct from it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(state.budgets, key = { it.budget.id }) { bws ->
                BudgetCard(bws = bws, currency = state.currency, onDelete = { vm.deleteBudget(bws.budget) })
            }
        }

        FloatingActionButton(
            onClick = { showSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add budget")
        }
    }

    if (showSheet) {
        AddBudgetSheet(
            categories = state.categories,
            onDismiss = { showSheet = false },
            onAdd = { name, catId, amount, period, excludedIds ->
                vm.addBudget(name, catId, amount, period, excludedIds)
                showSheet = false
            }
        )
    }
}

@Composable
private fun BudgetCard(bws: BudgetWithSpent, currency: AppCurrency, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BudgetRing(bws = bws, ringSize = 72.dp)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(bws.budget.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    bws.budget.period.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (bws.excludedCategories.isNotEmpty()) {
                    Text(
                        "Excludes: ${bws.excludedCategories.joinToString { "${it.icon} ${it.name}" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${currency.formatAmount(bws.spent)} / ${currency.formatAmount(bws.budget.amount)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (bws.budget.period != BudgetPeriod.DAILY) {
                    if (bws.isOverBudget) {
                        Text(
                            "${currency.formatAmount(bws.dailyOverspend)}/day over budget",
                            style = MaterialTheme.typography.bodySmall,
                            color = DebitRed
                        )
                    } else {
                        Text(
                            "${currency.formatAmount(bws.dailyAllowance)}/day remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = CreditGreen
                        )
                    }
                } else {
                    if (bws.isOverBudget) {
                        Text(
                            "Over by ${currency.formatAmount(bws.spent - bws.budget.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DebitRed
                        )
                    } else {
                        Text(
                            "${currency.formatAmount(bws.remaining)} remaining today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun BudgetRing(bws: BudgetWithSpent, ringSize: Dp) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = size.width * 0.14f
            val inset = strokePx / 2f
            val diameter = size.width - strokePx
            val topLeft = Offset(inset, inset)
            val arcSize = Size(diameter, diameter)

            // Gray track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx)
            )

            if (bws.isOverBudget) {
                drawArc(
                    color = DebitRed,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx)
                )
            } else {
                val spentSweep = 360f * bws.percentage
                val remainingSweep = 360f - spentSweep

                if (spentSweep > 0f) {
                    drawArc(
                        color = DebitRed,
                        startAngle = -90f,
                        sweepAngle = spentSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                }
                if (remainingSweep > 0f) {
                    drawArc(
                        color = CreditGreen,
                        startAngle = -90f + spentSweep,
                        sweepAngle = remainingSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                }
            }
        }

        Text(bws.category?.icon ?: "📦", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetSheet(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAdd: (String, Long?, Double, BudgetPeriod, List<Long>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val period = BudgetPeriod.MONTHLY
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var excludedIds by remember { mutableStateOf(emptySet<Long>()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New Budget", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Budget name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Limit amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Period: Monthly",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.let { "${it.icon} ${it.name}" } ?: "All categories",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All categories") },
                        onClick = {
                            selectedCategory = null
                            categoryExpanded = false
                        }
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.icon} ${cat.name}") },
                            onClick = {
                                selectedCategory = cat
                                excludedIds = emptySet()
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedCategory == null && categories.isNotEmpty()) {
                Text(
                    "Exclude from budget",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                categories.forEach { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = cat.id in excludedIds,
                            onCheckedChange = { checked ->
                                excludedIds = if (checked) excludedIds + cat.id else excludedIds - cat.id
                            }
                        )
                        Text("${cat.icon} ${cat.name}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: return@Button
                    onAdd(
                        name.ifBlank { selectedCategory?.name ?: "Budget" },
                        selectedCategory?.id,
                        amt,
                        period,
                        excludedIds.toList()
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amount.toDoubleOrNull() != null
            ) {
                Text("Create Budget")
            }
        }
    }
}
