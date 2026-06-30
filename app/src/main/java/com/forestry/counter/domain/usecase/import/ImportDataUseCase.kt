package com.forestry.counter.domain.usecase.import

import android.content.Context
import android.net.Uri
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.CounterRepository
import com.forestry.counter.domain.repository.FormulaRepository
import com.forestry.counter.domain.repository.GroupRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.opencsv.CSVReader
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.InputStreamReader
import java.util.UUID
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType

class ImportDataUseCase(
    private val context: Context,
    private val groupRepository: GroupRepository,
    private val counterRepository: CounterRepository,
    private val formulaRepository: FormulaRepository
) {

    suspend fun importFromJson(uri: Uri, mode: ImportMode = ImportMode.MERGE): Result<ImportResult> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).readText()
            } ?: return Result.failure(Exception("Could not read file"))

            val json = Json { ignoreUnknownKeys = true }
            val appExport = json.decodeFromString<AppExport>(jsonString)

            var importedCount = 0
            var skippedCount = 0
            val errors = mutableListOf<String>()

            if (mode == ImportMode.REPLACE) {
                groupRepository.deleteAllGroups()
            }

            appExport.groups.forEach { groupExport ->
                try {
                    val group = Group(
                        id = if (mode == ImportMode.REPLACE) groupExport.id else UUID.randomUUID().toString(),
                        name = groupExport.name,
                        color = groupExport.color
                    )
                    groupRepository.insertGroup(group)

                    groupExport.counters.forEach { counterExport ->
                        val counter = Counter(
                            id = if (mode == ImportMode.REPLACE) counterExport.id else UUID.randomUUID().toString(),
                            groupId = group.id,
                            name = counterExport.name,
                            value = counterExport.value,
                            step = counterExport.step,
                            min = counterExport.min,
                            max = counterExport.max,
                            bgColor = counterExport.bgColor,
                            fgColor = counterExport.fgColor,
                            targetValue = counterExport.targetValue,
                            tags = counterExport.tags
                        )
                        counterRepository.insertCounter(counter)
                        importedCount++
                    }

                    groupExport.formulas.forEach { formulaExport ->
                        val formula = Formula(
                            id = if (mode == ImportMode.REPLACE) formulaExport.id else UUID.randomUUID().toString(),
                            groupId = group.id,
                            name = formulaExport.name,
                            expression = formulaExport.expression,
                            description = formulaExport.description
                        )
                        formulaRepository.insertFormula(formula)
                    }

                } catch (e: Exception) {
                    errors.add("Error importing group ${groupExport.name}: ${e.message}")
                    skippedCount++
                }
            }

            Result.success(
                ImportResult(
                    success = errors.isEmpty(),
                    importedCount = importedCount,
                    skippedCount = skippedCount,
                    errorCount = errors.size,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromCsv(
        uri: Uri,
        groupId: String,
        mode: ImportMode = ImportMode.MERGE,
        separator: Char = ',',
        hasHeader: Boolean = true
    ): Result<ImportResult> {
        return try {
            var importedCount = 0
            var skippedCount = 0
            val errors = mutableListOf<String>()

            // Lecture ligne par ligne (readNext) au lieu de readAll() pour éviter
            // de charger l'intégralité du CSV en mémoire (10k ties ≈ 70 MB sinon).
            val opened = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val parser = CSVParserBuilder()
                    .withSeparator(separator)
                    .build()
                val reader = CSVReaderBuilder(InputStreamReader(inputStream, Charsets.UTF_8))
                    .withCSVParser(parser)
                    .build()

                val firstRow = reader.readNext()
                if (firstRow == null) {
                    reader.close()
                    return@use false
                }

                val headers = if (hasHeader) firstRow else arrayOf("Name", "Value")
                val nameIndex = headers.indexOfFirst { it.equals("Name", ignoreCase = true) }
                val valueIndex = headers.indexOfFirst { it.equals("Value", ignoreCase = true) }

                suspend fun processRow(row: Array<String>, displayIndex: Int) {
                    try {
                        if (nameIndex < 0 || nameIndex >= row.size) {
                            errors.add("Row $displayIndex: Missing name column")
                            skippedCount++
                            return
                        }
                        val name = row[nameIndex].trim()
                        val value = if (valueIndex >= 0 && valueIndex < row.size) {
                            row[valueIndex].toDoubleOrNull() ?: 0.0
                        } else {
                            0.0
                        }
                        val counter = Counter(
                            id = UUID.randomUUID().toString(),
                            groupId = groupId,
                            name = name,
                            value = value
                        )
                        counterRepository.insertCounter(counter)
                        importedCount++
                    } catch (e: Exception) {
                        errors.add("Row $displayIndex: ${e.message}")
                        skippedCount++
                    }
                }

                var dataRowIndex = 0
                // Sans en-tête, la première ligne est déjà une ligne de données.
                if (!hasHeader) {
                    processRow(firstRow, dataRowIndex + 1)
                    dataRowIndex++
                }

                var row = reader.readNext()
                while (row != null) {
                    processRow(row, dataRowIndex + 1)
                    dataRowIndex++
                    row = reader.readNext()
                }
                reader.close()
                true
            } ?: false

            if (!opened) {
                return Result.failure(Exception("CSV file is empty"))
            }

            Result.success(
                ImportResult(
                    success = errors.isEmpty(),
                    importedCount = importedCount,
                    skippedCount = skippedCount,
                    errorCount = errors.size,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromExcel(
        uri: Uri,
        groupId: String,
        sheetIndex: Int = 0,
        mode: ImportMode = ImportMode.MERGE
    ): Result<ImportResult> {
        return try {
            var importedCount = 0
            var skippedCount = 0
            val errors = mutableListOf<String>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                WorkbookFactory.create(inputStream).use { workbook ->
                    if (sheetIndex >= workbook.numberOfSheets) {
                        return Result.failure(Exception("Invalid sheet index"))
                    }
                    val sheet = workbook.getSheetAt(sheetIndex)
                    if (sheet.physicalNumberOfRows == 0) {
                        return Result.failure(Exception("Excel sheet is empty"))
                    }

                    // Detect header: first row
                    val headerRow = sheet.getRow(0)
                    val nameIdx = (0 until (headerRow?.lastCellNum ?: 0).toInt()).firstOrNull { idx ->
                        headerRow?.getCell(idx)?.toString()?.trim()?.equals("Name", true) == true
                    } ?: 0
                    val valueIdx = (0 until (headerRow?.lastCellNum ?: 0).toInt()).firstOrNull { idx ->
                        headerRow?.getCell(idx)?.toString()?.trim()?.equals("Value", true) == true
                    }

                    val startRow = 1 // assume header exists
                    for (r in startRow..sheet.lastRowNum) {
                        val row = sheet.getRow(r) ?: continue
                        try {
                            val nameCell = row.getCell(nameIdx) ?: continue
                            val name = nameCell.toString().trim()
                            if (name.isBlank()) throw IllegalArgumentException("Empty name")

                            val value = valueIdx?.let { idx ->
                                val cell = row.getCell(idx)
                                when (cell?.cellType) {
                                    CellType.NUMERIC -> cell.numericCellValue
                                    CellType.STRING -> cell.stringCellValue.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }
                            } ?: 0.0

                            val counter = com.forestry.counter.domain.model.Counter(
                                id = UUID.randomUUID().toString(),
                                groupId = groupId,
                                name = name,
                                value = value
                            )
                            counterRepository.insertCounter(counter)
                            importedCount++
                        } catch (e: Exception) {
                            errors.add("Row ${r + 1}: ${e.message}")
                            skippedCount++
                        }
                    }
                }
            }

            Result.success(
                ImportResult(
                    success = errors.isEmpty(),
                    importedCount = importedCount,
                    skippedCount = skippedCount,
                    errorCount = errors.size,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun detectFileType(uri: Uri): ExportFormat? {
        val mimeType = context.contentResolver.getType(uri)
        val fileName = uri.lastPathSegment ?: ""

        return when {
            mimeType?.contains("json") == true || fileName.endsWith(".json") -> ExportFormat.JSON
            mimeType?.contains("csv") == true || fileName.endsWith(".csv") -> ExportFormat.CSV
            mimeType?.contains("excel") == true || 
            mimeType?.contains("spreadsheet") == true ||
            fileName.endsWith(".xlsx") || 
            fileName.endsWith(".xls") -> ExportFormat.XLSX
            fileName.endsWith(".zip") -> ExportFormat.ZIP
            fileName.endsWith(".db") || fileName.endsWith(".sqlite") -> ExportFormat.SQLITE
            else -> null
        }
    }
}
