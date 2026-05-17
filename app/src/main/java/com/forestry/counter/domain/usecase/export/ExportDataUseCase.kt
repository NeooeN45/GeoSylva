package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.net.Uri
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.CounterRepository
import com.forestry.counter.domain.repository.FormulaRepository
import com.forestry.counter.domain.repository.GroupRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.charset.Charset

class ExportDataUseCase(
    private val context: Context,
    private val groupRepository: GroupRepository,
    private val counterRepository: CounterRepository,
    private val formulaRepository: FormulaRepository
) {

    suspend fun exportToJson(uri: Uri): Result<Unit> {
        return try {
            val groups = groupRepository.getAllGroups().first()
            val exportData = buildExportData(groups)
            val json = Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }
            val jsonString = json.encodeToString(exportData)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonString)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToZipFile(file: File, encoding: Charset = Charsets.UTF_8): Result<Unit> {
        return try {
            val groups = groupRepository.getAllGroups().first()
            val exportData = buildExportData(groups)

            FileOutputStream(file).use { fos ->
                ZipOutputStream(fos).use { zipStream ->
                    // Add JSON manifest
                    zipStream.putNextEntry(ZipEntry("manifest.json"))
                    val json = Json { prettyPrint = true }
                    zipStream.write(json.encodeToString(exportData).toByteArray(Charsets.UTF_8))
                    zipStream.closeEntry()

                    // Add CSV for each group
                    groups.forEach { group ->
                        val counters = counterRepository.getCountersByGroup(group.id).first()
                        val fileName = "${sanitizeFileName(group.name)}.csv"
                        zipStream.putNextEntry(ZipEntry(fileName))
                        val csvData = buildCsvData(group, counters)
                        zipStream.write(csvData.toByteArray(encoding))
                        zipStream.closeEntry()
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToCsv(
        uri: Uri,
        layout: ExportLayout = ExportLayout.LONG,
        separator: Char = ',',
        encoding: Charset = Charsets.UTF_8
    ): Result<Unit> {
        return try {
            val groups = groupRepository.getAllGroups().first()
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = CSVWriter(
                    OutputStreamWriter(outputStream, encoding),
                    separator,
                    CSVWriter.DEFAULT_QUOTE_CHARACTER,
                    CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                    CSVWriter.DEFAULT_LINE_END
                )

                when (layout) {
                    ExportLayout.LONG -> {
                        // Header
                        writer.writeNext(arrayOf("GroupID", "GroupName", "CounterID", "CounterName", "Value", "Step", "Min", "Max", "Target", "Tags"))
                        // Rows
                        for (group in groups) {
                            val counters = counterRepository.getCountersByGroup(group.id).first()
                            counters.forEach { c ->
                                writer.writeNext(
                                    arrayOf(
                                        group.id,
                                        group.name,
                                        c.id,
                                        c.name,
                                        c.value.toString(),
                                        c.step.toString(),
                                        c.min?.toString() ?: "",
                                        c.max?.toString() ?: "",
                                        c.targetValue?.toString() ?: "",
                                        c.tags.joinToString(",")
                                    )
                                )
                            }
                        }
                    }
                    ExportLayout.PIVOT -> exportPivotLayout(writer, groups)
                }

                writer.close()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToExcel(uri: Uri): Result<Unit> {
        return try {
            val groups = groupRepository.getAllGroups().first()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                XSSFWorkbook().use { workbook ->
                    groups.forEach { group ->
                        val counters = counterRepository.getCountersByGroup(group.id).first()
                        val sheet = workbook.createSheet(sanitizeSheetName(group.name.ifBlank { "Group" }))
                        // Header
                        val header = sheet.createRow(0)
                        val headers = listOf("Name", "Value", "Step", "Min", "Max", "Target", "Tags")
                        headers.forEachIndexed { idx, h -> header.createCell(idx).setCellValue(h) }

                        // Rows
                        counters.forEachIndexed { index, counter ->
                            val row = sheet.createRow(index + 1)
                            row.createCell(0).setCellValue(counter.name)
                            row.createCell(1).setCellValue(counter.value)
                            row.createCell(2).setCellValue(counter.step)
                            row.createCell(3).setCellValue(counter.min ?: Double.NaN)
                            row.createCell(4).setCellValue(counter.max ?: Double.NaN)
                            row.createCell(5).setCellValue(counter.targetValue ?: Double.NaN)
                            row.createCell(6).setCellValue(counter.tags.joinToString(","))
                        }

                        // Autosize
                        headers.indices.forEach { sheet.autoSizeColumn(it) }
                    }
                    workbook.write(outputStream)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToZip(uri: Uri, encoding: Charset = Charsets.UTF_8): Result<Unit> {
        return try {
            val groups = groupRepository.getAllGroups().first()
            val exportData = buildExportData(groups)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipStream ->
                    // Add JSON manifest
                    zipStream.putNextEntry(ZipEntry("manifest.json"))
                    val json = Json { prettyPrint = true }
                    zipStream.write(json.encodeToString(exportData).toByteArray(Charsets.UTF_8))
                    zipStream.closeEntry()

                    // Add CSV for each group
                    groups.forEach { group ->
                        val counters = counterRepository.getCountersByGroup(group.id).first()
                        val fileName = "${sanitizeFileName(group.name)}.csv"
                        zipStream.putNextEntry(ZipEntry(fileName))
                        
                        val csvData = buildCsvData(group, counters)
                        zipStream.write(csvData.toByteArray(encoding))
                        zipStream.closeEntry()
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun buildExportData(groups: List<Group>): AppExport {
        val groupExports = groups.map { group ->
            val counters = counterRepository.getCountersByGroup(group.id).first()
            val formulas = formulaRepository.getFormulasByGroup(group.id).first()

            GroupExport(
                id = group.id,
                name = group.name,
                color = group.color,
                counters = counters.map { counter ->
                    CounterExport(
                        id = counter.id,
                        groupId = counter.groupId,
                        groupName = group.name,
                        name = counter.name,
                        value = counter.value,
                        step = counter.step,
                        min = counter.min,
                        max = counter.max,
                        bgColor = counter.bgColor,
                        fgColor = counter.fgColor,
                        targetValue = counter.targetValue,
                        tags = counter.tags
                    )
                },
                formulas = formulas.map { formula ->
                    FormulaExport(
                        id = formula.id,
                        name = formula.name,
                        expression = formula.expression,
                        description = formula.description
                    )
                },
                variables = emptyList() // Will be added when variable repository is implemented
            )
        }

        return AppExport(
            exportDate = System.currentTimeMillis(),
            groups = groupExports
        )
    }

    private fun exportLongLayout(writer: CSVWriter, groups: List<Group>) {
        // Kept for future refactor; currently implemented inline in exportToCsv
        writer.writeNext(arrayOf("GroupID", "GroupName", "CounterID", "CounterName", "Value", "Step", "Min", "Max", "Target", "Tags"))
    }

    private fun exportPivotLayout(writer: CSVWriter, groups: List<Group>) {
        // Pivot layout implementation
        // This would create a matrix format suitable for forestry data
    }

    private fun buildCsvData(
        group: Group,
        counters: List<Counter>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Name,Value,Step,Min,Max,Target,Tags")
        counters.forEach { counter ->
            sb.appendLine(
                "${counter.name},${counter.value},${counter.step}," +
                        "${counter.min ?: ""},${counter.max ?: ""},${counter.targetValue ?: ""}," +
                        "\"${counter.tags.joinToString(",")}\""
            )
        }
        return sb.toString()
    }

    private fun sanitizeSheetName(name: String): String {
        // Excel sheet names cannot contain: \ / ? * [ ]
        return name.replace(Regex("[\\\\/:?*\\[\\]]"), "_").take(31)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
