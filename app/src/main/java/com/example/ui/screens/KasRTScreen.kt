package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.KasItem
import com.example.data.model.KasMonthlyRecap
import com.example.data.model.UserRole
import com.example.ui.components.KasGroupedBarChartCard
import com.example.ui.components.KasRolling12LineChartCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.WargaViewModel
import com.example.util.KasRecapHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KasRTScreen(
    kasList: List<KasItem>,
    rolling12: List<KasMonthlyRecap>,
    rekapBulanan: List<KasMonthlyRecap>,
    saldoKas: Long,
    totalMasuk: Long,
    totalKeluar: Long,
    userRole: UserRole,
    onAddKasClick: () -> Unit,
    onEditKasClick: (KasItem) -> Unit,
    onDeleteKasClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("Semua") } // "Semua", "Masuk", "Keluar"
    var selectedMonthKey by remember { mutableStateOf("Semua") } // "Semua" or "yyyy-MM"
    var chartMode by remember { mutableStateOf("bar") } // "bar" (MPAndroidChart Rekap_Bulanan) | "line" (rolling12)

    val baseTimelineSource = remember(rekapBulanan, kasList, rolling12) {
        when {
            rekapBulanan.isNotEmpty() -> rekapBulanan
            kasList.isNotEmpty() -> KasRecapHelper.buildFullMonthlyRecap(kasList)
            else -> rolling12.filter { it.totalMasuk > 0L || it.totalKeluar > 0L }
        }
    }

    val effectiveBarRecap = remember(baseTimelineSource) {
        if (baseTimelineSource.isNotEmpty()) {
            KasRecapHelper.buildTimelineRecap(
                sourceRecap = baseTimelineSource,
                startKey = "2026-01",
                minEndKey = "2030-12"
            )
        } else {
            emptyList()
        }
    }

    val effectiveLineRecap = remember(baseTimelineSource) {
        KasRecapHelper.buildTimelineRecap(
            sourceRecap = baseTimelineSource,
            startKey = "2026-01",
            minEndKey = "2030-12"
        )
    }

    // Extract all distinct months from both transaction history and rekapBulanan (sorted descending)
    val availableMonthKeys = remember(kasList, rekapBulanan) {
        val fromKas = kasList.mapNotNull { item ->
            KasRecapHelper.resolvePeriodeKey(item.periodeBulan, item.tanggal).takeIf { it.isNotBlank() }
        }
        val fromRekap = rekapBulanan.mapNotNull { recap ->
            KasRecapHelper.parseMonthKey(recap.periodeBulan.ifBlank { recap.labelBulan }).takeIf { it.isNotBlank() }
        }
        val keys = (fromKas + fromRekap).distinct().sortedDescending()
        listOf("Semua") + keys
    }

    // Filter by Month using periodeBulan or tanggal
    val monthFilteredList = remember(kasList, selectedMonthKey) {
        if (selectedMonthKey == "Semua") {
            kasList
        } else {
            kasList.filter { item ->
                KasRecapHelper.resolvePeriodeKey(item.periodeBulan, item.tanggal) == selectedMonthKey
            }
        }
    }

    val selectedMonthRecap = remember(rekapBulanan, selectedMonthKey) {
        if (selectedMonthKey == "Semua") null
        else rekapBulanan.firstOrNull { it.periodeBulan == selectedMonthKey }
    }

    // Monthly totals for report (Semua Periode stays identical to Beranda totals)
    val displayMasuk = remember(monthFilteredList, selectedMonthRecap, selectedMonthKey, totalMasuk) {
        when {
            selectedMonthKey == "Semua" -> totalMasuk
            monthFilteredList.isNotEmpty() -> monthFilteredList.sumOf { it.masuk }
            else -> selectedMonthRecap?.totalMasuk ?: 0L
        }
    }
    val displayKeluar = remember(monthFilteredList, selectedMonthRecap, selectedMonthKey, totalKeluar) {
        when {
            selectedMonthKey == "Semua" -> totalKeluar
            monthFilteredList.isNotEmpty() -> monthFilteredList.sumOf { it.keluar }
            else -> selectedMonthRecap?.totalKeluar ?: 0L
        }
    }
    val displaySaldo = remember(displayMasuk, displayKeluar, selectedMonthKey, saldoKas) {
        if (selectedMonthKey == "Semua") saldoKas else (displayMasuk - displayKeluar)
    }

    val finalFilteredList = remember(monthFilteredList, searchQuery, filterType) {
        monthFilteredList.filter { item ->
            val matchQuery = searchQuery.isBlank() ||
                item.keterangan.contains(searchQuery, ignoreCase = true) ||
                item.kategori.contains(searchQuery, ignoreCase = true) ||
                item.periodeBulan.contains(searchQuery, ignoreCase = true) ||
                item.tanggal.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (filterType) {
                "Masuk" -> item.masuk > 0L
                "Keluar" -> item.keluar > 0L
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    Box(modifier = modifier.fillMaxSize().background(SlateBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Title
            item {
                Column {
                    Text(
                        text = "Buku Kas & Keuangan RT 005",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDone,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tersinkron ke Cloud Firestore & Room Database",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // AREA ATAS (Lingkaran Merah): Grafik Rekap Bulanan (MPAndroidChart Grouped Bar & Rolling12 Line)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = chartMode == "bar",
                            onClick = { chartMode = "bar" },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.BarChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Grafik Batang (Masuk/Keluar)", fontSize = 11.sp) },
                            modifier = Modifier.testTag("chart_mode_bar_chip")
                        )
                        FilterChip(
                            selected = chartMode == "line",
                            onClick = { chartMode = "line" },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ShowChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Tren Saldo Kas", fontSize = 11.sp) },
                            modifier = Modifier.testTag("chart_mode_line_chip")
                        )
                    }

                    if (chartMode == "bar") {
                        KasGroupedBarChartCard(
                            rekapBulanan = effectiveBarRecap,
                            selectedMonthKey = selectedMonthKey
                        )
                    } else {
                        KasRolling12LineChartCard(
                            rolling12 = effectiveLineRecap,
                            selectedMonthKey = selectedMonthKey
                        )
                    }
                }
            }

            // AREA DI BAWAH GRAFIK: 3 Kartu Ringkasan Nominal Horizontal (Total Masuk, Total Keluar, Saldo Akhir)
            item {
                val periodCaption = if (selectedMonthKey == "Semua") {
                    "Semua Periode"
                } else {
                    KasRecapHelper.formatMonthYearLabel(selectedMonthKey)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kas_summary_cards_row"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Total Masuk
                    KasHorizontalSummaryCard(
                        title = "Total Masuk",
                        subtitle = periodCaption,
                        amount = displayMasuk,
                        icon = Icons.Filled.ArrowDownward,
                        containerColor = GreenIncomeBg.copy(alpha = 0.65f),
                        iconContainerColor = Color(0xFFDCFCE7),
                        accentColor = GreenIncome,
                        titleColor = Color(0xFF166534),
                        modifier = Modifier.testTag("kas_card_masuk")
                    )

                    // 2. Total Keluar
                    KasHorizontalSummaryCard(
                        title = "Total Keluar",
                        subtitle = periodCaption,
                        amount = displayKeluar,
                        icon = Icons.Filled.ArrowUpward,
                        containerColor = RedExpenseBg.copy(alpha = 0.65f),
                        iconContainerColor = Color(0xFFFEE2E2),
                        accentColor = RedExpense,
                        titleColor = Color(0xFF991B1B),
                        modifier = Modifier.testTag("kas_card_keluar")
                    )

                    // 3. Saldo Akhir
                    KasHorizontalSummaryCard(
                        title = "Saldo Akhir",
                        subtitle = periodCaption,
                        amount = displaySaldo,
                        icon = Icons.Filled.AccountBalanceWallet,
                        containerColor = SurfaceCard,
                        iconContainerColor = SkySoft,
                        accentColor = if (displaySaldo >= 0L) NavyPrimary else RedExpense,
                        titleColor = NavyPrimary,
                        modifier = Modifier.testTag("kas_card_saldo")
                    )
                }
            }

            // AREA BAWAH KARTU RINGKASAN: Pencarian & Filter Laporan Bulanan
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari transaksi, kategori, atau tanggal...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("kas_search_input")
                    )

                    // Monthly Report Selection Chips
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Laporan Per Bulan:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableMonthKeys) { monthKey ->
                                val label = if (monthKey == "Semua") {
                                    "Semua Periode"
                                } else {
                                    KasRecapHelper.formatMonthYearLabel(monthKey)
                                }
                                FilterChip(
                                    selected = selectedMonthKey == monthKey,
                                    onClick = { selectedMonthKey = monthKey },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selectedMonthKey == monthKey) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NavyPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Summary banner for monthly report
                        if (selectedMonthKey != "Semua") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Arus Kas ${KasRecapHelper.formatMonthYearLabel(selectedMonthKey)}:",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${if (displaySaldo >= 0) "+ " else ""}${WargaViewModel.formatRupiah(displaySaldo)}",
                                        fontSize = 12.sp,
                                        color = if (displaySaldo >= 0) Color(0xFF166534) else RedExpense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Type filter chips: Semua, Masuk, Keluar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Semua", "Masuk", "Keluar").forEach { filter ->
                            FilterChip(
                                selected = filterType == filter,
                                onClick = { filterType = filter },
                                label = { Text(filter, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Table / List Subheader
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lembar Kerja Excel Kas RT",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (userRole == UserRole.WARGA) SkySoft else Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = if (userRole == UserRole.WARGA) "Mode Baca (Warga)" else "Mode Kelola (Pengurus)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (userRole == UserRole.WARGA) NavyPrimary else Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Transaction Items
            if (finalFilteredList.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp).fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada data transaksi kas", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(finalFilteredList, key = { it.id }) { item ->
                    KasItemCard(
                        item = item,
                        userRole = userRole,
                        onEdit = { onEditKasClick(item) },
                        onDelete = { onDeleteKasClick(item.id) }
                    )
                }
            }

            // Bottom space for FAB
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        // Floating Action Button to Add Transaction
        FloatingActionButton(
            onClick = onAddKasClick,
            containerColor = NavyPrimary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_kas_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah Transaksi Kas RT")
        }
    }
}

@Composable
fun KasItemCard(
    item: KasItem,
    userRole: UserRole,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isMasuk = item.masuk > 0L
    val amount = if (isMasuk) item.masuk else item.keluar

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("kas_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Type Pill, Category Pill (if available), and Formatted Clean Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isMasuk) GreenIncomeBg else RedExpenseBg
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isMasuk) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint = if (isMasuk) GreenIncome else RedExpense,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isMasuk) "Masuk" else "Keluar",
                                color = if (isMasuk) Color(0xFF166534) else Color(0xFF991B1B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (item.kategori.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SkySoft
                        ) {
                            Text(
                                text = item.kategori,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NavyPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatKasDate(item.tanggal),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.buktiFoto.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEFF6FF)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Ada Bukti", fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Amount HORIZONTAL directly under the Date
            Text(
                text = (if (isMasuk) "+ " else "- ") + WargaViewModel.formatRupiah(amount),
                color = if (isMasuk) GreenIncome else RedExpense,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Description
            Text(
                text = item.keterangan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )

            // Optional Bukti Foto preview if available
            if (item.buktiFoto.isNotBlank()) {
                val displayModel = remember(item.buktiFoto) {
                    com.example.util.PhotoStorageHelper.getDisplayModel(item.buktiFoto)
                }
                if (displayModel != "") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        SubcomposeAsyncImage(
                            model = displayModel,
                            contentDescription = "Bukti Transaksi",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = NavyPrimary)
                                }
                            },
                            error = {
                                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                    Text("Gagal memuat foto bukti", color = Color(0xFF64748B), fontSize = 11.sp)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 4: Footer: Saldo akumulasi & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saldo Akumulasi: ${WargaViewModel.formatRupiah(item.effectiveSaldo)}",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                if (userRole != UserRole.WARGA) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = AccentBlue, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = RedExpense, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KasHorizontalSummaryCard(
    title: String,
    subtitle: String,
    amount: Long,
    icon: ImageVector,
    containerColor: Color,
    iconContainerColor: Color,
    accentColor: Color,
    titleColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = WargaViewModel.formatRupiah(amount),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun formatKasDate(dateStr: String): String {
    return try {
        if (dateStr.contains("T")) {
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = iso.parse(dateStr.substringBefore("."))
            val out = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            out.format(date ?: Date())
        } else if (dateStr.length >= 10 && dateStr.contains("-")) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr.substring(0, 10))
            val out = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            out.format(date ?: Date())
        } else {
            dateStr
        }
    } catch (e: Exception) {
        dateStr
    }
}
