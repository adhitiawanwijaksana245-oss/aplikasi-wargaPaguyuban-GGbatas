package com.example.util

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class SheetImageRows(
    val byRowId: Map<String, Map<String, String>> = emptyMap(),
    val byRowIndex: Map<Int, Map<String, String>> = emptyMap()
)

/**
 * Mengekstrak URL gambar dari rumus Google Sheets =IMAGE("https://...") secara langsung
 * melalui file ekspor XLSX publik. Berguna ketika Apps Script Web App doGet() masih
 * menggunakan getValues() sehingga sel gambar muncul sebagai {"valueType":"IMAGE"}.
 */
object XlsxImageFormulaExtractor {

    fun extract(xlsxInputStream: InputStream): Map<String, SheetImageRows> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(xlsxInputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val workbookBytes = entries["xl/workbook.xml"] ?: return emptyMap()
        val relsBytes = entries["xl/_rels/workbook.xml.rels"] ?: return emptyMap()
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

        val relTargetById = parseWorkbookRelationships(relsBytes)
        val sheets = parseWorkbookSheets(workbookBytes)

        val result = LinkedHashMap<String, SheetImageRows>()
        for ((sheetName, relId) in sheets) {
            val target = relTargetById[relId] ?: continue
            val normalizedPath = normalizeWorksheetPath(target)
            val sheetBytes = entries[normalizedPath] ?: continue
            result[sheetName] = parseWorksheetImages(sheetBytes, sharedStrings)
        }
        return result
    }

    private fun normalizeWorksheetPath(target: String): String {
        val clean = target.trim().removePrefix("/")
        return if (clean.startsWith("xl/")) clean else "xl/$clean"
    }

    private fun newSafeDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        try {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: Exception) {
        }
    }.newDocumentBuilder()

    private fun parseWorkbookRelationships(bytes: ByteArray): Map<String, String> {
        val map = HashMap<String, String>()
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val nodes = doc.getElementsByTagName("Relationship")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val id = el.getAttribute("Id")
            val target = el.getAttribute("Target")
            if (id.isNotBlank() && target.isNotBlank()) {
                map[id] = target
            }
        }
        return map
    }

    private fun parseWorkbookSheets(bytes: ByteArray): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val nodes = doc.getElementsByTagName("sheet")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val name = el.getAttribute("name")
            val relId = el.getAttribute("r:id").ifBlank { el.getAttribute("id") }
            if (name.isNotBlank() && relId.isNotBlank()) {
                list.add(name to relId)
            }
        }
        return list
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val siNodes = doc.getElementsByTagName("si")
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as? Element ?: continue
            val tNodes = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) {
                val text = tNodes.item(j)?.textContent ?: ""
                sb.append(text)
            }
            strings.add(sb.toString())
        }
        return strings
    }

    private fun parseWorksheetImages(
        bytes: ByteArray,
        sharedStrings: List<String>
    ): SheetImageRows {
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val rowNodes = doc.getElementsByTagName("row")

        val colHeaders = HashMap<String, String>()
        val byRowId = LinkedHashMap<String, Map<String, String>>()
        val byRowIndex = LinkedHashMap<Int, Map<String, String>>()

        var headerRowNumber = -1
        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as? Element ?: continue
            val rowNum = rowEl.getAttribute("r").toIntOrNull() ?: (i + 1)

            if (headerRowNumber == -1 || rowNum == 1) {
                headerRowNumber = rowNum
                val cells = directChildElements(rowEl, "c")
                for (cell in cells) {
                    val ref = cell.getAttribute("r")
                    val colLetter = ref.takeWhile { it.isLetter() }
                    val headerText = readCellDisplayText(cell, sharedStrings).trim()
                    if (colLetter.isNotBlank() && headerText.isNotBlank()) {
                        colHeaders[colLetter] = headerText
                    }
                }
                continue
            }

            var rowId = ""
            val rowImages = LinkedHashMap<String, String>()
            val cells = directChildElements(rowEl, "c")
            for (cell in cells) {
                val ref = cell.getAttribute("r")
                val colLetter = ref.takeWhile { it.isLetter() }
                val headerName = colHeaders[colLetter] ?: colLetter

                if (colLetter == "A" || headerName.equals("ID", ignoreCase = true)) {
                    val idVal = readCellDisplayText(cell, sharedStrings).trim()
                    if (idVal.isNotBlank()) {
                        rowId = idVal
                    }
                }

                val formulaNode = firstDirectChild(cell, "f")
                if (formulaNode != null) {
                    val rawFormula = formulaNode.textContent?.trim().orEmpty()
                    val normalizedFormula = if (rawFormula.startsWith("=")) rawFormula else "=$rawFormula"
                    val extractedUrl = PhotoStorageHelper.extractUrlFromFormula(normalizedFormula)
                    if (extractedUrl.startsWith("http://") || extractedUrl.startsWith("https://")) {
                        rowImages[headerName] = extractedUrl
                    }
                }
            }

            if (rowImages.isNotEmpty()) {
                val dataRowIndex = (rowNum - headerRowNumber - 1).coerceAtLeast(0)
                byRowIndex[dataRowIndex] = rowImages
                if (rowId.isNotBlank()) {
                    byRowId[rowId] = rowImages
                }
            }
        }

        return SheetImageRows(byRowId = byRowId, byRowIndex = byRowIndex)
    }

    private fun directChildElements(parent: Element, tagName: String): List<Element> {
        val list = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tagName) {
                list.add(child as Element)
            }
        }
        return list
    }

    private fun firstDirectChild(parent: Element, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tagName) {
                return child as Element
            }
        }
        return null
    }

    private fun readCellDisplayText(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "s") {
            val idx = firstDirectChild(cell, "v")?.textContent?.trim()?.toIntOrNull() ?: -1
            return sharedStrings.getOrNull(idx) ?: ""
        }
        if (type == "inlineStr") {
            val isEl = firstDirectChild(cell, "is")
            if (isEl != null) {
                val tNodes = isEl.getElementsByTagName("t")
                val sb = StringBuilder()
                for (i in 0 until tNodes.length) {
                    sb.append(tNodes.item(i)?.textContent ?: "")
                }
                return sb.toString()
            }
        }
        return firstDirectChild(cell, "v")?.textContent ?: ""
    }
}
