package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KasMonthlyRecap
import com.example.ui.theme.*
import com.example.ui.viewmodel.WargaViewModel
import com.example.util.KasRecapHelper
import java.util.Locale
import kotlin.math.abs

@Composable
fun KasRolling12LineChartCard(
    rolling12: List<KasMonthlyRecap>,
    selectedMonthKey: String,
    modifier: Modifier = Modifier
) {
    val chartData = remember(rolling12) {
        if (rolling12.isNotEmpty()) rolling12 else KasRecapHelper.buildTimelineRecap(emptyList())
    }

    val initialStartIndex = remember(chartData) {
        val dec2025Idx = chartData.indexOfFirst { it.periodeBulan == "2025-12" }
        if (dec2025Idx >= 0) dec2025Idx else 0
    }

    var inspectedIndex by remember(chartData) {
        mutableIntStateOf(initialStartIndex)
    }

    LaunchedEffect(selectedMonthKey, chartData) {
        if (selectedMonthKey != "Semua") {
            val matchIdx = chartData.indexOfFirst { it.periodeBulan == selectedMonthKey }
            if (matchIdx != -1) {
                inspectedIndex = matchIdx
            }
        } else {
            inspectedIndex = initialStartIndex
        }
    }

    val safeIndex = inspectedIndex.coerceIn(0, (chartData.size - 1).coerceAtLeast(0))
    val activePoint = chartData.getOrNull(safeIndex)

    val columnWidth: Dp = 104.dp
    val plotHeight: Dp = 148.dp

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val columnWidthPx = with(density) { columnWidth.toPx() }
    LaunchedEffect(chartData, selectedMonthKey) {
        val targetIndex = if (selectedMonthKey != "Semua") {
            chartData.indexOfFirst { it.periodeBulan == selectedMonthKey }
                .let { if (it >= 0) it else initialStartIndex }
        } else {
            initialStartIndex
        }
        scrollState.scrollTo((targetIndex * columnWidthPx).toInt().coerceAtLeast(0))
    }

    // Compute clean Y-axis scale bounds
    val rawMin = chartData.minOfOrNull { it.saldoAkhir } ?: 0L
    val rawMax = chartData.maxOfOrNull { it.saldoAkhir } ?: 0L
    val minY = minOf(0L, rawMin)
    val maxY = if (rawMax <= minY) minY + 100_000L else rawMax
    val ySpan = (maxY - minY).coerceAtLeast(1L)
    val yTicks = listOf(
        maxY,
        minY + (ySpan * 2L) / 3L,
        minY + ySpan / 3L,
        minY
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("kas_rolling12_chart_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Title & Range Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SkySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = "Grafik Tren Saldo Kas",
                            tint = NavyPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Grafik Tren Saldo Kas",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        val startLabel = chartData.firstOrNull()?.labelBulan.orEmpty()
                        val endLabel = chartData.lastOrNull()?.labelBulan.orEmpty()
                        Text(
                            text = if (startLabel.isNotBlank() && endLabel.isNotBlank()) {
                                "$startLabel – $endLabel"
                            } else {
                                "Rekap Saldo Bulanan"
                            },
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Inspected Month Highlight Banner
            if (activePoint != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activePoint.labelBulan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Masuk ${formatCompactRupiah(activePoint.totalMasuk)} • Keluar ${formatCompactRupiah(activePoint.totalKeluar)}",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Saldo Akhir",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = WargaViewModel.formatRupiah(activePoint.saldoAkhir),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (activePoint.saldoAkhir >= 0L) NavyPrimary else RedExpense
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Line Chart Row: Fixed Y-Axis on Left + Horizontally Scrollable 12-Month Line Plot & X-Axis Labels
            Row(modifier = Modifier.fillMaxWidth()) {
                // Y-Axis Ticks Column
                Column(
                    modifier = Modifier
                        .width(58.dp)
                        .height(plotHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    yTicks.forEach { tickValue ->
                        Text(
                            text = formatCompactRupiah(tickValue),
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }

                // Scrollable Plot Area + Full Indonesian Month+Year Labels
                val totalChartWidth = columnWidth * chartData.size
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .width(totalChartWidth)
                            .height(plotHeight)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 14.dp, bottom = 10.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val count = chartData.size.coerceAtLeast(1)
                            val slotW = w / count

                            // 4 dashed horizontal gridlines matching Y-axis ticks
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            for (i in 0..3) {
                                val gy = (h / 3f) * i
                                drawLine(
                                    color = Color(0xFFE2E8F0),
                                    start = Offset(0f, gy),
                                    end = Offset(w, gy),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }

                            val points = chartData.mapIndexed { idx, recap ->
                                val x = slotW * idx + (slotW / 2f)
                                val ratio = (recap.saldoAkhir - minY).toFloat() / ySpan.toFloat()
                                val y = h - (ratio.coerceIn(0f, 1f) * h)
                                Offset(x, y)
                            }

                            if (points.size >= 2) {
                                val fillPath = Path().apply {
                                    moveTo(points.first().x, h)
                                    points.forEach { pt -> lineTo(pt.x, pt.y) }
                                    lineTo(points.last().x, h)
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            AccentBlue.copy(alpha = 0.22f),
                                            AccentBlue.copy(alpha = 0.02f)
                                        )
                                    )
                                )

                                val linePath = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    for (i in 1 until points.size) {
                                        lineTo(points[i].x, points[i].y)
                                    }
                                }
                                drawPath(
                                    path = linePath,
                                    color = NavyPrimary,
                                    style = Stroke(
                                        width = 2.8.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // Vertical guide line for inspected month
                            points.getOrNull(safeIndex)?.let { selectedPt ->
                                drawLine(
                                    color = AccentBlue.copy(alpha = 0.45f),
                                    start = Offset(selectedPt.x, 0f),
                                    end = Offset(selectedPt.x, h),
                                    strokeWidth = 1.2.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }

                            // Data nodes
                            points.forEachIndexed { idx, pt ->
                                val isSelected = idx == safeIndex
                                val hasActivity = chartData[idx].jumlahTransaksi > 0 ||
                                    chartData[idx].totalMasuk > 0L ||
                                    chartData[idx].totalKeluar > 0L

                                if (isSelected) {
                                    drawCircle(
                                        color = AccentBlue.copy(alpha = 0.22f),
                                        radius = 9.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 5.5.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = AccentBlue,
                                        radius = 4.dp.toPx(),
                                        center = pt
                                    )
                                } else {
                                    drawCircle(
                                        color = Color.White,
                                        radius = 4.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = if (hasActivity) NavyPrimary else Color(0xFF94A3B8),
                                        radius = 2.8.dp.toPx(),
                                        center = pt
                                    )
                                }
                            }
                        }

                        // Touch columns overlay for accurate point inspection (min >= 48.dp touch target)
                        Row(modifier = Modifier.fillMaxSize()) {
                            chartData.forEachIndexed { idx, recap ->
                                Box(
                                    modifier = Modifier
                                        .width(columnWidth)
                                        .fillMaxHeight()
                                        .clickable { inspectedIndex = idx }
                                        .testTag("chart_point_${recap.periodeBulan}")
                                )
                            }
                        }
                    }

                    // X-Axis Full Month & Year Labels ("Januari 2026", "Februari 2026", ...)
                    Row(modifier = Modifier.width(totalChartWidth)) {
                        chartData.forEachIndexed { idx, recap ->
                            val isSelected = idx == safeIndex
                            Box(
                                modifier = Modifier
                                    .width(columnWidth)
                                    .defaultMinSize(minHeight = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SkySoft else Color.Transparent)
                                    .clickable { inspectedIndex = idx }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = recap.labelBulan,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NavyPrimary else TextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Geser ke kiri/kanan & ketuk bulan untuk detail saldo bulanan",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

private fun formatCompactRupiah(amount: Long): String {
    val absVal = abs(amount)
    val sign = if (amount < 0L) "-" else ""
    return when {
        absVal >= 1_000_000_000L -> {
            val v = absVal / 1_000_000_000.0
            "${sign}Rp ${String.format(Locale("id", "ID"), "%.1fM", v)}"
        }
        absVal >= 1_000_000L -> {
            val v = absVal / 1_000_000.0
            val formatted = if (absVal % 1_000_000L == 0L) {
                "${absVal / 1_000_000L}jt"
            } else {
                String.format(Locale("id", "ID"), "%.1fjt", v)
            }
            "${sign}Rp $formatted"
        }
        absVal >= 1_000L -> "${sign}Rp ${absVal / 1_000L}rb"
        else -> "${sign}Rp $absVal"
    }
}
