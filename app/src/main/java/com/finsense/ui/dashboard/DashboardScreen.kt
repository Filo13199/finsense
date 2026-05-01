package com.finsense.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finsense.data.entity.TransactionType
import com.finsense.data.entity.TransactionWithCategory
import com.finsense.data.model.BudgetWithSpent
import com.finsense.data.preferences.AppCurrency
import com.finsense.ui.theme.CreditGreen
import com.finsense.ui.theme.DebitRed
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("This Month", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { MonthlyOverviewCard(expense = state.monthlyExpense, income = state.monthlyIncome, currency = state.currency) }

        if (state.budgets.isNotEmpty()) {
            item {
                Text("Budgets", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.budgets) { b -> BudgetProgressCard(b, state.currency) }
        }

        item {
            Text("Recent Transactions", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.recentTransactions.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "No transactions yet. Import from SMS or add manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.recentTransactions) { txc -> TransactionRow(txc, state.currency) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MonthlyOverviewCard(expense: Double, income: Double, currency: AppCurrency) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AmountColumn("Income", income, CreditGreen, currency)
            VerticalDivider(modifier = Modifier.height(48.dp))
            AmountColumn("Expenses", expense, DebitRed, currency)
            VerticalDivider(modifier = Modifier.height(48.dp))
            AmountColumn("Balance", income - expense,
                if (income >= expense) CreditGreen else DebitRed, currency)
        }
    }
}

@Composable
private fun AmountColumn(label: String, amount: Double, color: Color, currency: AppCurrency) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            currency.formatCompact(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun BudgetProgressCard(bws: BudgetWithSpent, currency: AppCurrency) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bws.category?.icon ?: "📦", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(bws.budget.name, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "${currency.formatAmount(bws.spent)} / ${currency.formatAmount(bws.budget.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bws.isOverBudget) DebitRed else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { bws.percentage },
                modifier = Modifier.fillMaxWidth(),
                color = if (bws.isOverBudget) DebitRed else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (bws.isOverBudget) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Over by ${currency.formatAmount(bws.spent - bws.budget.amount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DebitRed
                )
            }
        }
    }
}

@Composable
fun TransactionRow(txc: TransactionWithCategory, currency: AppCurrency, onClick: (() -> Unit)? = null) {
    val tx = txc.transaction
    Card(modifier = Modifier.fillMaxWidth().let { m -> if (onClick != null) m.clickable { onClick() } else m }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = txc.category?.icon ?: "📦",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.width(40.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.vendor, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    txc.category?.name ?: "Uncategorized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatDate(tx.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val amountStr = if (tx.currency == currency.name) {
                currency.formatAmount(tx.amount)
            } else {
                "${tx.currency} ${"%.2f".format(tx.amount)}"
            }
            Text(
                text = "${if (tx.type == TransactionType.DEBIT) "−" else "+"}$amountStr",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (tx.type == TransactionType.DEBIT) DebitRed else CreditGreen
            )
        }
    }
}

private val dateFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
fun formatDate(millis: Long): String = dateFmt.format(Date(millis))
