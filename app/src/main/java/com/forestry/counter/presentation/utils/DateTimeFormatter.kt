package com.forestry.counter.presentation.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formateur centralisé pour les dates et heures.
 * Remplace les 36 SimpleDateFormat dupliqués dans le codebase.
 * Utilise Locale.getDefault() par défaut pour respecter la locale utilisateur.
 */
object DateTimeFormatter {

    // ── Patterns courants (constantes pour éviter la duplication) ─────────────

    private val DATE_FR = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }
    private val DATETIME_FR = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }
    private val DATE_ISO = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }
    private val TIMESTAMP_FILE = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
    }
    private val MONTH_YEAR = ThreadLocal.withInitial {
        SimpleDateFormat("MM/yy", Locale.getDefault())
    }

    // ── Helper : ThreadLocal.get() est non-null car withInitial garantit l'init ─

    private fun ThreadLocal<SimpleDateFormat>.safeGet(): SimpleDateFormat =
        requireNotNull(get()) { "ThreadLocal SimpleDateFormat not initialized" }

    // ── Formatage ─────────────────────────────────────────────────────────────

    /** Format : 30/06/2026 */
    fun date(date: Date = Date()): String = DATE_FR.safeGet().format(date)

    /** Format : 30/06/2026 14:30 */
    fun dateTime(date: Date = Date()): String = DATETIME_FR.safeGet().format(date)

    /** Format : 2026-06-30 (ISO) */
    fun dateIso(date: Date = Date()): String = DATE_ISO.safeGet().format(date)

    /** Format : 20260630-1430 (pour noms de fichiers) */
    fun timestampFile(date: Date = Date()): String = TIMESTAMP_FILE.safeGet().format(date)

    /** Format : 06/26 (mois/année court) */
    fun monthYear(date: Date = Date()): String = MONTH_YEAR.safeGet().format(date)

    /** Format : 06/26 (mois/année court) */
    fun monthYear(dateMs: Long): String = MONTH_YEAR.safeGet().format(Date(dateMs))

    // ── Formatage depuis timestamp (Long) ─────────────────────────────────────

    fun date(dateMs: Long): String = DATE_FR.safeGet().format(Date(dateMs))

    fun dateTime(dateMs: Long): String = DATETIME_FR.safeGet().format(Date(dateMs))

    fun dateIso(dateMs: Long): String = DATE_ISO.safeGet().format(Date(dateMs))

    // ── Formatage custom (pour cas spécifiques) ───────────────────────────────

    fun custom(pattern: String, date: Date = Date()): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)

    fun custom(pattern: String, dateMs: Long): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(dateMs))
}
