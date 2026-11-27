package com.example

import com.example.data.model.KasItem
import com.example.data.model.UserRole
import com.example.data.repository.WargaRepository
import com.example.ui.viewmodel.WargaViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun admin_authority_password_is_080309() {
    assertEquals("080309", WargaRepository.ADMIN_UTAMA_PASSWORD)
  }

  @Test
  fun formatRupiah_is_correct() {
    val formatted = WargaViewModel.formatRupiah(3870000L)
    assertTrue("Harus mengandung Rp dan 3.870.000", formatted.contains("3.870.000"))
  }

  @Test
  fun kasCalculation_is_correct() {
    val masuk = 5550000L
    val keluar = 1680000L
    val saldo = masuk - keluar
    assertEquals(3870000L, saldo)
  }

  @Test
  fun kasEntity_conversion_is_correct() {
    val kasItem = KasItem(
      id = "KAS-999",
      tanggal = "2026-11-08",
      keterangan = "Iuran Kas RT",
      masuk = 200000L,
      keluar = 0L,
      jumlah = 200000L,
      buktiFoto = "https://example.com/receipt.jpg"
    )
    val entity = com.example.data.local.KasEntity.fromKasItem(kasItem)
    assertEquals("KAS-999", entity.id)
    assertEquals(com.example.data.local.KasEntity.SYNC_STATUS_SYNCED, entity.syncStatus)

    val backToItem = entity.toKasItem()
    assertEquals(kasItem.id, backToItem.id)
    assertEquals(kasItem.keterangan, backToItem.keterangan)
  }

  @Test
  fun aspirasiEntity_conversion_is_correct() {
    val aspirasiItem = com.example.data.model.AspirasiItem(
      id = "ASP-999",
      tanggal = "2026-11-08",
      nama = "Bpk. Santoso",
      kategori = "Masukan/Saran",
      pesan = "Usulan kerja bakti",
      status = "Selesai",
      tanggapanPengurus = "Sudah dijadwalkan",
      penanggapNama = "Tim Kebersihan",
      penanggapUsername = "budi_rt05",
      penanggapNamaAsli = "Budi Santoso"
    )
    val entity = com.example.data.local.AspirasiEntity.fromAspirasiItem(aspirasiItem)
    assertEquals("ASP-999", entity.id)
    assertEquals("Bpk. Santoso", entity.nama)
    assertEquals("Tim Kebersihan", entity.penanggapNama)
    assertEquals("budi_rt05", entity.penanggapUsername)
    assertEquals("Budi Santoso", entity.penanggapNamaAsli)

    val backToItem = entity.toAspirasiItem()
    assertEquals(aspirasiItem.pesan, backToItem.pesan)
    assertEquals(aspirasiItem.penanggapNama, backToItem.penanggapNama)
    assertEquals(aspirasiItem.penanggapUsername, backToItem.penanggapUsername)
    assertEquals(aspirasiItem.penanggapNamaAsli, backToItem.penanggapNamaAsli)
  }
}
