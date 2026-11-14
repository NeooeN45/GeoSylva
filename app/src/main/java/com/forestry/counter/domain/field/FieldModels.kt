package com.forestry.counter.domain.field

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class FieldTargetKind { WORKSITE, WOOD_DEPOT, WOOD_OBJECT, FOREST, PARCEL, PLOT }
@Serializable data class FieldTarget(val kind: FieldTargetKind, val id: String) {
    init {
        require(id.isNotBlank() && id.length <= 200 && id.none { it.isISOControl() })
        if (kind in setOf(FieldTargetKind.WORKSITE,FieldTargetKind.WOOD_DEPOT,FieldTargetKind.WOOD_OBJECT)) require(UUID.fromString(id).toString() == id)
    }
}
@Serializable enum class FieldEntryKind { NOTE, ACCESS, BEFORE, AFTER, INCIDENT, ACTIVITY, QUALITY, INITIAL_STOCK, INCOMING, REMOVAL, REMAINING }
@Serializable enum class FieldQuantityUnit { SOLID_M3, APPARENT_M3, TONNE, STERE, PIECE }
@Serializable enum class FieldActivity { PRODUCTION, TRAVEL, WAITING, MAINTENANCE, SURVEY }
@Serializable enum class FieldRemainder { PARTIAL, BOTTOM, EMPTY, UNKNOWN }

/** Editable draft: blanks are preserved and do not participate in any balance. */
@Serializable data class FieldEntryForm(
    val kind: FieldEntryKind = FieldEntryKind.NOTE,
    val title: String = "", val notes: String = "",
    val observed: String = "", val zone: String = ZoneId.systemDefault().id,
    val latitude: String = "", val longitude: String = "", val locationSource: String = "MANUAL",
    val accuracyM: String = "", val vehicle: String = "", val restriction: String = "",
    val durationMinutes: String = "", val activity: FieldActivity? = null, val equipment: String = "",
    val product: String = "", val defects: String = "",
    val quantity: String = "", val unit: FieldQuantityUnit? = null, val convention: String = "",
    val counterparty: String = "", val destination: String = "", val documentReference: String = "",
    val passageReference: String = "", val remainder: FieldRemainder? = null,
    val photoIds: List<String> = emptyList(), val correctionOf: String? = null, val correctionReason: String = "",
) {
    fun validate(target: FieldTarget): FieldEntryForm {
        require(title.isNotBlank() && title.length <= 200 && notes.length <= 5000)
        require(listOf(vehicle,restriction,equipment,product,defects,convention,counterparty,destination,documentReference,passageReference,correctionReason).all { it.length <= 500 })
        require(photoIds.size <= 6 && photoIds.distinct().size == photoIds.size)
        photoIds.forEach { require(UUID.fromString(it).toString() == it) }
        correctionOf?.let { require(UUID.fromString(it).toString() == it && correctionReason.isNotBlank()) }
        require(observed.length <= 32 && zone.length <= 100)
        observedInstant()
        if (latitude.isNotBlank() || longitude.isNotBlank()) {
            require(decimal(latitude) in BigDecimal("-90")..BigDecimal("90"))
            require(decimal(longitude) in BigDecimal("-180")..BigDecimal("180"))
        }
        require(locationSource in setOf("MANUAL", "GPS"))
        if (accuracyM.isNotBlank()) require(decimal(accuracyM) > BigDecimal.ZERO)
        if (kind == FieldEntryKind.ACTIVITY) require(activity != null && decimal(durationMinutes) > BigDecimal.ZERO)
        if (kind == FieldEntryKind.QUALITY) require(target.kind == FieldTargetKind.WOOD_OBJECT)
        if (kind in quantityKinds) {
            require(target.kind == FieldTargetKind.WOOD_OBJECT && observed.isNotBlank())
            if (kind == FieldEntryKind.REMAINING && remainder == FieldRemainder.UNKNOWN) require(quantity.isBlank())
            else {
                require(unit != null && convention.isNotBlank())
                val value = decimal(quantity)
                require(value >= BigDecimal.ZERO)
                if (kind in setOf(FieldEntryKind.INCOMING, FieldEntryKind.REMOVAL)) require(value > BigDecimal.ZERO)
                if (unit == FieldQuantityUnit.PIECE) require(value.stripTrailingZeros().scale() <= 0)
                if (kind == FieldEntryKind.REMAINING) {
                    require(remainder != null)
                    require(if (remainder == FieldRemainder.EMPTY) value.compareTo(BigDecimal.ZERO) == 0 else value > BigDecimal.ZERO)
                }
            }
        }
        return this
    }
    fun observedInstant(): Long? {
        if (observed.isBlank()) return null
        val local = LocalDateTime.parse(observed, dateFormat)
        val z = ZoneId.of(zone)
        val offsets = z.rules.getValidOffsets(local)
        require(offsets.size == 1) { "AMBIGUOUS_OR_MISSING_TIME" }
        return local.toInstant(offsets.single()).toEpochMilli().also { require(it >= 0) }
    }
    companion object {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT)
        val quantityKinds = setOf(FieldEntryKind.INITIAL_STOCK, FieldEntryKind.INCOMING, FieldEntryKind.REMOVAL, FieldEntryKind.REMAINING)
        fun decimal(raw: String): BigDecimal {
            require(raw.length <= 64 && Regex("[+-]?[0-9]+([.,][0-9]+)?").matches(raw.trim()))
            return BigDecimal(raw.trim().replace(',', '.'))
        }
    }
}

