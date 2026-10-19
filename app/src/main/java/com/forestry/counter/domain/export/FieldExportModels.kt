package com.forestry.counter.domain.export

import com.forestry.counter.domain.field.WorksiteExportSnapshot
import kotlinx.serialization.Serializable

/** Local portable archive, not a FORLOG exchange protocol, signature or server acknowledgement. */
@Serializable data class ExportManifest(
    val format: String = "geosylva-worksite-export",
    val formatVersion: Int = 1,
    val exportId: String,
    val capturedAt: Long,
    val capturedAtUtc: String,
    val scope: String,
    val worksiteId: String,
    val worksiteRevision: Int,
    val photosIncluded: Boolean = false,
    val csvEncoding: String = "UTF-8 with BOM",
    val csvSeparator: String = ";",
    val decimalRepresentation: String = "exact decimal text; no unit conversion",
    val revisions: List<ExportRevision>,
    val files: List<ExportFileDigest>,
)

@Serializable data class ExportRevision(val type: String, val id: String, val revision: Int)
@Serializable data class ExportFileDigest(val name: String, val bytes: Int, val sha256: String)

data class ExportVerificationResult(val manifest: ExportManifest, val snapshot: WorksiteExportSnapshot)

/** Limits apply to actual bytes read, never to untrusted ZIP size declarations. */
data class FieldExportLimits(val maxFileBytes: Int = 16 * 1024 * 1024, val maxArchiveBytes: Int = 32 * 1024 * 1024) {
    init { require(maxFileBytes > 0 && maxArchiveBytes >= maxFileBytes) }
}

enum class FieldExportError { INVALID_SNAPSHOT, INVALID_ARCHIVE, UNSUPPORTED_FORMAT, LIMIT_EXCEEDED, INTEGRITY, BALANCE_MISMATCH, IO }
class FieldExportException(val code: FieldExportError) : IllegalArgumentException(code.name)
