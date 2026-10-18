package com.forestry.counter.presentation.screens.wood

import com.forestry.counter.domain.repository.SavedWoodObservation
import com.forestry.counter.domain.wood.WoodObjectKind
import kotlinx.serialization.Serializable

@Serializable internal data class WoodStoredDraft(
    val fields: Map<String,String>?,val objectId: String?,val revision: Int?,val correctionId: String?,
    val command: String?=null,val commandSha256: String?=null,val next: Boolean=false,
)
internal fun WoodManualForm.toDraftFields()=mapOf(
    "kind" to kind.name,"name" to name,"tag" to tag,"species" to species,"quality" to quality,"origin" to origin,"notes" to notes,
    "length" to length,"depth" to depth,"height" to height,"pieceLength" to pieceLength,"start" to start,"middle" to middle,"end" to end,
    "shape" to shape.toString(),"protocol" to protocol,"method" to method.orEmpty(),"bark" to bark.orEmpty(),
    "observedDate" to observedDate,"observedZone" to observedZone,"reason" to reason,"identityOnly" to identityOnly.toString(),"newWithMeasurement" to newWithMeasurement.toString(),
)
internal fun restoreWoodForm(fields:Map<String,String>,correction:SavedWoodObservation?):WoodManualForm {
    fun value(key:String)=fields[key].orEmpty()
    return WoodManualForm(WoodObjectKind.valueOf(value("kind")),name=value("name"),tag=value("tag"),species=value("species"),quality=value("quality"),origin=value("origin"),notes=value("notes"),
        length=value("length"),depth=value("depth"),height=value("height"),pieceLength=value("pieceLength"),start=value("start"),middle=value("middle"),end=value("end"),shape=value("shape").toBooleanStrict(),protocol=value("protocol"),method=value("method").ifEmpty {null},bark=value("bark").ifEmpty {null},
        observedDate=value("observedDate"),observedZone=value("observedZone"),reason=value("reason"),identityOnly=value("identityOnly").toBooleanStrict(),newWithMeasurement=value("newWithMeasurement").toBooleanStrict(),correction=correction)
}