@Serializable data class WorksiteParcel(
    val source: String, val reference: String, val label: String,
    val localParcelId: String? = null, val sourceVersion: String = "",
)
@Serializable data class WorksiteForm(val name: String = "", val notes: String = "", val parcels: List<WorksiteParcel> = emptyList()) {
    fun validate(): WorksiteForm {
        require(name.isNotBlank() && name.length <= 200 && notes.length <= 5000 && parcels.size <= 100)
        require(parcels.distinctBy { it.source to it.reference }.size == parcels.size)
        parcels.forEach {
            require(it.source.isNotBlank() && it.reference.isNotBlank() && it.label.isNotBlank())
            require(listOf(it.source,it.reference,it.label,it.sourceVersion).all { v -> v.length <= 300 })
            it.localParcelId?.let { id -> require(id.isNotBlank() && id.length <= 200 && id.none { it.isISOControl() }) }
        }
        return this
    }
}
@Serializable data class SavedWorksite(val id: String, val form: WorksiteForm, val revision: Int, val archived: Boolean)
@Serializable data class FieldEntry(val id: String, val target: FieldTarget, val form: FieldEntryForm, val recordedAt: Long, val author: String)
data class FieldDraft(val revision: Long, val content: String, val updatedAt: Long)
@Serializable data class FieldPhoto(val id: String, val caption: String, val mime: String, val bytes: Int, val sha256: String)
/** Lightweight identity metadata only; no measurement history or stock is loaded. */
@Serializable data class FieldTargetChoice(
    val target: FieldTarget, val label: String,
    val woodKind: String? = null, val woodTag: String? = null, val declaredSpecies: String? = null,
    val depotId: String? = null, val depotLabel: String? = null,
)

object FieldCodec {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
}

/** Physical, explicitly declared quantities only. No currency or volume conversion. */
object FieldBalance {
    data class Balance(val expected: BigDecimal?, val observed: BigDecimal?, val difference: BigDecimal?, val reason: String?)
    fun active(entries: List<FieldEntry>): List<FieldEntry> {
        val corrected = entries.mapNotNull { it.form.correctionOf }.toSet()
        return entries.filter { it.id !in corrected }
    }
    fun at(entries: List<FieldEntry>, time: Long, unit: FieldQuantityUnit, convention: String): Balance {
        val current = active(entries).filter { it.form.kind in FieldEntryForm.quantityKinds }
            .filter { it.form.observedInstant()?.let { date -> date <= time } == true }
        val compatible = current.filter { it.form.unit == unit && it.form.convention == convention }
        // A mixed convention is a separate ledger; never silently combine it with this one.
        val bases = compatible.filter { it.form.kind == FieldEntryKind.INITIAL_STOCK }
        if (bases.size != 1) return Balance(null, null, null, "BASE_REQUIRED")
        val base = bases.single()
        val start = checkNotNull(base.form.observedInstant())
        if (compatible.any { it.form.kind in setOf(FieldEntryKind.INCOMING, FieldEntryKind.REMOVAL) && checkNotNull(it.form.observedInstant()) < start })
            return Balance(null, null, null, "MOVEMENT_BEFORE_BASE")
        var expected = FieldEntryForm.decimal(base.form.quantity)
        val movements = compatible.filter { it.form.kind in setOf(FieldEntryKind.INCOMING, FieldEntryKind.REMOVAL) }
        for (group in movements.groupBy { it.form.observedInstant() }.toSortedMap(compareBy { it })) {
            for (entry in group.value) expected += FieldEntryForm.decimal(entry.form.quantity) *
                if (entry.form.kind == FieldEntryKind.REMOVAL) BigDecimal(-1) else BigDecimal.ONE
            if (expected < BigDecimal.ZERO) return Balance(null, null, null, "NEGATIVE_BALANCE")
        }
        val controls = compatible.filter { it.form.kind == FieldEntryKind.REMAINING && it.form.observedInstant() == time && it.form.remainder != FieldRemainder.UNKNOWN }
        val observed = controls.singleOrNull()?.form?.quantity?.let(FieldEntryForm::decimal)
        return Balance(expected, observed, observed?.minus(expected), if (controls.size > 1) "MULTIPLE_CONTROLS" else null)
    }
}
