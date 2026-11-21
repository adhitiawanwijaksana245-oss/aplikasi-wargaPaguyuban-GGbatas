package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.KasMonthlyRecap
import com.example.ui.theme.*
import com.example.ui.viewmodel.WargaViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.util.Locale
import kotlin.math.abs

@Composable
fun KasGroupedBarChartCard(
    rekapBulanan: List<KasMonthlyRecap>,
    selectedMonthKey: String,
    modifier: Modifier = Modifier
) {
    val chartData = remember(rekapBulanan) {
        rekapBulanan.ifEmpty { emptyList() }
    }

    val initialStartIndex = remember(chartData) {
        val jan2026Idx = chartData.indexOfFirst { it.periodeBulan == "2026-01" }
        if (jan2026Idx >= 0) jan2026Idx else 0
    }

    var selectedIndex by remember(chartData) {
        mutableIntStateOf(initialStartIndex)
    }

    var boundChartData by remember { mutableStateOf<List<KasMonthlyRecap>?>(null) }
    var boundViewportKey by remember { mutableStateOf<Pair<List<KasMonthlyRecap>, String>?>(null) }

    LaunchedEffect(selectedMonthKey, chartData) {
        if (selectedMonthKey != "Semua") {
            val match = chartData.indexOfFirst { it.periodeBulan == selectedMonthKey }
            if (match != -1) selectedIndex = match
        } else {
            selectedIndex = initialStartIndex
        }
    }

    val activeRecap = chartData.getOrNull(selectedIndex.coerceIn(0, (chartData.size - 1).coerceAtLeast(0)))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("kas_grouped_bar_chart_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SkySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = "Grafik Batang Rekap Bulanan",
                        tint = NavyPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Grafik Rekap Bulanan (Masuk vs Keluar)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Sinkron Google Sheets (Rekap_Bulanan)",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (activeRecap != null) {
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
                                text = activeRecap.labelBulan.ifBlank { activeRecap.periodeBulan },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Masuk ${WargaViewModel.formatRupiah(activeRecap.totalMasuk)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GreenIncome
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Keluar ${WargaViewModel.formatRupiah(activeRecap.totalKeluar)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RedExpense
                            )
                            Text(
                                text = "Selisih ${WargaViewModel.formatRupiah(activeRecap.totalMasuk - activeRecap.totalKeluar)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (chartData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada data Rekap_Bulanan untuk ditampilkan",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            } else {
                AndroidView(
                    factory = { context ->
                        BarChart(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            description.isEnabled = false
                            setDrawBarShadow(false)
                            setDrawValueAboveBar(true)
                            setPinchZoom(false)
                            setScaleEnabled(true)
                            isDragEnabled = true
                            setDrawGridBackground(false)
                            setExtraOffsets(6f, 10f, 6f, 10f)

                            legend.apply {
                                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                                orientation = Legend.LegendOrientation.HORIZONTAL
                                setDrawInside(false)
                                textSize = 11f
                                textColor = AndroidColor.parseColor("#1E293B")
                            }

                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                setDrawGridLines(false)
                                granularity = 1f
                                isGranularityEnabled = true
                                setCenterAxisLabels(true)
                                textSize = 10f
                                textColor = AndroidColor.parseColor("#475569")
                            }

                            axisLeft.apply {
                                axisMinimum = 0f
                                setDrawAxisLine(false)
                                enableGridDashedLine(10f, 8f, 0f)
                                gridColor = AndroidColor.parseColor("#E2E8F0")
                                textSize = 10f
                                textColor = AndroidColor.parseColor("#64748B")
                                valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        return formatBarCompactRupiah(value.toLong())
                                    }
                                }
                            }
                            axisRight.isEnabled = false
                        }
                    },
                    update = { barChart ->
                        val dataChanged = barChart.data == null || boundChartData != chartData
                        val viewportKey = chartData to selectedMonthKey
                        val viewportChanged = dataChanged || boundViewportKey != viewportKey

                        if (dataChanged) {
                            val masukEntries = chartData.mapIndexed { idx, item ->
                                BarEntry(idx.toFloat(), item.totalMasuk.toFloat().coerceAtLeast(0f))
                            }
                            val keluarEntries = chartData.mapIndexed { idx, item ->
                                BarEntry(idx.toFloat(), item.totalKeluar.toFloat().coerceAtLeast(0f))
                            }

                            val compactValueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    val v = value.toLong()
                                    return if (v <= 0L) "" else formatBarCompactRupiah(v)
                                }
                            }

                            val setMasuk = BarDataSet(masukEntries, "Total Masuk").apply {
                                color = AndroidColor.parseColor("#2563EB") // Google Sheets style Blue
                                valueTextColor = AndroidColor.parseColor("#1E3A8A")
                                valueTextSize = 9f
                                valueFormatter = compactValueFormatter
                            }

                            val setKeluar = BarDataSet(keluarEntries, "Total Keluar").apply {
                                color = AndroidColor.parseColor("#DC2626") // Google Sheets style Red
                                valueTextColor = AndroidColor.parseColor("#991B1B")
                                valueTextSize = 9f
                                valueFormatter = compactValueFormatter
                            }

                            val groupSpace = 0.24f
                            val barSpace = 0.04f
                            val barWidth = 0.34f

                            val barData = BarData(setMasuk, setKeluar).apply {
                                this.barWidth = barWidth
                            }

                            val labels = chartData.map { item ->
                                item.labelBulan.ifBlank { item.periodeBulan }
                            }

                            barChart.xAxis.apply {
                                valueFormatter = IndexAxisValueFormatter(labels)
                                axisMinimum = 0f
                                axisMaximum = barData.getGroupWidth(groupSpace, barSpace) * chartData.size
                                labelCount = chartData.size.coerceAtMost(6)
                            }

                            barChart.data = barData
                            barChart.groupBars(0f, groupSpace, barSpace)
                            boundChartData = chartData
                        }

                        if (viewportChanged) {
                            if (chartData.size > 4) {
                                barChart.setVisibleXRangeMaximum(4f)
                                val targetX = if (selectedMonthKey != "Semua") {
                                    val match = chartData.indexOfFirst { it.periodeBulan == selectedMonthKey }
                                    if (match != -1) {
                                        (match - 1).coerceIn(0, (chartData.size - 4).coerceAtLeast(0)).toFloat()
                                    } else {
                                        initialStartIndex.toFloat()
                                    }
                                } else {
                                    initialStartIndex.toFloat()
                                }
                                barChart.moveViewToX(targetX)
                            } else {
                                barChart.fitScreen()
                            }
                            boundViewportKey = viewportKey
                        }

                        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                            override fun onValueSelected(e: Entry?, h: Highlight?) {
                                val idx = e?.x?.toInt()?.coerceIn(0, chartData.lastIndex) ?: return
                                selectedIndex = idx
                            }

                            override fun onNothingSelected() {}
                        })

                        if (viewportChanged) {
                            barChart.invalidate()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(235.dp)
                )
            }
        }
    }
}

private fun formatBarCompactRupiah(amount: Long): String {
    val absVal = abs(amount)
    val sign = if (amount < 0L) "-" else ""
    return when {
        absVal >= 1_000_000_000L -> "${sign}Rp ${String.format(Locale("id", "ID"), "%.1fM", absVal / 1_000_000_000.0)}"
        absVal >= 1_000_000L -> {
            val v = absVal / 1_000_000.0
            val formatted = if (absVal % 1_000_000L == 0L) "${absVal / 1_000_000L}jt" else String.format(Locale("id", "ID"), "%.1fjt", v)
            "${sign}Rp $formatted"
        }
        absVal >= 1_000L -> "${sign}Rp ${absVal / 1_000L}rb"
        absVal == 0L -> "Rp 0"
        else -> "${sign}Rp $absVal"
    }
}
