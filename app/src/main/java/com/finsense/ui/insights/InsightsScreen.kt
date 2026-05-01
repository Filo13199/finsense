package com.finsense.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finsense.data.preferences.AppCurrency
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    contentPadding: PaddingValues,
    onNavigateToSlice: (startMs: Long, endMs: Long, filterType: String, filterValue: String, label: String, currency: AppCurrency) -> Unit = { _, _, _, _, _, _ -> },
    vm: InsightsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }

    val onSliceClick: (SpendingSlice) -> Unit = { slice ->
        val filterType = if (slice.vendorKey != null) "vendor" else "category"
        val filterValue = when {
            slice.vendorKey != null -> slice.vendorKey
            slice.categoryId != null -> slice.categoryId.toString()
            else -> "__uncategorized__"
        }
        onNavigateToSlice(state.periodStartMs, state.periodEndMs, filterType, filterValue, slice.label, state.currency)
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Period chips
        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InsightsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { vm.selectPeriod(period) },
                        label = { Text(period.label) }
                    )
                }
            }
        }

        // View mode toggle + filter button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    SegmentedButton(
                        selected = state.viewMode == InsightsViewMode.CATEGORIES,
                        onClick = { vm.selectViewMode(InsightsViewMode.CATEGORIES) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Categories") }
                    SegmentedButton(
                        selected = state.viewMode == InsightsViewMode.VENDORS,
                        onClick = { vm.selectViewMode(InsightsViewMode.VENDORS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Vendors") }
                }
                val excludedCount = state.excludedCategoryIds.size
                FilterChip(
                    selected = excludedCount > 0,
                    onClick = { showFilterSheet = true },
                    label = { Text(if (excludedCount == 0) "Filter" else "$excludedCount excluded") },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // Donut chart + total
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    slices = state.slices,
                    onSliceClick = onSliceClick,
                    modifier = Modifier.size(200.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Total spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        state.currency.formatCompact(state.totalSpent),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Empty state
        if (state.slices.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "No expenses recorded for this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Breakdown list
        items(state.slices) { slice ->
            SliceRow(slice = slice, currency = state.currency, onClick = { onSliceClick(slice) })
        }
    }

    if (showFilterSheet) {
        ExcludeFilterSheet(
            categories = state.categories,
            excludedIds = state.excludedCategoryIds,
            onToggle = { vm.toggleExcludeCategory(it) },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun DonutChart(
    slices: List<SpendingSlice>,
    onSliceClick: (SpendingSlice) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier.pointerInput(slices) {
            detectTapGestures { offset ->
                if (slices.isEmpty()) return@detectTapGestures
                val cx = size.width / 2f
                val cy = size.height / 2f
                val strokePx = size.width * 0.14f
                val outerRadius = size.width / 2f
                val innerRadius = outerRadius - strokePx
                val dx = offset.x - cx
                val dy = offset.y - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < innerRadius || dist > outerRadius) return@detectTapGestures
                val rawAngle = (atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)).toFloat()
                val normalizedAngle = (rawAngle + 90f + 360f) % 360f
                val gapDeg = if (slices.size == 1) 0f else 2f
                val available = 360f - gapDeg * slices.size
                var cumAngle = 0f
                for (slice in slices) {
                    val sweep = (available * slice.percentage).coerceAtLeast(1f)
                    if (normalizedAngle >= cumAngle && normalizedAngle < cumAngle + sweep) {
                        onSliceClick(slice)
                        return@detectTapGestures
                    }
                    cumAngle += sweep + gapDeg
                }
            }
        }
    ) {
        val strokePx = size.width * 0.14f
        val inset = strokePx / 2f
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        val topLeft = Offset(inset, inset)

        if (slices.isEmpty()) {
            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokePx)
            )
            return@Canvas
        }

        val gapDegrees = if (slices.size == 1) 0f else 2f
        val available = 360f - gapDegrees * slices.size
        var startAngle = -90f

        slices.forEach { slice ->
            val sweep = (available * slice.percentage).coerceAtLeast(1f)
            drawArc(
                color = Color(slice.color),
                startAngle = startAngle, sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
            )
            startAngle += sweep + gapDegrees
        }
    }
}

@Composable
private fun SliceRow(slice: SpendingSlice, currency: AppCurrency, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color(slice.color), CircleShape)
            )
            if (slice.icon.isNotEmpty()) {
                Text(slice.icon, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                slice.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    currency.formatCompact(slice.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${(slice.percentage * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LinearProgressIndicator(
            progress = { slice.percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(slice.color),
            trackColor = Color(slice.color).copy(alpha = 0.15f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcludeFilterSheet(
    categories: List<com.finsense.data.entity.Category>,
    excludedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Exclude from insights", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            categories.forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = cat.id in excludedIds,
                        onCheckedChange = { onToggle(cat.id) }
                    )
                    Text("${cat.icon} ${cat.name}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
