package com.example

import com.example.util.XlsxImageFormulaExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxImageFormulaExtractorTest {

    @Test
    fun extract_readsMultiPhotoGaleriAndAspirasiImageFormulasFromXlsx() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun addEntry(path: String, xml: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            addEntry(
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Galeri" sheetId="4" r:id="rId8"/>
                    <sheet name="Aspirasi" sheetId="5" r:id="rId9"/>
                  </sheets>
                </workbook>
                """.trimIndent()
            )

            addEntry(
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId8" Type="worksheet" Target="worksheets/sheet4.xml"/>
                  <Relationship Id="rId9" Type="worksheet" Target="worksheets/sheet5.xml"/>
                </Relationships>
                """.trimIndent()
            )

            addEntry(
                "xl/sharedStrings.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>ID</t></si>
                  <si><t>Judul</t></si>
                  <si><t>Foto</t></si>
                  <si><t>Foto_2</t></si>
                  <si><t>Foto_3</t></si>
                  <si><t>Foto_Masalah</t></si>
                  <si><t>Foto_Pengerjaan</t></si>
                  <si><t>GAL-100</t></si>
                  <si><t>ASP-200</t></si>
                </sst>
                """.trimIndent()
            )

            addEntry(
                "xl/worksheets/sheet4.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="F1" t="s"><v>2</v></c>
                      <c r="G1" t="s"><v>3</v></c>
                      <c r="H1" t="s"><v>4</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>7</v></c>
                      <c r="F2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=f1&quot;)</f><v></v></c>
                      <c r="G2" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=f2&quot;)</f><v></v></c>
                      <c r="H2" t="str"><f>IMAGE(&quot;https://iili.io/f3.jpg&quot;)</f><v></v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """.trimIndent()
            )

            addEntry(
                "xl/worksheets/sheet5.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="F1" t="s"><v>5</v></c>
                      <c r="I1" t="s"><v>6</v></c>
                    </row>
                    <row r="3">
                      <c r="A3" t="s"><v>8</v></c>
                      <c r="F3" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=masalah1&quot;)</f><v></v></c>
                      <c r="I3" t="str"><f>IMAGE(&quot;https://drive.google.com/uc?id=solusi1&quot;)</f><v></v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """.trimIndent()
            )
        }

        val extracted = XlsxImageFormulaExtractor.extract(ByteArrayInputStream(baos.toByteArray()))

        val galeriSheet = extracted["Galeri"]
        assertNotNull(galeriSheet)
        val galRow = galeriSheet!!.byRowId["GAL-100"]
        assertNotNull(galRow)
        assertEquals("https://drive.google.com/uc?id=f1", galRow!!["Foto"])
        assertEquals("https://drive.google.com/uc?id=f2", galRow["Foto_2"])
        assertEquals("https://iili.io/f3.jpg", galRow["Foto_3"])

        val aspirasiSheet = extracted["Aspirasi"]
        assertNotNull(aspirasiSheet)
        val aspRow = aspirasiSheet!!.byRowId["ASP-200"]
        assertNotNull(aspRow)
        assertEquals("https://drive.google.com/uc?id=masalah1", aspRow!!["Foto_Masalah"])
        assertEquals("https://drive.google.com/uc?id=solusi1", aspRow["Foto_Pengerjaan"])
    }
}
