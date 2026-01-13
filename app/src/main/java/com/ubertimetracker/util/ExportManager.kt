package com.ubertimetracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.ubertimetracker.data.model.TimesheetEntry
import com.ubertimetracker.data.model.WorkSegment
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.*
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr

/**
 * Export Manager for generating Arbeitszeitliste (German Timesheet)
 * File naming format: Arbeitszeitliste_YYYY_MM.{pdf|xlsx|docx}
 */
class ExportManager(private val context: Context) {

    private val germanMonths = mapOf(
        1 to "Januar",
        2 to "Februar",
        3 to "März",
        4 to "April",
        5 to "Mai",
        6 to "Juni",
        7 to "Juli",
        8 to "August",
        9 to "September",
        10 to "Oktober",
        11 to "November",
        12 to "Dezember"
    )

    private val germanDays = mapOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So"
    )

    /**
     * Generate filename in format: Arbeitszeitliste_YYYY_MM
     */
    private fun generateFileName(yearMonth: YearMonth, extension: String): String {
        val year = yearMonth.year
        val month = String.format("%02d", yearMonth.monthValue)
        return "Arbeitszeitliste_${year}_$month.$extension"
    }

    /**
     * Helper to extract fields for export
     */
    private data class ExportFields(
        val start1: String = "-",
        val stop1: String = "-",
        val pauseRange: String = "-",
        val totalPause: String = "-",
        val start2: String = "-",
        val stop2: String = "-"
    )

    private fun extractExportFields(entry: TimesheetEntry): ExportFields {
        val workSegments = entry.segments.filterIsInstance<WorkSegment.Work>()
        val pauseSegments = entry.segments.filterIsInstance<WorkSegment.Pause>()
        
        val start1 = workSegments.getOrNull(0)?.startTime ?: "-"
        val stop1 = workSegments.getOrNull(0)?.endTime ?: "-"
        
        val start2 = workSegments.getOrNull(1)?.startTime ?: "-"
        val stop2 = workSegments.getOrNull(1)?.endTime ?: "-"
        
        // Use the first pause for the specific range column
        val firstPause = pauseSegments.firstOrNull()
        val pauseRange = if (firstPause != null && firstPause.startTime.isNotEmpty()) {
            "${firstPause.startTime}-${firstPause.endTime ?: "?"}"
        } else {
            "-"
        }
        
        // Calculate total pause duration
        var totalMinutes = 0L
        for (segment in pauseSegments) { 
             var added = false
             val parts = segment.duration?.split(":")
             if (parts?.size == 2) {
                 val h = parts[0].toLongOrNull() ?: 0L
                 val m = parts[1].toLongOrNull() ?: 0L
                 val minutes = (h * 60 + m)
                 if (minutes > 0) {
                     totalMinutes += minutes
                     added = true
                 }
             }
             
             if (!added) {
                 // Fallback: Calculate from Start/End if duration is missing or 0
                 val startParts = segment.startTime.split(":")
                 val endParts = segment.endTime?.split(":")
                 if (startParts.size == 2 && endParts?.size == 2) {
                     val startH = startParts[0].toLongOrNull() ?: 0L
                     val startM = startParts[1].toLongOrNull() ?: 0L
                     val endH = endParts[0].toLongOrNull() ?: 0L
                     val endM = endParts[1].toLongOrNull() ?: 0L
                     
                     val startTotal = startH * 60 + startM
                     val endTotal = endH * 60 + endM
                     var diff = endTotal - startTotal
                     if (diff < 0) diff += 24 * 60 
                     
                     if (diff > 0) totalMinutes += diff
                 }
             }
        }
        val totalPause = if (totalMinutes > 0) String.format("%02d:%02dh", totalMinutes / 60, totalMinutes % 60) else "-"

        return ExportFields(start1, stop1, pauseRange, totalPause, start2, stop2)
    }

    /**
     * Export to PDF
     */
    fun exportToPdf(
        entries: List<TimesheetEntry>,
        weeklyTotals: Map<Int, String>,
        monthlyTotal: String,
        yearMonth: YearMonth
    ): File {
        val fileName = generateFileName(yearMonth, "pdf")
        val file = File(getExportDirectory(), fileName)
        
        val writer = PdfWriter(FileOutputStream(file))
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        document.setMargins(10f, 10f, 10f, 10f)

        // Title
        val monthName = germanMonths[yearMonth.monthValue] ?: yearMonth.month.name
        document.add(
            Paragraph("ARBEITSZEITLISTE")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(14f)
                .setBold()
        )
        document.add(
            Paragraph("$monthName ${yearMonth.year}")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(12f)
        )
        document.add(Paragraph("\n"))

        // Table - 9 columns
        val table = Table(UnitValue.createPercentArray(floatArrayOf(10f, 6f, 10f, 10f, 14f, 10f, 10f, 10f, 10f)))
            .useAllAvailableWidth()

        // Header
        val headers = listOf("Datum", "Tag", "Start 1", "Stop 1", "Pause", "Gesamtp.", "Start 2", "Stop 2", "Gesamt")
        headers.forEach { header ->
            table.addHeaderCell(
                Cell().add(Paragraph(header).setBold().setFontSize(8f))
                    .setTextAlignment(TextAlignment.CENTER)
            )
        }

        var lastWeekNumber = -1

        // Data rows
        entries.forEach { entry ->
            val weekNumber = entry.weekNumber
            
            // Weekly total row
            if (lastWeekNumber != -1 && weekNumber != lastWeekNumber) {
                addWeeklyTotalRow(table, lastWeekNumber, weeklyTotals[lastWeekNumber] ?: "00:00")
            }
            if (lastWeekNumber != weekNumber) {
                 lastWeekNumber = weekNumber
            }

            val dayName = germanDays[entry.date.dayOfWeek] ?: "?"
            val dateStr = entry.date.format(DateTimeFormatter.ofPattern("dd.MM"))
            
            val fields = extractExportFields(entry)

            table.addCell(Cell().add(Paragraph(dateStr).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
            table.addCell(Cell().add(Paragraph(dayName).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
            table.addCell(Cell().add(Paragraph(fields.start1).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
            table.addCell(Cell().add(Paragraph(fields.stop1).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
            table.addCell(Cell().add(Paragraph(fields.pauseRange).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
            table.addCell(Cell().add(Paragraph(fields.totalPause).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
            table.addCell(Cell().add(Paragraph(fields.start2).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
            table.addCell(Cell().add(Paragraph(fields.stop2).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
            
            val totalText = if (entry.hasConflict) "⚠️ ${entry.totalDailyHours ?: "-"}" else entry.totalDailyHours ?: "-"
            table.addCell(Cell().add(Paragraph(totalText).setFontSize(8f)).setTextAlignment(TextAlignment.CENTER))
        }

        // Last weekly total
        if (entries.isNotEmpty()) {
            addWeeklyTotalRow(table, lastWeekNumber, weeklyTotals[lastWeekNumber] ?: "00:00")
        }

        document.add(table)

        // Monthly total at bottom right
        document.add(Paragraph("\n"))
        document.add(
            Paragraph("MONATSGESAMT: $monthlyTotal")
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(14f)
                .setBold()
        )

        document.close()
        return file
    }

    private fun addWeeklyTotalRow(table: Table, weekNumber: Int, total: String) {
        val cell = Cell(1, 9) // Spanning 9 columns
            .add(Paragraph("Woche $weekNumber Gesamt: $total").setBold().setFontSize(8f))
            .setTextAlignment(TextAlignment.RIGHT)
        table.addCell(cell)
    }

    /**
     * Export to Excel (XLSX)
     */
    fun exportToExcel(
        entries: List<TimesheetEntry>,
        weeklyTotals: Map<Int, String>,
        monthlyTotal: String,
        yearMonth: YearMonth
    ): File {
        val fileName = generateFileName(yearMonth, "xlsx")
        val file = File(getExportDirectory(), fileName)
        
        val workbook = XSSFWorkbook()
        val monthName = germanMonths[yearMonth.monthValue] ?: yearMonth.month.name
        val sheet = workbook.createSheet("$monthName ${yearMonth.year}")

        // Title row
        val titleRow = sheet.createRow(0)
        titleRow.createCell(0).setCellValue("ARBEITSZEITLISTE - $monthName ${yearMonth.year}")

        // Header row
        val headerRow = sheet.createRow(2)
        val headers = listOf("Datum", "Tag", "Start 1", "Stop 1", "Pause", "Gesamtpause", "Start 2", "Stop 2", "Gesamt")
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }

        var rowIndex = 3
        var lastWeekNumber = -1

        entries.forEach { entry ->
            val weekNumber = entry.weekNumber
            
            // Weekly total row
            if (lastWeekNumber != -1 && weekNumber != lastWeekNumber) {
                val weekRow = sheet.createRow(rowIndex++)
                weekRow.createCell(8).setCellValue("Woche $lastWeekNumber Gesamt: ${weeklyTotals[lastWeekNumber] ?: "00:00"}")
            }
            if (lastWeekNumber != weekNumber) {
                 lastWeekNumber = weekNumber
            }

            val row = sheet.createRow(rowIndex++)
            val dayName = germanDays[entry.date.dayOfWeek] ?: "?"
            val dateStr = entry.date.format(DateTimeFormatter.ofPattern("dd.MM"))
            
            val fields = extractExportFields(entry)

            row.createCell(0).setCellValue(dateStr)
            row.createCell(1).setCellValue(dayName)
            row.createCell(2).setCellValue(fields.start1)
            row.createCell(3).setCellValue(fields.stop1)
            row.createCell(4).setCellValue(fields.pauseRange)
            row.createCell(5).setCellValue(fields.totalPause)
            row.createCell(6).setCellValue(fields.start2)
            row.createCell(7).setCellValue(fields.stop2)
            
            val totalText = if (entry.hasConflict) "⚠️ ${entry.totalDailyHours ?: "-"}" else entry.totalDailyHours ?: "-"
            row.createCell(8).setCellValue(totalText)
        }

        // Last weekly total
        if (entries.isNotEmpty()) {
            val weekRow = sheet.createRow(rowIndex++)
            weekRow.createCell(8).setCellValue("Woche $lastWeekNumber Gesamt: ${weeklyTotals[lastWeekNumber] ?: "00:00"}")
        }

        // Monthly total at bottom right
        rowIndex++
        val totalRow = sheet.createRow(rowIndex)
        totalRow.createCell(8).setCellValue("MONATSGESAMT: $monthlyTotal")

        // Set column widths
        sheet.setColumnWidth(0, 256 * 10)
        sheet.setColumnWidth(1, 256 * 6)
        sheet.setColumnWidth(2, 256 * 10)
        sheet.setColumnWidth(3, 256 * 10)
        sheet.setColumnWidth(4, 256 * 15)
        sheet.setColumnWidth(5, 256 * 12)
        sheet.setColumnWidth(6, 256 * 10)
        sheet.setColumnWidth(7, 256 * 10)
        sheet.setColumnWidth(8, 256 * 12)

        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file
    }

    /**
     * Export to Word (DOCX)
     */
    fun exportToDocx(
        entries: List<TimesheetEntry>,
        weeklyTotals: Map<Int, String>,
        monthlyTotal: String,
        yearMonth: YearMonth
    ): File {
        val fileName = generateFileName(yearMonth, "docx")
        val file = File(getExportDirectory(), fileName)
        
        val document = XWPFDocument()
        val monthName = germanMonths[yearMonth.monthValue] ?: yearMonth.month.name

        // Title
        val titleParagraph = document.createParagraph()
        titleParagraph.alignment = ParagraphAlignment.CENTER
        val titleRun = titleParagraph.createRun()
        titleRun.isBold = true
        titleRun.fontSize = 14
        titleRun.setText("ARBEITSZEITLISTE: Monat: ${monthName} ${yearMonth.year}")

        document.createParagraph()

        // Table
        val table = document.createTable()
        
        // Add explicit borders for cloud viewer compatibility
        try {
            val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
            val borders = tblPr.tblBorders ?: tblPr.addNewTblBorders()
            
            fun setBorder(border: org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder) {
                border.setVal(STBorder.SINGLE)
                border.sz = java.math.BigInteger.valueOf(4) // 4 = 1/2 pt
                border.space = java.math.BigInteger.valueOf(0)
                border.color = "000000"
            }

            setBorder(borders.bottom ?: borders.addNewBottom())
            setBorder(borders.top ?: borders.addNewTop())
            setBorder(borders.left ?: borders.addNewLeft())
            setBorder(borders.right ?: borders.addNewRight())
            setBorder(borders.insideH ?: borders.addNewInsideH())
            setBorder(borders.insideV ?: borders.addNewInsideV())
        } catch (e: Exception) {
             android.util.Log.e("ExportManager", "Error setting table borders", e)
        }

        val headerRow = table.getRow(0) ?: table.createRow()
        while (headerRow.tableCells.size < 9) {
            headerRow.createCell()
        }
        
        val headers = listOf("Datum", "Tag", "Start 1", "Stop 1", "Pause", "Gesamtpause", "Start 2", "Stop 2", "Gesamtstunden")
        headers.forEachIndexed { index, header ->
            val cell = headerRow.getCell(index)
            cell.setText(header)
            val p = cell.paragraphs[0]
            if (p.runs.isEmpty()) p.createRun()
            p.runs[0].isBold = true
            p.alignment = ParagraphAlignment.CENTER
        }

        var lastWeekNumber = -1
        
        fun addWeekHeader(weekNum: Int) {
            val row = table.createRow()
            while (row.tableCells.size < 9) row.createCell()
            row.getCell(4).setText("Woche $weekNum")
            val p = row.getCell(4).paragraphs[0]
            if (p.runs.isEmpty()) p.createRun()
            p.runs[0].isBold = true
            p.alignment = ParagraphAlignment.CENTER
        }

        fun addWeekFooter(weekNum: Int, total: String) {
            val row = table.createRow()
            while (row.tableCells.size < 9) row.createCell()
            row.getCell(6).setText("Gesamtstunden W$weekNum:")
            row.getCell(6).paragraphs[0].alignment = ParagraphAlignment.RIGHT
            row.getCell(8).setText(total)
            val p = row.getCell(8).paragraphs[0]
            if (p.runs.isEmpty()) p.createRun()
            p.runs[0].isBold = true
        }

        entries.forEach { entry ->
            val weekNumber = entry.weekNumber
            if (weekNumber != lastWeekNumber) {
                if (lastWeekNumber != -1) {
                    addWeekFooter(lastWeekNumber, weeklyTotals[lastWeekNumber] ?: "00:00")
                }
                addWeekHeader(weekNumber)
                lastWeekNumber = weekNumber
            }

            val row = table.createRow()
            while (row.tableCells.size < 9) row.createCell()
            
            val dayName = germanDays[entry.date.dayOfWeek] ?: "?"
            val dateStr = entry.date.format(DateTimeFormatter.ofPattern("dd.MM."))
            val fields = extractExportFields(entry)

            val values = listOf(
                dateStr, dayName, 
                fields.start1, fields.stop1, 
                fields.pauseRange, fields.totalPause, 
                fields.start2, fields.stop2, 
                (entry.totalDailyHours ?: "-")
            )
            
            values.forEachIndexed { i, text ->
                row.getCell(i).setText(text)
            }
            
             if (entry.date.dayOfWeek == DayOfWeek.SUNDAY) {
                  row.tableCells.forEach { cell ->
                      cell.setColor("D65A5A")
                  }
             }
             
             // Minimized spacing for DOCX
             row.tableCells.forEach { cell ->
                 if (cell.paragraphs.isNotEmpty()) {
                     cell.paragraphs[0].spacingAfter = 0
                 }
             }
        }
        
        if (entries.isNotEmpty()) {
            addWeekFooter(lastWeekNumber, weeklyTotals[lastWeekNumber] ?: "00:00")
        }
        
        val totalRow = table.createRow()
        while (totalRow.tableCells.size < 9) totalRow.createCell()
        totalRow.getCell(6).setText("Gesamtstunden Monat:")
        totalRow.getCell(6).paragraphs[0].alignment = ParagraphAlignment.RIGHT
        totalRow.getCell(8).setText(monthlyTotal)
        val pTotal = totalRow.getCell(8).paragraphs[0]
        if (pTotal.runs.isEmpty()) pTotal.createRun()
        pTotal.runs[0].isBold = true

        FileOutputStream(file).use { document.write(it) }
        document.close()
        return file
    }

    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareFile(file: File) {
        val uri = getUriForFile(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export teilen"))
    }

    private fun getExportDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "UberTimeTracker")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMimeType(file: File): String {
        return when {
            file.name.endsWith(".pdf") -> "application/pdf"
            file.name.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            file.name.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    fun saveToDownloads(file: File): Uri? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, getMimeType(file))
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            try {
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: throw Exception("Failed to open output stream")
                return uri
            } catch (e: Exception) {
                android.util.Log.e("ExportManager", "Error saving to downloads", e)
                return null
            }
        } else {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, file.name)
                file.copyTo(destFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(getMimeType(destFile)), null)
                return getUriForFile(destFile)
            } catch (e: Exception) {
                android.util.Log.e("ExportManager", "Error saving to downloads", e)
                return null
            }
        }
    }
}
