package com.finsense.ui.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finsense.data.entity.Category
import com.finsense.data.entity.RecurringFrequency
import com.finsense.data.entity.RecurringTransaction
import com.finsense.data.entity.TransactionType
import com.finsense.data.entity.TransactionWithCategory
import com.finsense.data.preferences.AppCurrency
import com.finsense.ui.dashboard.TransactionRow
import com.finsense.ui.dashboard.formatDate
import com.finsense.ui.theme.CreditGreen
import com.finsense.ui.theme.DebitRed
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    contentPadding: PaddingValues,
    vm: TransactionsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var detailTxc by remember { mutableStateOf<TransactionWithCategory?>(null) }
    var showDatePickerForDetail by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::onSearchQueryChange,
                placeholder = { Text("Search vendor or description") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 80.dp
                    )
                ) {
                    if (state.searchQuery.isBlank() && state.recurringTransactions.isNotEmpty()) {
                        item(key = "recurring_header") {
                            Text(
                                "Recurring",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(state.recurringTransactions, key = { "rec_${it.id}" }) { rule ->
                            RecurringRuleCard(
                                rule = rule,
                                currency = state.currency,
                                onDelete = { vm.deleteRecurring(rule) }
                            )
                        }
                    }

                    val grouped = state.transactions.groupBy { formatDate(it.transaction.date).substringBefore(",") }
                    grouped.forEach { (dateLabel, txcs) ->
                        item(key = "date_$dateLabel") {
                            Text(
                                dateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(txcs, key = { it.transaction.id }) { txc ->
                            SwipeToDeleteBox(
                                onDelete = { vm.deleteTransaction(txc.transaction) }
                            ) {
                                TransactionRow(
                                    txc, state.currency,
                                    onClick = { detailTxc = txc }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add transaction")
        }
    }

    if (showAddSheet) {
        AddTransactionSheet(
            categories = state.categories,
            onDismiss = { showAddSheet = false },
            onAdd = { amount, type, vendor, desc, catId ->
                vm.addManualTransaction(amount, type, vendor, desc, catId)
                showAddSheet = false
            },
            onAddRecurring = { amount, type, vendor, desc, catId, freq, day ->
                vm.addRecurringTransaction(amount, type, vendor, desc, catId, freq, day)
                showAddSheet = false
            }
        )
    }

    detailTxc?.let { txc ->
        TransactionDetailSheet(
            txc = txc,
            categories = state.categories,
            currency = state.currency,
            onDismiss = { detailTxc = null },
            onEditDate = { showDatePickerForDetail = true },
            onCategorize = { categoryId, cascade ->
                vm.categorizeTransaction(txc.transaction, categoryId, cascade)
                detailTxc = null
            }
        )

        if (showDatePickerForDetail) {
            EditDateDialog(
                initialDateMs = txc.transaction.date,
                onConfirm = { newMs ->
                    vm.updateDate(txc.transaction, newMs)
                    showDatePickerForDetail = false
                    detailTxc = null
                },
                onDismiss = { showDatePickerForDetail = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(
    txc: TransactionWithCategory,
    categories: List<Category>,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onEditDate: () -> Unit,
    onCategorize: (categoryId: Long, cascade: Boolean) -> Unit
) {
    val tx = txc.transaction
    var showCategoryPicker by remember { mutableStateOf(tx.categoryId == null) }
    var selectedCategory by remember { mutableStateOf(txc.category) }
    var cascade by remember { mutableStateOf(true) }

    val amountStr = if (tx.currency == currency.name) currency.formatAmount(tx.amount)
                    else "${tx.currency} ${"%.2f".format(tx.amount)}"
    val amountText = "${if (tx.type == TransactionType.DEBIT) "−" else "+"}$amountStr"
    val amountColor = if (tx.type == TransactionType.DEBIT) DebitRed else CreditGreen

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(txc.category?.icon ?: "📦", style = MaterialTheme.typography.displaySmall)
                Column {
                    Text(tx.normalizedVendorName ?: tx.vendor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(amountText, style = MaterialTheme.typography.titleMedium,
                        color = amountColor, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider()

            // Date row
            ListItem(
                headlineContent = { Text("Date") },
                supportingContent = { Text(formatDate(tx.date)) },
                leadingContent = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onEditDate() }
            )

            HorizontalDivider()

            // Category row
            ListItem(
                headlineContent = { Text("Category") },
                supportingContent = { Text(txc.category?.name ?: "Uncategorized") },
                leadingContent = {
                    Text(txc.category?.icon ?: "📦", style = MaterialTheme.typography.titleMedium)
                },
                trailingContent = {
                    Icon(
                        if (showCategoryPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { showCategoryPicker = !showCategoryPicker }
            )

            if (showCategoryPicker) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
                    categories.forEach { cat ->
                        val isSelected = selectedCategory?.id == cat.id
                        Card(
                            onClick = { selectedCategory = cat },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(cat.icon, style = MaterialTheme.typography.titleMedium)
                                Text(cat.name, modifier = Modifier.weight(1f))
                                if (isSelected) Icon(
                                    Icons.Default.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Apply to all \"${tx.normalizedVendorName ?: tx.vendor}\" transactions",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Updates other uncategorized transactions from this vendor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = cascade, onCheckedChange = { cascade = it })
                    }

                    Button(
                        onClick = { selectedCategory?.let { onCategorize(it.id, cascade) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedCategory != null
                    ) {
                        Text("Save Category")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Description
            if (tx.description.isNotBlank()) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Description") },
                    supportingContent = { Text(tx.description, maxLines = 5) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) }
                )
            }

            // Source
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(if (tx.isManual) "Manual entry" else "From SMS") },
                leadingContent = {
                    Icon(
                        if (tx.isManual) Icons.Default.Edit else Icons.Default.Sms,
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDateDialog(
    initialDateMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMs)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selectedMs = datePickerState.selectedDateMillis ?: return@TextButton
                val localMs = Instant.ofEpochMilli(selectedMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                onConfirm(localMs)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { it * 0.4f }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (state.targetValue == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.errorContainer
                else Color.Transparent,
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, shape = CardDefaults.shape)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        content()
    }
}

@Composable
private fun RecurringRuleCard(
    rule: RecurringTransaction,
    currency: AppCurrency,
    onDelete: () -> Unit
) {
    val scheduleLabel = when (rule.frequency) {
        RecurringFrequency.MONTHLY -> "Monthly · ${dayWithSuffix(rule.dayOfPeriod)}"
        RecurringFrequency.WEEKLY -> {
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val name = dayNames.getOrElse(rule.dayOfPeriod - 1) { "day ${rule.dayOfPeriod}" }
            "Weekly · $name"
        }
    }
    val amountText = if (rule.type == TransactionType.DEBIT)
        "−${currency.formatAmount(rule.amount)}"
    else
        "+${currency.formatAmount(rule.amount)}"
    val amountColor = if (rule.type == TransactionType.DEBIT)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.vendor, style = MaterialTheme.typography.bodyMedium)
                Text(
                    scheduleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                amountText,
                style = MaterialTheme.typography.bodyMedium,
                color = amountColor,
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete recurring",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun dayWithSuffix(day: Int): String = when {
    day in 11..13 -> "${day}th"
    day % 10 == 1 -> "${day}st"
    day % 10 == 2 -> "${day}nd"
    day % 10 == 3 -> "${day}rd"
    else -> "${day}th"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionSheet(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAdd: (Double, TransactionType, String, String, Long?) -> Unit,
    onAddRecurring: (Double, TransactionType, String, String, Long?, RecurringFrequency, Int) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.DEBIT) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
    var dayOfPeriod by remember { mutableStateOf(1) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Transaction", style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TransactionType.DEBIT,
                    onClick = { type = TransactionType.DEBIT },
                    label = { Text("Expense") }
                )
                FilterChip(
                    selected = type == TransactionType.CREDIT,
                    onClick = { type = TransactionType.CREDIT },
                    label = { Text("Income") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recurring", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
            }

            if (isRecurring) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurringFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                if (frequency == RecurringFrequency.MONTHLY) {
                    OutlinedTextField(
                        value = if (dayOfPeriod == 0) "" else dayOfPeriod.toString(),
                        onValueChange = { v ->
                            val d = v.toIntOrNull()
                            if (v.isEmpty()) dayOfPeriod = 0
                            else if (d != null && d in 1..31) dayOfPeriod = d
                        },
                        label = { Text("Day of month (1–31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayLabels.forEachIndexed { i, label ->
                            FilterChip(
                                selected = dayOfPeriod == i + 1,
                                onClick = { dayOfPeriod = i + 1 },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vendor,
                onValueChange = { vendor = it },
                label = { Text("Vendor / Payee") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.let { "${it.icon} ${it.name}" } ?: "Select category",
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

            val amountValid = amount.toDoubleOrNull() != null
            val dayValid = !isRecurring || dayOfPeriod in 1..31

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: return@Button
                    if (isRecurring) {
                        onAddRecurring(amt, type, vendor, description, selectedCategory?.id, frequency, dayOfPeriod)
                    } else {
                        onAdd(amt, type, vendor, description, selectedCategory?.id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amountValid && dayValid
            ) {
                Text(if (isRecurring) "Add Recurring" else "Add Transaction")
            }
        }
    }
}
