package com.example.data.source

object GoogleAppsScriptTemplate {
    const val TARGET_SPREADSHEET_ID = "1N_kod18u3HS86R1UAVfiqE1_hAUYiDLRz5eT02rIuws"
    const val TARGET_SPREADSHEET_URL = "https://docs.google.com/spreadsheets/d/1N_kod18u3HS86R1UAVfiqE1_hAUYiDLRz5eT02rIuws/edit?gid=115653630#gid=115653630"
    
    // Tautan default Apps Script Web App agar seluruh HP warga langsung tersambung otomatis saat instalasi
    const val EMBEDDED_WEB_APP_URL = "https://script.google.com/macros/s/AKfycby_Xq0Zvjtn_Xd9y46YOjZ00r-OOffkGxLsewgfarHUixUKpgAmPlYAoUWguvvRtYSsdA/exec"

    val SCRIPT_CODE = """
// === MULAI KODE APPS SCRIPT v9 (SALIN DARI BARIS INI SAMPAI AKHIR) ===
/**
 * GOOGLE APPS SCRIPT - APLIKASI WARGA RT 005 / RW 011 MULYAHARJA
 * 
 * SPREADSHEET TARGET:
 * ID: 1N_kod18u3HS86R1UAVfiqE1_hAUYiDLRz5eT02rIuws
 * URL: https://docs.google.com/spreadsheets/d/1N_kod18u3HS86R1UAVfiqE1_hAUYiDLRz5eT02rIuws/edit
 * 
 * PANDUAN CEPAT:
 * 1. Buka spreadsheet target Anda di browser:
 *    https://docs.google.com/spreadsheets/d/1N_kod18u3HS86R1UAVfiqE1_hAUYiDLRz5eT02rIuws/edit
 * 2. Klik menu: Ekstensi (Extensions) -> Apps Script.
 * 3. Hapus semua kode lama di editor, lalu tempel (Paste) seluruh kode script ini dan klik Simpan (Ctrl+S).
 * 4. WAJIB SEKALI SAJA (untuk izin Google Drive & migrasi kolom):
 *    Di toolbar atas editor Apps Script, pilih fungsi 'otorisasiDanCekSetup' lalu klik tombol 'Run' (Jalankan) -> Review Permissions -> Pilih Akun Google Anda -> Advanced -> Go to ... -> Allow.
 * 5. Jika UPDATE script dari versi sebelumnya (agar URL /exec tetap sama untuk semua HP warga):
 *    Klik Deploy -> Manage deployments -> ikon Pensil (Edit) -> Version: pilih 'New version' -> klik Deploy.
 * 6. Jika BARU PERTAMA KALI deploy:
 *    Klik Deploy -> New deployment -> Select type: 'Web app' -> Execute as: 'Me' -> Who has access: 'Anyone' -> Deploy -> salin URL '/exec' ke aplikasi -> Simpan & Hubungkan!
 */

var TARGET_SPREADSHEET_ID = "1N_kod18u3HS86R1UAVfiqE1_hAUYiDLRz5eT02rIuws";
var AUTH_PASSWORD = "005011";
var MASTER_CHANGE_KEY = "Amm080309";

function getConfiguredAuthPassword() {
  try {
    var saved = PropertiesService.getScriptProperties().getProperty("AUTH_PASSWORD");
    if (saved && saved.trim() !== "") return saved.trim();
  } catch (e) {}
  return AUTH_PASSWORD;
}

function getSpreadsheet() {
  if (TARGET_SPREADSHEET_ID && TARGET_SPREADSHEET_ID.trim() !== "") {
    try {
      return SpreadsheetApp.openById(TARGET_SPREADSHEET_ID.trim());
    } catch(err) {
      Logger.log("openById error: " + err);
    }
  }
  return SpreadsheetApp.getActiveSpreadsheet();
}

function checkDrivePermission() {
  try {
    DriveApp.getFoldersByName("Foto_Aplikasi_Warga_RT005").hasNext();
    return { ok: true, error: "" };
  } catch (err) {
    return { ok: false, error: err.toString() };
  }
}

// Jalankan fungsi ini sekali di editor Apps Script agar izin Spreadsheet & Google Drive langsung aktif
function otorisasiDanCekSetup() {
  var ss = getSpreadsheet();
  if (!ss) throw new Error("Spreadsheet tidak ditemukan.");
  ensureAllSheets(ss);
  var folderName = "Foto_Aplikasi_Warga_RT005";
  var folders = DriveApp.getFoldersByName(folderName);
  var folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(folderName);
  try {
    folder.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  } catch (e) {}
  SpreadsheetApp.flush();
  Logger.log("Setup & Otorisasi Berhasil! Spreadsheet: " + ss.getName() + " | Folder Drive: " + folder.getName());
  return "OK: " + ss.getName();
}

// Handler GET: Membaca data dan memproses aksi (Aman dari pemotongan payload HTTP 302 redirect)
function doGet(e) {
  try {
    var ss = getSpreadsheet();
    if (!ss) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error",
        message: "Spreadsheet dengan ID " + TARGET_SPREADSHEET_ID + " tidak ditemukan atau tidak memiliki izin akses."
      })).setMimeType(ContentService.MimeType.JSON);
    }

    ensureAllSheets(ss);

    // Jika ada parameter action, proses aksi tersebut
    if (e && e.parameter && e.parameter.action) {
      return handleAction(ss, e.parameter);
    }

    // Jika diminta khusus ?sheet=Rekap_Bulanan, kembalikan JSON rekap bulanan secara langsung
    if (e && e.parameter && e.parameter.sheet === "Rekap_Bulanan") {
      var kasOnlyRows = getSheetData(ss.getSheetByName("Kas_RT"));
      var sheetRecapOnly = readRekapBulananSheet(ss);
      var recapOut = sheetRecapOnly.length > 0 ? sheetRecapOnly : buildKasRecapPayload(kasOnlyRows).rekapBulanan;
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        sheet: "Rekap_Bulanan",
        rekapBulanan: recapOut,
        updatedAt: new Date().toISOString()
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // Default: kembalikan seluruh data dari 5 sheet + Rekap_Bulanan & rolling12 Kas RT
    var kasRows = getSheetData(ss.getSheetByName("Kas_RT"));
    var kasRekap = buildKasRecapPayload(kasRows);
    var rekapSheetRows = readRekapBulananSheet(ss);
    var finalRekapBulanan = rekapSheetRows.length > 0 ? rekapSheetRows : kasRekap.rekapBulanan;
    var driveStatus = checkDrivePermission();
    var result = {
      status: "success",
      version: 9,
      maxGaleriPhotos: 10,
      maxAspirasiPhotos: 5,
      maxUndanganPhotos: 1,
      supportsImageFormula: true,
      supportsDriveUpload: driveStatus.ok,
      driveAuthorized: driveStatus.ok,
      supportsRolling12: true,
      supportsRekapBulananSheet: true,
      supportsAspirasiMeta: true,
      spreadsheetId: ss.getId(),
      spreadsheetName: ss.getName(),
      kas: kasRows,
      rolling12: kasRekap.rolling12,
      rekapBulanan: finalRekapBulanan,
      kasSummary: kasRekap.summary,
      pengurus: getSheetData(ss.getSheetByName("Pengurus")),
      undangan: getSheetData(ss.getSheetByName("Undangan")),
      galeri: getSheetData(ss.getSheetByName("Galeri")),
      aspirasi: getSheetData(ss.getSheetByName("Aspirasi")),
      updatedAt: new Date().toISOString()
    };
    return ContentService.createTextOutput(JSON.stringify(result))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

// Handler POST: Menerima kiriman data dari aplikasi
function doPost(e) {
  try {
    var ss = getSpreadsheet();
    if (!ss) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error",
        message: "Spreadsheet dengan ID " + TARGET_SPREADSHEET_ID + " tidak dapat diakses."
      })).setMimeType(ContentService.MimeType.JSON);
    }

    ensureAllSheets(ss);

    var body = {};
    var rawContents = (e && e.postData && e.postData.contents) ? e.postData.contents.trim() : "";
    if (rawContents && rawContents.indexOf("{") === 0) {
      try {
        body = JSON.parse(rawContents);
      } catch (ex) {
        body = (e && e.parameter) ? e.parameter : {};
      }
    } else if (e && e.parameter) {
      body = e.parameter;
    }

    return handleAction(ss, body);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function handleAction(ss, params) {
  var action = params.action;
  var sheetName = params.sheet || "";
  var password = params.password || "";

  var data = params.data;
  if (typeof data === "string") {
    try {
      data = JSON.parse(data);
    } catch (e) {
      data = params;
    }
  } else if (!data) {
    data = params;
  }

  if (action === "test_connection") {
    var driveStatus = checkDrivePermission();
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      version: 9,
      maxGaleriPhotos: 10,
      maxAspirasiPhotos: 5,
      maxUndanganPhotos: 1,
      driveAuthorized: driveStatus.ok,
      driveError: driveStatus.error,
      supportsDriveUpload: driveStatus.ok,
      supportsImageFormula: true,
      supportsAspirasiMeta: true,
      spreadsheetId: ss.getId(),
      spreadsheetName: ss.getName(),
      message: driveStatus.ok
        ? ("Koneksi Berhasil! Terhubung ke spreadsheet: " + ss.getName())
        : ("Terhubung ke " + ss.getName() + ", tetapi izin Google Drive belum diaktifkan: " + driveStatus.error)
    })).setMimeType(ContentService.MimeType.JSON);
  }

  if (action === "update_auth_password") {
    var masterKey = data.masterKey || params.masterKey || "";
    var newPass = (data.newPassword || params.newPassword || "").toString().trim();
    var activePass = getConfiguredAuthPassword();
    if ((masterKey === MASTER_CHANGE_KEY || password === activePass || password === "005011") && newPass) {
      PropertiesService.getScriptProperties().setProperty("AUTH_PASSWORD", newPass);
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        message: "Password otoritas Apps Script diperbarui."
      })).setMimeType(ContentService.MimeType.JSON);
    }
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: "Otorisasi gagal untuk mengubah password Apps Script."
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // Khusus aspirasi dari warga: warga tidak perlu memasukkan password pengurus
  var isAspirasiAdd = (sheetName === "Aspirasi" && action === "add");
  var activeAuthPass = getConfiguredAuthPassword();

  if (!isAspirasiAdd) {
    if (password !== activeAuthPass && password !== AUTH_PASSWORD && password !== "005011") {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error",
        message: "Password otoritas salah! Akses ditolak."
      })).setMimeType(ContentService.MimeType.JSON);
    }
  }

  if (action === "backup_all") {
    backupAllData(ss, data);
    SpreadsheetApp.flush();
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      action: "backup_all",
      message: "Seluruh data Kas RT, Pengurus, Undangan, Galeri, dan Aspirasi berhasil dibackup ke spreadsheet!",
      timestamp: new Date().toISOString()
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // Mengunggah foto ke Google Drive dan mengembalikan URL publik langsung
  if (action === "upload_photo") {
    try {
      var base64 = data.imageBase64 || params.imageBase64 || "";
      var filename = data.filename || params.filename || ("foto_" + new Date().getTime() + ".jpg");
      if (!base64) {
        return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Data foto kosong" })).setMimeType(ContentService.MimeType.JSON);
      }
      var clean = base64.toString().trim();
      if (clean.indexOf("data:") === 0 && clean.indexOf(",") !== -1) {
        clean = clean.split(",")[1];
      }
      clean = clean.replace(/\s+/g, "");
      var bytes = Utilities.base64Decode(clean);
      var blob = Utilities.newBlob(bytes, "image/jpeg", filename);
      var folderName = "Foto_Aplikasi_Warga_RT005";
      var folders = DriveApp.getFoldersByName(folderName);
      var folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(folderName);
      try {
        folder.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
      } catch (folderShareErr) {}
      var file = folder.createFile(blob);
      try {
        file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
      } catch (fileShareErr) {}
      var fileId = file.getId();
      var publicUrl = "https://drive.google.com/uc?export=view&id=" + fileId;
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        fileId: fileId,
        url: publicUrl,
        formula: '=IMAGE("' + publicUrl + '")',
        message: "Foto berhasil diunggah ke Google Drive"
      })).setMimeType(ContentService.MimeType.JSON);
    } catch(err) {
      return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Drive error: " + err.toString() })).setMimeType(ContentService.MimeType.JSON);
    }
  }

  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ensureSheet(ss, sheetName);
  }

  if (action === "add") {
    var rowData = (sheetName === "Kas_RT") ? convertKasObjToRow(sheet, data) : convertObjToRow(sheetName, data);
    sheet.appendRow(rowData);
    formatPhotoRow(sheet, sheetName, sheet.getLastRow());
    SpreadsheetApp.flush();
  } else if (action === "edit") {
    updateRow(sheet, sheetName, data, params.targetId);
    SpreadsheetApp.flush();
  } else if (action === "delete") {
    deleteRow(sheet, params.targetId || data.id);
    SpreadsheetApp.flush();
  } else if (action === "respond_aspirasi") {
    var respResult = respondAspirasi(sheet, data, params.targetId);
    if (!respResult || !respResult.ok) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "error",
        action: action,
        sheet: sheetName,
        message: (respResult && respResult.error) ? respResult.error : "Data aspirasi tidak ditemukan di Spreadsheet."
      })).setMimeType(ContentService.MimeType.JSON);
    }
    SpreadsheetApp.flush();
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      action: action,
      sheet: sheetName,
      aspirasiMetaSaved: true,
      mode: respResult.mode,
      rowNum: respResult.rowNum,
      message: "Tanggapan pengurus berhasil dicatat ke spreadsheet tab " + sheetName,
      timestamp: new Date().toISOString()
    })).setMimeType(ContentService.MimeType.JSON);
  }

  return ContentService.createTextOutput(JSON.stringify({
    status: "success",
    action: action,
    sheet: sheetName,
    message: "Data berhasil dicatat ke spreadsheet tab " + sheetName,
    timestamp: new Date().toISOString()
  })).setMimeType(ContentService.MimeType.JSON);
}

// Memastikan 5 sheet dan header kolom otomatis dibuat sesuai format
function ensureAllSheets(ss) {
  ensureSheet(ss, "Kas_RT");
  ensureSheet(ss, "Pengurus");
  ensureSheet(ss, "Undangan");
  ensureSheet(ss, "Galeri");
  ensureSheet(ss, "Aspirasi");
}

var GALERI_HEADERS = [
  "ID", "Tanggal", "Judul", "Kategori", "Keterangan",
  "Foto_1", "Foto_2", "Foto_3", "Foto_4", "Foto_5",
  "Foto_6", "Foto_7", "Foto_8", "Foto_9", "Foto_10",
  "Petugas"
];

var ASPIRASI_HEADERS = [
  "ID", "Tanggal", "Nama", "Kategori", "Pesan",
  "Foto_Masalah_1", "Foto_Masalah_2", "Foto_Masalah_3", "Foto_Masalah_4", "Foto_Masalah_5",
  "Status", "Nama_Pengurus", "Tanggapan_pengurus",
  "Foto_pengerjaan_1", "Foto_pengerjaan_2", "Foto_pengerjaan_3", "Foto_pengerjaan_4", "Foto_pengerjaan_5"
];

function ensureSheet(ss, name) {
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
  }
  if (sheet.getLastRow() === 0) {
    var headers = [];
    if (name === "Kas_RT") headers = ["id", "tanggal", "periode_bulan", "kategori", "keterangan", "masuk", "keluar", "saldo", "bukti_foto"];
    else if (name === "Pengurus") headers = ["ID", "Jabatan", "Nama", "No HP", "Alamat", "Masa_Bakti", "Foto"];
    else if (name === "Undangan") headers = ["ID", "Judul", "Tanggal", "Waktu", "Lokasi", "Keterangan", "Tipe", "Pengingat_Menit", "Pengingat_Aktif", "Pengingat_Target_Millis", "Pengingat_Aktif_Pada", "Foto"];
    else if (name === "Galeri") headers = GALERI_HEADERS;
    else if (name === "Aspirasi") headers = ASPIRASI_HEADERS;
    sheet.appendRow(headers);
    sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold").setBackground("#D9E1F2");
    if (name === "Undangan") {
      sheet.setColumnWidth(12, 150);
    } else if (name === "Galeri") {
      sheet.setColumnWidths(6, 10, 150);
    } else if (name === "Aspirasi") {
      sheet.setColumnWidths(6, 5, 150);
      sheet.setColumnWidths(14, 5, 150);
    }
  } else if (name === "Galeri") {
    migrateGaleriSheetLayout(sheet);
  } else if (name === "Aspirasi") {
    migrateAspirasiSheetLayout(sheet);
  } else if (name === "Undangan" && sheet.getLastColumn() < 12) {
    var undHeaders = ["Tipe", "Pengingat_Menit", "Pengingat_Aktif", "Pengingat_Target_Millis", "Pengingat_Aktif_Pada", "Foto"];
    var uStart = Math.max(sheet.getLastColumn() + 1, 7);
    for (var u = uStart; u <= 12; u++) {
      var uIdx = u - 7;
      if (uIdx >= 0 && uIdx < undHeaders.length) {
        sheet.getRange(1, u).setValue(undHeaders[uIdx]).setFontWeight("bold").setBackground("#D9E1F2");
      }
    }
    sheet.setColumnWidth(12, 150);
  }
  return sheet;
}

function migrateGaleriSheetLayout(sheet) {
  if (!sheet || sheet.getLastRow() === 0) return;
  var lastCol = sheet.getLastColumn();
  var headerRow = sheet.getRange(1, 1, 1, Math.max(lastCol, 1)).getValues()[0];
  var isAlreadySynced = lastCol >= 16 &&
    headerRow[5] === "Foto_1" &&
    headerRow[14] === "Foto_10" &&
    headerRow[15] === "Petugas";
  if (isAlreadySynced) return;

  var range = sheet.getDataRange();
  var values = range.getValues();
  var formulas = range.getFormulas();
  var oldHeaders = values[0];
  var migratedRows = [];

  for (var i = 1; i < values.length; i++) {
    var row = values[i];
    var formulaRow = (formulas && formulas[i]) ? formulas[i] : null;
    var obj = {};
    for (var j = 0; j < oldHeaders.length; j++) {
      var rawKey = oldHeaders[j] ? oldHeaders[j].toString().trim() : "";
      if (!rawKey) continue;
      var fVal = (formulaRow && formulaRow[j]) ? formulaRow[j].toString() : "";
      var rVal = (row[j] !== undefined && row[j] !== null) ? row[j] : "";
      obj[rawKey.toLowerCase()] = (fVal.indexOf("=") === 0) ? fVal : rVal;
    }

    var f1 = obj["foto_1"] || obj["foto1"] || obj["foto"] || "";
    migratedRows.push([
      obj["id"] || "",
      obj["tanggal"] || "",
      obj["judul"] || "",
      obj["kategori"] || "",
      obj["keterangan"] || "",
      toImageFormula(f1),
      toImageFormula(obj["foto_2"] || obj["foto2"] || ""),
      toImageFormula(obj["foto_3"] || obj["foto3"] || ""),
      toImageFormula(obj["foto_4"] || obj["foto4"] || ""),
      toImageFormula(obj["foto_5"] || obj["foto5"] || ""),
      toImageFormula(obj["foto_6"] || obj["foto6"] || ""),
      toImageFormula(obj["foto_7"] || obj["foto7"] || ""),
      toImageFormula(obj["foto_8"] || obj["foto8"] || ""),
      toImageFormula(obj["foto_9"] || obj["foto9"] || ""),
      toImageFormula(obj["foto_10"] || obj["foto10"] || ""),
      obj["petugas"] || ""
    ]);
  }

  var clearCols = Math.max(oldHeaders.length, 16);
  if (values.length > 1) {
    sheet.getRange(2, 1, values.length - 1, clearCols).clearContent();
  }
  sheet.getRange(1, 1, 1, 16).setValues([GALERI_HEADERS]).setFontWeight("bold").setBackground("#D9E1F2");
  if (migratedRows.length > 0) {
    sheet.getRange(2, 1, migratedRows.length, 16).setValues(migratedRows);
  }
  sheet.setColumnWidths(6, 10, 150);
  for (var r = 0; r < migratedRows.length; r++) {
    var hasPhoto = false;
    for (var c = 5; c <= 14; c++) {
      if (migratedRows[r][c]) {
        hasPhoto = true;
        break;
      }
    }
    if (hasPhoto) {
      sheet.setRowHeight(r + 2, 100);
    }
  }
}

function extractAspirasiMetaName(raw) {
  if (!raw) return "";
  var m = raw.toString().match(/\[\[META_BALASAN:([^\]]+)\]\]/);
  if (!m || !m[1]) return "";
  var parts = m[1].split("&");
  for (var i = 0; i < parts.length; i++) {
    var eq = parts[i].indexOf("=");
    if (eq > 0 && parts[i].substring(0, eq) === "n") {
      try {
        return decodeURIComponent(parts[i].substring(eq + 1).replace(/\+/g, " "));
      } catch (e) {
        return parts[i].substring(eq + 1);
      }
    }
  }
  return "";
}

function normalizeAspirasiPhotoSlots(s1, s2, s3, s4, s5) {
  var rawSlots = [s1 || "", s2 || "", s3 || "", s4 || "", s5 || ""];
  var out = [];
  for (var i = 0; i < rawSlots.length; i++) {
    var cell = rawSlots[i] ? rawSlots[i].toString().trim() : "";
    if (!cell) continue;
    if (cell.toUpperCase().indexOf("=IMAGE(") === 0) {
      if (out.indexOf(cell) === -1) out.push(cell);
    } else if (cell.indexOf(",") !== -1 && (cell.indexOf("http://") !== -1 || cell.indexOf("https://") !== -1)) {
      var parts = cell.split(",");
      for (var p = 0; p < parts.length; p++) {
        var formula = toImageFormula(parts[p]);
        if (formula && out.indexOf(formula) === -1) out.push(formula);
      }
    } else {
      var singleFormula = toImageFormula(cell);
      if (singleFormula && out.indexOf(singleFormula) === -1) out.push(singleFormula);
    }
    if (out.length >= 5) break;
  }
  while (out.length < 5) out.push("");
  return out.slice(0, 5);
}

function isImageOrUrlValue(val) {
  if (!val) return false;
  var s = val.toString().trim();
  return s.toUpperCase().indexOf("=IMAGE(") === 0 || s.indexOf("http://") === 0 || s.indexOf("https://") === 0;
}

function migrateAspirasiSheetLayout(sheet) {
  if (!sheet || sheet.getLastRow() === 0) return;
  var lastCol = sheet.getLastColumn();
  var headerRow = sheet.getRange(1, 1, 1, Math.max(lastCol, 1)).getValues()[0];
  var isAlreadySynced = lastCol === 18;
  if (isAlreadySynced) {
    for (var h = 0; h < 18; h++) {
      var cellHeader = headerRow[h] ? headerRow[h].toString().trim() : "";
      if (cellHeader !== ASPIRASI_HEADERS[h]) {
        isAlreadySynced = false;
        break;
      }
    }
  }
  if (isAlreadySynced) return;

  var range = sheet.getDataRange();
  var values = range.getValues();
  var formulas = range.getFormulas();
  var oldHeaders = values[0];
  var migratedRows = [];

  for (var i = 1; i < values.length; i++) {
    var row = values[i];
    var formulaRow = (formulas && formulas[i]) ? formulas[i] : null;
    var obj = {};
    for (var j = 0; j < Math.max(oldHeaders.length, row.length); j++) {
      var rawKey = (j < oldHeaders.length && oldHeaders[j]) ? oldHeaders[j].toString().trim() : "";
      var fVal = (formulaRow && formulaRow[j]) ? formulaRow[j].toString() : "";
      var rVal = (row[j] !== undefined && row[j] !== null) ? row[j] : "";
      var cellVal = (fVal.indexOf("=") === 0) ? fVal : rVal;
      if (!rawKey) {
        // Selamatkan kolom yang sempat bergeser/tanpa header pada row lama sesuai urutan A-R
        if (j < ASPIRASI_HEADERS.length && cellVal !== "") {
          var stdKey = ASPIRASI_HEADERS[j].toLowerCase();
          if (obj[stdKey] === undefined || obj[stdKey] === "") {
            obj[stdKey] = cellVal;
          }
        }
        continue;
      }
      obj[rawKey.toLowerCase()] = cellVal;
    }

    var fm1 = obj["foto_masalah_1"] || obj["foto_masalah"] || obj["fotomasalah1"] || obj["fotomasalah"] || "";
    var masalahSlots = normalizeAspirasiPhotoSlots(
      fm1,
      obj["foto_masalah_2"] || obj["fotomasalah2"] || "",
      obj["foto_masalah_3"] || obj["fotomasalah3"] || "",
      obj["foto_masalah_4"] || obj["fotomasalah4"] || "",
      obj["foto_masalah_5"] || obj["fotomasalah5"] || ""
    );

    var fp1 = obj["foto_pengerjaan_1"] || obj["foto_pengerjaan"] || obj["fotopengerjaan1"] || obj["fotopengerjaan"] || "";
    var pengerjaanSlots = normalizeAspirasiPhotoSlots(
      fp1,
      obj["foto_pengerjaan_2"] || obj["fotopengerjaan2"] || "",
      obj["foto_pengerjaan_3"] || obj["fotopengerjaan3"] || "",
      obj["foto_pengerjaan_4"] || obj["fotopengerjaan4"] || "",
      obj["foto_pengerjaan_5"] || obj["fotopengerjaan5"] || ""
    );

    var rawTanggapan = obj["tanggapan_pengurus"] || obj["tanggapan pengurus"] || obj["tanggapanpengurus"] || obj["tanggapan"] || "";
    var namaPengurus = obj["nama_pengurus"] || obj["nama pengurus"] || obj["namapengurus"] ||
      obj["penanggap_nama"] || obj["penanggap nama"] || obj["penanggapnama"] ||
      extractAspirasiMetaName(rawTanggapan) || "";

    var rawStatus = obj["status"] ? obj["status"].toString().trim() : "";
    if (!rawStatus || isImageOrUrlValue(rawStatus)) {
      if (namaPengurus && /^(menunggu|diproses|selesai)$/i.test(namaPengurus.toString().trim())) {
        rawStatus = namaPengurus.toString().trim();
        namaPengurus = "";
      } else {
        rawStatus = stripAspirasiMetaMarker(rawTanggapan) ? "Diproses" : "Menunggu";
      }
    }

    migratedRows.push([
      obj["id"] || "",
      obj["tanggal"] || "",
      obj["nama"] || "",
      obj["kategori"] || "Masukan/Saran",
      obj["pesan"] || "",
      masalahSlots[0],
      masalahSlots[1],
      masalahSlots[2],
      masalahSlots[3],
      masalahSlots[4],
      rawStatus,
      namaPengurus,
      stripAspirasiMetaMarker(rawTanggapan),
      pengerjaanSlots[0],
      pengerjaanSlots[1],
      pengerjaanSlots[2],
      pengerjaanSlots[3],
      pengerjaanSlots[4]
    ]);
  }

  var clearCols = Math.max(oldHeaders.length, 18);
  if (values.length > 1) {
    sheet.getRange(2, 1, values.length - 1, clearCols).clearContent();
  }
  if (oldHeaders.length > 18) {
    sheet.getRange(1, 19, 1, oldHeaders.length - 18).clearContent();
  }
  sheet.getRange(1, 1, 1, 18).setValues([ASPIRASI_HEADERS]).setFontWeight("bold").setBackground("#D9E1F2");
  if (migratedRows.length > 0) {
    sheet.getRange(2, 1, migratedRows.length, 18).setValues(migratedRows);
  }
  sheet.setColumnWidths(6, 5, 150);
  sheet.setColumnWidths(14, 5, 150);
  for (var r = 0; r < migratedRows.length; r++) {
    var rowHasPhoto = false;
    for (var mc = 5; mc <= 9; mc++) {
      if (migratedRows[r][mc]) { rowHasPhoto = true; break; }
    }
    if (!rowHasPhoto) {
      for (var pc = 13; pc <= 17; pc++) {
        if (migratedRows[r][pc]) { rowHasPhoto = true; break; }
      }
    }
    if (rowHasPhoto) {
      sheet.setRowHeight(r + 2, 100);
    }
  }
}

function getSheetData(sheet) {
  if (!sheet) return [];
  var range = sheet.getDataRange();
  var data = range.getValues();
  var formulas = range.getFormulas();
  if (data.length < 2) return [];
  var headers = data[0];
  var rows = [];
  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    var formulaRow = (formulas && formulas[i]) ? formulas[i] : null;
    var obj = {};
    for (var j = 0; j < headers.length; j++) {
      var key = headers[j].toString().trim();
      var fVal = (formulaRow && formulaRow[j]) ? formulaRow[j].toString() : "";
      var rVal = (row[j] !== undefined && row[j] !== null) ? row[j] : "";
      // Jika sel berisi formula =IMAGE("..."), utamakan formula agar URL gambar terbaca
      if (fVal.indexOf("=") === 0) {
        obj[key] = fVal;
      } else {
        obj[key] = rVal;
      }
    }
    rows.push(obj);
  }
  return rows;
}

function toImageFormula(val) {
  if (!val) return "";
  var s = val.toString().trim();
  if (!s || s.indexOf("file://") === 0 || s.indexOf("content://") === 0) return "";
  if (s.toUpperCase().indexOf("=IMAGE(") === 0) return s;
  if (s.indexOf("http://") === 0 || s.indexOf("https://") === 0) {
    return '=IMAGE("' + s + '")';
  }
  return s;
}

function convertObjToRow(sheetName, d) {
  var now = new Date();
  var dateStr = Utilities.formatDate(now, Session.getScriptTimeZone(), "yyyy-MM-dd");

  if (sheetName === "Kas_RT") {
    var saldoVal = Number(d.saldo !== undefined ? d.saldo : d.jumlah) || 0;
    var periodeVal = d.periode_bulan || d.periodeBulan || resolveKasMonthKey(d.tanggal || dateStr);
    return [
      d.id || "KAS-" + now.getTime(),
      d.tanggal || dateStr,
      periodeVal,
      d.kategori || "",
      d.keterangan || "",
      Number(d.masuk) || 0,
      Number(d.keluar) || 0,
      saldoVal,
      toImageFormula(d.bukti_foto || d.buktiFoto || "")
    ];
  } else if (sheetName === "Pengurus") {
    return [
      d.id || "PGR-" + now.getTime(),
      d.jabatan || "",
      d.nama || "",
      d.noHp || "",
      d.alamat || "",
      d.masaBakti || "",
      toImageFormula(d.foto || "")
    ];
  } else if (sheetName === "Undangan") {
    return [
      d.id || "UND-" + now.getTime(),
      d.judul || "",
      d.tanggal || dateStr,
      d.waktu || "",
      d.lokasi || "",
      d.keterangan || "",
      d.tipe || "Undangan",
      Number(d.pengingatMenit) || 0,
      Boolean(d.pengingatAktif),
      Number(d.pengingatTargetMillis) || 0,
      Number(d.pengingatActivatedMillis) || 0,
      toImageFormula(d.foto || d.Foto || "")
    ];
  } else if (sheetName === "Galeri") {
    var galFoto1 = d.foto1 || d.foto || "";
    return [
      d.id || "GAL-" + now.getTime(),
      d.tanggal || dateStr,
      d.judul || "",
      d.kategori || "",
      d.keterangan || "",
      toImageFormula(galFoto1),
      toImageFormula(d.foto2 || ""),
      toImageFormula(d.foto3 || ""),
      toImageFormula(d.foto4 || ""),
      toImageFormula(d.foto5 || ""),
      toImageFormula(d.foto6 || ""),
      toImageFormula(d.foto7 || ""),
      toImageFormula(d.foto8 || ""),
      toImageFormula(d.foto9 || ""),
      toImageFormula(d.foto10 || ""),
      d.petugas || ""
    ];
  } else if (sheetName === "Aspirasi") {
    var fm1 = d.fotoMasalah1 || d.Foto_Masalah_1 || d.fotoMasalah || d.Foto_Masalah || "";
    var masalahSlots = normalizeAspirasiPhotoSlots(
      fm1,
      d.fotoMasalah2 || d.Foto_Masalah_2 || "",
      d.fotoMasalah3 || d.Foto_Masalah_3 || "",
      d.fotoMasalah4 || d.Foto_Masalah_4 || "",
      d.fotoMasalah5 || d.Foto_Masalah_5 || ""
    );
    var fp1 = d.fotoPengerjaan1 || d.Foto_pengerjaan_1 || d.Foto_Pengerjaan_1 || d.fotoPengerjaan || d.Foto_pengerjaan || d.Foto_Pengerjaan || "";
    var pengerjaanSlots = normalizeAspirasiPhotoSlots(
      fp1,
      d.fotoPengerjaan2 || d.Foto_pengerjaan_2 || d.Foto_Pengerjaan_2 || "",
      d.fotoPengerjaan3 || d.Foto_pengerjaan_3 || d.Foto_Pengerjaan_3 || "",
      d.fotoPengerjaan4 || d.Foto_pengerjaan_4 || d.Foto_Pengerjaan_4 || "",
      d.fotoPengerjaan5 || d.Foto_pengerjaan_5 || d.Foto_Pengerjaan_5 || ""
    );
    var namaPengurusVal = (d.namaPengurus || d.Nama_Pengurus || d.penanggapNama || "").toString().trim();
    var cleanTanggapan = stripAspirasiMetaMarker(
      d.tanggapanPengurus || d.Tanggapan_pengurus || d.Tanggapan_Pengurus || d.tanggapan_pengurus || ""
    );
    var statusVal = (d.status || d.Status || "Menunggu").toString().trim() || "Menunggu";
    return [
      d.id || d.ID || ("ASP-" + now.getTime()),
      d.tanggal || d.Tanggal || dateStr,
      d.nama || d.Nama || "",
      d.kategori || d.Kategori || "Masukan/Saran",
      d.pesan || d.Pesan || "",
      masalahSlots[0],
      masalahSlots[1],
      masalahSlots[2],
      masalahSlots[3],
      masalahSlots[4],
      statusVal,
      namaPengurusVal,
      cleanTanggapan,
      pengerjaanSlots[0],
      pengerjaanSlots[1],
      pengerjaanSlots[2],
      pengerjaanSlots[3],
      pengerjaanSlots[4]
    ];
  }
  return [];
}

function formatPhotoRow(sheet, sheetName, rowIndex) {
  if (!sheet || rowIndex < 2) return;
  if (sheetName === "Galeri") {
    if (sheet.getLastColumn() >= 15) {
      sheet.setColumnWidths(6, 10, 150);
    } else {
      sheet.setColumnWidth(6, 150);
    }
    var hasGalPhoto = false;
    var maxGalCol = Math.min(sheet.getLastColumn(), 15);
    for (var gCol = 6; gCol <= maxGalCol; gCol++) {
      if (sheet.getRange(rowIndex, gCol).getValue() || sheet.getRange(rowIndex, gCol).getFormula()) {
        hasGalPhoto = true;
        break;
      }
    }
    if (hasGalPhoto) {
      sheet.setRowHeight(rowIndex, 100);
    }
  } else if (sheetName === "Undangan") {
    if (sheet.getLastColumn() >= 12) {
      sheet.setColumnWidth(12, 150);
    }
    if (sheet.getRange(rowIndex, 12).getValue() || sheet.getRange(rowIndex, 12).getFormula()) {
      sheet.setRowHeight(rowIndex, 100);
    }
  } else if (sheetName === "Aspirasi") {
    if (sheet.getLastColumn() >= 18) {
      sheet.setColumnWidths(6, 5, 150);
      sheet.setColumnWidths(14, 5, 150);
    }
    var hasPhoto = false;
    for (var col = 6; col <= 10; col++) {
      if (sheet.getRange(rowIndex, col).getValue() || sheet.getRange(rowIndex, col).getFormula()) {
        hasPhoto = true;
        break;
      }
    }
    if (!hasPhoto && sheet.getLastColumn() >= 18) {
      for (var colP = 14; colP <= 18; colP++) {
        if (sheet.getRange(rowIndex, colP).getValue() || sheet.getRange(rowIndex, colP).getFormula()) {
          hasPhoto = true;
          break;
        }
      }
    }
    if (hasPhoto) {
      sheet.setRowHeight(rowIndex, 100);
    }
  }
}

function updateRow(sheet, sheetName, data, targetId) {
  var id = (targetId || data.id || data.ID || "").toString().trim();
  var values = sheet.getDataRange().getValues();
  for (var i = 1; i < values.length; i++) {
    if (values[i][0].toString().trim() === id) {
      var row = (sheetName === "Kas_RT") ? convertKasObjToRow(sheet, data) : convertObjToRow(sheetName, data);
      sheet.getRange(i + 1, 1, 1, row.length).setValues([row]);
      formatPhotoRow(sheet, sheetName, i + 1);
      return;
    }
  }
  var newRow = (sheetName === "Kas_RT") ? convertKasObjToRow(sheet, data) : convertObjToRow(sheetName, data);
  sheet.appendRow(newRow);
  formatPhotoRow(sheet, sheetName, sheet.getLastRow());
}

function convertKasObjToRow(sheet, d) {
  var now = new Date();
  var dateStr = Utilities.formatDate(now, Session.getScriptTimeZone(), "yyyy-MM-dd");
  var lastCol = sheet ? sheet.getLastColumn() : 0;
  if (lastCol <= 0) return convertObjToRow("Kas_RT", d);
  var headers = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  var saldoVal = Number(d.saldo !== undefined ? d.saldo : d.jumlah) || 0;
  var periodeVal = d.periode_bulan || d.periodeBulan || resolveKasMonthKey(d.tanggal || dateStr);
  var fotoFormula = toImageFormula(d.bukti_foto || d.buktiFoto || "");
  return headers.map(function(h) {
    var k = h.toString().trim().toLowerCase();
    if (k === "id") return d.id || "KAS-" + now.getTime();
    if (k === "tanggal") return d.tanggal || dateStr;
    if (k === "periode_bulan" || k === "periodebulan") return periodeVal;
    if (k === "kategori") return d.kategori || "";
    if (k === "keterangan") return d.keterangan || "";
    if (k === "masuk") return Number(d.masuk) || 0;
    if (k === "keluar") return Number(d.keluar) || 0;
    if (k === "saldo" || k === "jumlah") return saldoVal;
    if (k === "bukti_foto" || k === "buktifoto") return fotoFormula;
    return "";
  });
}

function resolveKasMonthKey(raw) {
  if (!raw) return "";
  if (Object.prototype.toString.call(raw) === "[object Date]" && !isNaN(raw.getTime())) {
    return Utilities.formatDate(raw, Session.getScriptTimeZone(), "yyyy-MM");
  }
  var s = raw.toString().trim();
  var m1 = s.match(/^(\d{4})[-\/](\d{1,2})/);
  if (m1) return m1[1] + "-" + ("0" + parseInt(m1[2], 10)).slice(-2);
  var m2 = s.match(/^(\d{1,2})[-\/](\d{1,2})[-\/](\d{4})/);
  if (m2) return m2[3] + "-" + ("0" + parseInt(m2[2], 10)).slice(-2);
  var months = {
    januari:1, jan:1, februari:2, feb:2, pebruari:2, maret:3, mar:3, april:4, apr:4,
    mei:5, juni:6, jun:6, juli:7, jul:7, agustus:8, agu:8, agt:8,
    september:9, sep:9, oktober:10, okt:10, november:11, nov:11, desember:12, des:12
  };
  var m3 = s.toLowerCase().match(/([a-z]+)\s+(\d{4})/);
  if (m3 && months[m3[1]]) {
    return m3[2] + "-" + ("0" + months[m3[1]]).slice(-2);
  }
  return "";
}

function formatKasMonthLabel(key) {
  var names = ["Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember"];
  var parts = key.split("-");
  if (parts.length !== 2) return key;
  var idx = parseInt(parts[1], 10) - 1;
  return (names[idx] || parts[1]) + " " + parts[0];
}

function buildKasRecapPayload(kasRows) {
  var map = {};
  var totalMasukAll = 0;
  var totalKeluarAll = 0;
  (kasRows || []).forEach(function(r) {
    var masuk = Number(r.masuk !== undefined ? r.masuk : r.Masuk) || 0;
    var keluar = Number(r.keluar !== undefined ? r.keluar : r.Keluar) || 0;
    var ket = (r.keterangan !== undefined ? r.keterangan : r.Keterangan) || "";
    if (!ket && masuk === 0 && keluar === 0) return;
    totalMasukAll += masuk;
    totalKeluarAll += keluar;
    var key = resolveKasMonthKey(r.periode_bulan || r.Periode_Bulan || r.periodeBulan || r.tanggal || r.Tanggal);
    if (!key) return;
    if (!map[key]) {
      map[key] = { periode_bulan: key, label_bulan: formatKasMonthLabel(key), totalMasuk: 0, totalKeluar: 0, jumlahTransaksi: 0 };
    }
    map[key].totalMasuk += masuk;
    map[key].totalKeluar += keluar;
    map[key].jumlahTransaksi += 1;
  });

  var keys = Object.keys(map).sort();
  var running = 0;
  var rekapBulanan = keys.map(function(k) {
    var entry = map[k];
    var arus = entry.totalMasuk - entry.totalKeluar;
    running += arus;
    return {
      Bulan: entry.label_bulan,
      Total_Masuk: entry.totalMasuk,
      Total_Keluar: entry.totalKeluar,
      periode_bulan: entry.periode_bulan,
      label_bulan: entry.label_bulan,
      totalMasuk: entry.totalMasuk,
      totalKeluar: entry.totalKeluar,
      arusKas: arus,
      saldoAkhir: running,
      jumlahTransaksi: entry.jumlahTransaksi
    };
  });

  var now = new Date();
  var tz = Session.getScriptTimeZone();
  var curKey = Utilities.formatDate(now, tz, "yyyy-MM");
  var lastDataKey = keys.length ? keys[keys.length - 1] : curKey;
  var endKey = (lastDataKey > curKey) ? lastDataKey : curKey;
  var endParts = endKey.split("-");
  var endYear = parseInt(endParts[0], 10);
  var endMonthIdx = parseInt(endParts[1], 10) - 1;

  var windowKeys = [];
  for (var offset = -11; offset <= 0; offset++) {
    var totalM = endYear * 12 + endMonthIdx + offset;
    var y = Math.floor(totalM / 12);
    var m = ((totalM % 12) + 12) % 12;
    windowKeys.push(("000" + y).slice(-4) + "-" + ("0" + (m + 1)).slice(-2));
  }

  var firstWinKey = windowKeys[0];
  var winRunning = 0;
  rekapBulanan.forEach(function(r) {
    if (r.periode_bulan < firstWinKey) {
      winRunning += (r.totalMasuk - r.totalKeluar);
    }
  });

  var byKey = {};
  rekapBulanan.forEach(function(r) { byKey[r.periode_bulan] = r; });
  var rolling12 = windowKeys.map(function(k) {
    var r = byKey[k];
    var masuk = r ? r.totalMasuk : 0;
    var keluar = r ? r.totalKeluar : 0;
    var arus = masuk - keluar;
    winRunning += arus;
    return {
      Bulan: formatKasMonthLabel(k),
      Total_Masuk: masuk,
      Total_Keluar: keluar,
      periode_bulan: k,
      label_bulan: formatKasMonthLabel(k),
      totalMasuk: masuk,
      totalKeluar: keluar,
      arusKas: arus,
      saldoAkhir: winRunning,
      jumlahTransaksi: r ? r.jumlahTransaksi : 0
    };
  });

  return {
    rekapBulanan: rekapBulanan,
    rolling12: rolling12,
    summary: {
      saldoKas: totalMasukAll - totalKeluarAll,
      totalMasuk: totalMasukAll,
      totalKeluar: totalKeluarAll
    }
  };
}

function readRekapBulananSheet(ss) {
  var sheet = ss.getSheetByName("Rekap_Bulanan");
  if (!sheet || sheet.getLastRow() < 2) return [];
  var rawRows = getSheetData(sheet);
  var running = 0;
  var out = [];
  rawRows.forEach(function(r) {
    var bulanRaw = (r.Bulan !== undefined ? r.Bulan : (r.bulan !== undefined ? r.bulan : (r.label_bulan || r.periode_bulan || ""))).toString().trim();
    var masuk = Number(r.Total_Masuk !== undefined ? r.Total_Masuk : (r.total_masuk !== undefined ? r.total_masuk : (r.totalMasuk !== undefined ? r.totalMasuk : r.Masuk))) || 0;
    var keluar = Number(r.Total_Keluar !== undefined ? r.Total_Keluar : (r.total_keluar !== undefined ? r.total_keluar : (r.totalKeluar !== undefined ? r.totalKeluar : r.Keluar))) || 0;
    if (!bulanRaw && masuk === 0 && keluar === 0) return;
    var key = resolveKasMonthKey(bulanRaw);
    var arus = masuk - keluar;
    running += arus;
    out.push({
      Bulan: bulanRaw || formatKasMonthLabel(key),
      Total_Masuk: masuk,
      Total_Keluar: keluar,
      periode_bulan: key || bulanRaw,
      label_bulan: key ? formatKasMonthLabel(key) : bulanRaw,
      totalMasuk: masuk,
      totalKeluar: keluar,
      arusKas: arus,
      saldoAkhir: running,
      jumlahTransaksi: 1
    });
  });
  return out;
}

function deleteRow(sheet, id) {
  var target = (id || "").toString().trim();
  var values = sheet.getDataRange().getValues();
  for (var i = 1; i < values.length; i++) {
    if (values[i][0].toString().trim() === target) {
      sheet.deleteRow(i + 1);
      return;
    }
  }
}

function stripAspirasiMetaMarker(raw) {
  if (!raw) return "";
  return raw.toString().replace(/\s*\[\[META_BALASAN:[^\]]+\]\]\s*/g, "\n").trim();
}

function respondAspirasi(sheet, data, targetId) {
  migrateAspirasiSheetLayout(sheet);
  var id = (targetId || data.id || data.ID || "").toString().trim();
  if (!id) {
    return { ok: false, error: "ID aspirasi kosong sehingga balasan tidak dapat dicocokkan." };
  }
  var cleanTanggapan = stripAspirasiMetaMarker(
    data.tanggapanPengurus || data.Tanggapan_pengurus || data.Tanggapan_Pengurus || data.tanggapan_pengurus || ""
  );
  var namaPengurusVal = (data.namaPengurus || data.Nama_Pengurus || data.penanggapNama || "").toString().trim();
  var statusRaw = (data.status !== undefined && data.status !== null && data.status !== "") ? data.status : data.Status;
  var statusVal = (statusRaw !== undefined && statusRaw !== null && statusRaw.toString().trim() !== "") ? statusRaw.toString().trim() : "Menunggu";

  var fp1 = (data.fotoPengerjaan1 !== undefined && data.fotoPengerjaan1 !== "") ? data.fotoPengerjaan1
    : (data.Foto_pengerjaan_1 || data.Foto_Pengerjaan_1 || data.fotoPengerjaan || data.Foto_pengerjaan || data.Foto_Pengerjaan || "");
  var pengerjaanSlots = normalizeAspirasiPhotoSlots(
    fp1,
    data.fotoPengerjaan2 || data.Foto_pengerjaan_2 || data.Foto_Pengerjaan_2 || "",
    data.fotoPengerjaan3 || data.Foto_pengerjaan_3 || data.Foto_Pengerjaan_3 || "",
    data.fotoPengerjaan4 || data.Foto_pengerjaan_4 || data.Foto_Pengerjaan_4 || "",
    data.fotoPengerjaan5 || data.Foto_pengerjaan_5 || data.Foto_Pengerjaan_5 || ""
  );

  var values = sheet.getDataRange().getValues();
  for (var i = 1; i < values.length; i++) {
    if (values[i][0].toString().trim() === id) {
      var rowNum = i + 1;
      // Perbarui tepat Kolom K (Status), Kolom L (Nama_Pengurus), Kolom M (Tanggapan_pengurus),
      // dan Kolom N-R (Foto_pengerjaan_1..5) tanpa menyentuh Kolom A-J atau menduplikasi baris.
      sheet.getRange(rowNum, 11, 1, 8).setValues([[
        statusVal,
        namaPengurusVal,
        cleanTanggapan,
        pengerjaanSlots[0],
        pengerjaanSlots[1],
        pengerjaanSlots[2],
        pengerjaanSlots[3],
        pengerjaanSlots[4]
      ]]);
      formatPhotoRow(sheet, "Aspirasi", rowNum);
      return { ok: true, mode: "updated", rowNum: rowNum };
    }
  }

  // Upsert baris jika aspirasi belum tercatat di sheet (mis. item baru/offline/seed)
  if (data.pesan || data.Pesan || data.nama || data.Nama || cleanTanggapan) {
    var upsertData = {};
    for (var k in data) {
      if (Object.prototype.hasOwnProperty.call(data, k)) upsertData[k] = data[k];
    }
    upsertData.id = id;
    upsertData.status = statusVal;
    upsertData.namaPengurus = namaPengurusVal;
    upsertData.tanggapanPengurus = cleanTanggapan;
    upsertData.fotoPengerjaan1 = pengerjaanSlots[0];
    upsertData.fotoPengerjaan2 = pengerjaanSlots[1];
    upsertData.fotoPengerjaan3 = pengerjaanSlots[2];
    upsertData.fotoPengerjaan4 = pengerjaanSlots[3];
    upsertData.fotoPengerjaan5 = pengerjaanSlots[4];
    var newRow = convertObjToRow("Aspirasi", upsertData);
    sheet.appendRow(newRow);
    var appendedRow = sheet.getLastRow();
    formatPhotoRow(sheet, "Aspirasi", appendedRow);
    return { ok: true, mode: "inserted", rowNum: appendedRow };
  }

  return { ok: false, error: "Baris aspirasi dengan ID '" + id + "' tidak ditemukan di Spreadsheet." };
}

function backupAllData(ss, allData) {
  if (allData.kas && allData.kas.length) {
    var sKas = ensureSheet(ss, "Kas_RT");
    allData.kas.forEach(function(item) { updateRow(sKas, "Kas_RT", item, item.id); });
  }
  if (allData.pengurus && allData.pengurus.length) {
    var sPgr = ensureSheet(ss, "Pengurus");
    allData.pengurus.forEach(function(item) { updateRow(sPgr, "Pengurus", item, item.id); });
  }
  if (allData.undangan && allData.undangan.length) {
    var sUnd = ensureSheet(ss, "Undangan");
    allData.undangan.forEach(function(item) { updateRow(sUnd, "Undangan", item, item.id); });
  }
  if (allData.galeri && allData.galeri.length) {
    var sGal = ensureSheet(ss, "Galeri");
    allData.galeri.forEach(function(item) { updateRow(sGal, "Galeri", item, item.id); });
  }
  if (allData.aspirasi && allData.aspirasi.length) {
    var sAsp = ensureSheet(ss, "Aspirasi");
    allData.aspirasi.forEach(function(item) { updateRow(sAsp, "Aspirasi", item, item.id); });
  }
}
// === AKHIR KODE APPS SCRIPT v9 (PASTIKAN BARIS INI IKUT TERSALIN) ===
""".trimIndent()
}
