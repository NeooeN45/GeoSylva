package com.forestry.counter.data.mapper

import com.forestry.counter.data.local.entity.CounterEntity
import com.forestry.counter.data.local.entity.FormulaEntity
import com.forestry.counter.data.local.entity.GroupEntity
import com.forestry.counter.data.local.entity.GroupVariableEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.local.entity.ParameterEntity
import com.forestry.counter.domain.model.Counter
import com.forestry.counter.domain.model.TargetAction
import com.forestry.counter.domain.model.Formula
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.model.GroupVariable
import com.forestry.counter.domain.model.TileSize
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.ParameterItem

// Group mappings
fun GroupEntity.toGroup(counterCount: Int = 0, totalValue: Double = 0.0): Group {
    return Group(
        id = groupId,
        name = name,
        color = color,
        sortIndex = sortIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
        counterCount = counterCount,
        totalValue = totalValue
    )
}

fun Group.toGroupEntity(): GroupEntity {
    return GroupEntity(
        groupId = id,
        name = name,
        color = color,
        sortIndex = sortIndex,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// Counter mappings
fun CounterEntity.toCounter(): Counter {
    return Counter(
        id = counterId,
        groupId = groupOwnerId,
        name = name,
        value = value,
        step = step,
        min = min,
        max = max,
        bgColor = bgColor,
        fgColor = fgColor,
        iconName = iconName,
        isComputed = isComputed,
        formulaId = formulaId,
        targetValue = targetValue,
        decimalPlaces = decimalPlaces,
        initialValue = initialValue,
        resetValue = resetValue,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        vibrationIntensity = vibrationIntensity,
        targetAction = targetAction?.let { runCatching { TargetAction.valueOf(it) }.getOrNull() },
        tags = tags?.split(",")?.map { it.trim() } ?: emptyList(),
        showInFieldView = showInFieldView,
        tileSize = TileSize.valueOf(tileSize),
        sortIndex = sortIndex,
        lastUpdatedAt = lastUpdatedAt,
        createdAt = createdAt
    )
}

fun Counter.toCounterEntity(): CounterEntity {
    return CounterEntity(
        counterId = id,
        groupOwnerId = groupId,
        name = name,
        value = value,
        step = step,
        min = min,
        max = max,
        bgColor = bgColor,
        fgColor = fgColor,
        iconName = iconName,
        isComputed = isComputed,
        formulaId = formulaId,
        targetValue = targetValue,
        decimalPlaces = decimalPlaces,
        initialValue = initialValue,
        resetValue = resetValue,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        vibrationIntensity = vibrationIntensity,
        targetAction = targetAction?.name,
        tags = tags.joinToString(","),
        showInFieldView = showInFieldView,
        tileSize = tileSize.name,
        sortIndex = sortIndex,
        lastUpdatedAt = lastUpdatedAt,
        createdAt = createdAt
    )
}

// Formula mappings
fun FormulaEntity.toFormula(): Formula {
    return Formula(
        id = formulaId,
        groupId = groupOwnerId,
        name = name,
        expression = expression,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Formula.toFormulaEntity(): FormulaEntity {
    return FormulaEntity(
        formulaId = id,
        groupOwnerId = groupId,
        name = name,
        expression = expression,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// GroupVariable mappings
fun GroupVariableEntity.toGroupVariable(): GroupVariable {
    return GroupVariable(
        id = variableId,
        groupId = groupOwnerId,
        name = name,
        value = value,
        description = description
    )
}

fun GroupVariable.toGroupVariableEntity(): GroupVariableEntity {
    return GroupVariableEntity(
        variableId = id,
        groupOwnerId = groupId,
        name = name,
        value = value,
        description = description
    )
}

// Parcelle mappings
fun ParcelleEntity.toParcelle(): Parcelle {
    return Parcelle(
        id = parcelleId,
        forestId = forestOwnerId,
        foretId = foretId,
        name = name,
        surfaceHa = surfaceHa,
        shape = shape,
        slopePct = slopePct,
        aspect = aspect,
        access = access,
        altitudeM = altitudeM,
        objectifType = objectifType,
        objectifVal = objectifVal,
        tolerancePct = tolerancePct,
        samplingMode = samplingMode,
        sampleAreaM2 = sampleAreaM2,
        targetSpeciesCsv = targetSpeciesCsv,
        srid = srid,
        remarks = remarks,
        codeInseeCommune = codeInseeCommune,
        nomCommune = nomCommune,
        sectionCadastrale = sectionCadastrale,
        numeroCadastral = numeroCadastral,
        contenanceCadastraleHa = contenanceCadastraleHa,
        geometrieIgnWkt = geometrieIgnWkt,
        natureCadastraleCode = natureCadastraleCode,
        localisationMode = localisationMode,
        codeSer = codeSer,
        nomSer = nomSer,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Parcelle.toParcelleEntity(): ParcelleEntity {
    return ParcelleEntity(
        parcelleId = id,
        forestOwnerId = forestId,
        foretId = foretId,
        name = name,
        surfaceHa = surfaceHa,
        shape = shape,
        slopePct = slopePct,
        aspect = aspect,
        access = access,
        altitudeM = altitudeM,
        objectifType = objectifType,
        objectifVal = objectifVal,
        tolerancePct = tolerancePct,
        samplingMode = samplingMode,
        sampleAreaM2 = sampleAreaM2,
        targetSpeciesCsv = targetSpeciesCsv,
        srid = srid,
        remarks = remarks,
        codeInseeCommune = codeInseeCommune,
        nomCommune = nomCommune,
        sectionCadastrale = sectionCadastrale,
        numeroCadastral = numeroCadastral,
        contenanceCadastraleHa = contenanceCadastraleHa,
        geometrieIgnWkt = geometrieIgnWkt,
        natureCadastraleCode = natureCadastraleCode,
        localisationMode = localisationMode,
        codeSer = codeSer,
        nomSer = nomSer,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// Placette mappings
fun PlacetteEntity.toPlacette(): Placette {
    return Placette(
        id = placetteId,
        parcelleId = parcelleOwnerId,
        name = name,
        type = type,
        rayonM = rayonM,
        surfaceM2 = surfaceM2,
        centerWkt = centerWkt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Placette.toPlacetteEntity(): PlacetteEntity {
    return PlacetteEntity(
        placetteId = id,
        parcelleOwnerId = parcelleId,
        name = name,
        type = type,
        rayonM = rayonM,
        surfaceM2 = surfaceM2,
        centerWkt = centerWkt,
        sessionId = null,
        typeReleve = null,
        referenceGpsWkt = null,
        azimutRef = null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// Essence mappings
fun EssenceEntity.toEssence(): Essence {
    return Essence(
        code = code,
        name = name,
        categorie = categorie,
        densiteBoite = densiteBoite,
        colorHex = colorHex,
        densiteBois = densiteBois,
        qualiteTypique = qualiteTypique,
        typeCoupePreferee = typeCoupePreferee,
        usageBois = usageBois,
        vitesseCroissance = vitesseCroissance,
        hauteurMaxM = hauteurMaxM,
        diametreMaxCm = diametreMaxCm,
        toleranceOmbre = toleranceOmbre,
        remarques = remarques
    )
}

fun Essence.toEssenceEntity(): EssenceEntity {
    return EssenceEntity(
        code = code,
        name = name,
        categorie = categorie,
        densiteBoite = densiteBoite,
        colorHex = colorHex,
        densiteBois = densiteBois,
        qualiteTypique = qualiteTypique,
        typeCoupePreferee = typeCoupePreferee,
        usageBois = usageBois,
        vitesseCroissance = vitesseCroissance,
        hauteurMaxM = hauteurMaxM,
        diametreMaxCm = diametreMaxCm,
        toleranceOmbre = toleranceOmbre,
        remarques = remarques
    )
}

// Tige mappings
fun TigeEntity.toTige(): Tige {
    return Tige(
        id = tigeId,
        parcelleId = parcelleOwnerId,
        placetteId = placetteOwnerId,
        essenceCode = essenceCode,
        diamCm = diamCm,
        hauteurM = hauteurM,
        gpsWkt = gpsWkt,
        precisionM = precisionM,
        altitudeM = altitudeM,
        timestamp = timestamp,
        note = note,
        produit = produit,
        fCoef = fCoef,
        valueEur = valueEur,
        numero = numero,
        categorie = categorie,
        qualite = qualite,
        defauts = defauts?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
        photoUri = photoUri,
        qualiteDetail = qualiteDetail,
        classeKraft = classeKraft,
        etatSanitaire = etatSanitaire,
        vigueur = vigueur,
        origine = origine,
        typeCoupe = typeCoupe,
        biomasseFusTonnes = biomasseFusTonnes,
        carboneFusTonnes = carboneFusTonnes,
        coefficientElancement = coefficientElancement,
        houppierM = houppierM,
        houppierPct = houppierPct,
        isTigeHabitat = isTigeHabitat,
        sessionId = sessionId
    )
}

fun Tige.toTigeEntity(): TigeEntity {
    return TigeEntity(
        tigeId = id,
        parcelleOwnerId = parcelleId,
        placetteOwnerId = placetteId,
        sessionId = sessionId,
        essenceCode = essenceCode,
        diamCm = diamCm,
        hauteurM = hauteurM,
        gpsWkt = gpsWkt,
        precisionM = precisionM,
        altitudeM = altitudeM,
        timestamp = timestamp,
        note = note,
        produit = produit,
        fCoef = fCoef,
        valueEur = valueEur,
        numero = numero,
        categorie = categorie,
        qualite = qualite,
        defauts = defauts?.joinToString(","),
        photoUri = photoUri,
        qualiteDetail = qualiteDetail,
        classeKraft = classeKraft,
        etatSanitaire = etatSanitaire,
        vigueur = vigueur,
        origine = origine,
        typeCoupe = typeCoupe,
        biomasseFusTonnes = biomasseFusTonnes,
        carboneFusTonnes = carboneFusTonnes,
        coefficientElancement = coefficientElancement,
        houppierM = houppierM,
        houppierPct = houppierPct,
        isTigeHabitat = isTigeHabitat
    )
}

// Parameter mappings
fun ParameterEntity.toParameterItem(): ParameterItem {
    return ParameterItem(
        key = key,
        valueJson = valueJson,
        updatedAt = updatedAt
    )
}

fun ParameterItem.toParameterEntity(): ParameterEntity {
    return ParameterEntity(
        key = key,
        valueJson = valueJson,
        updatedAt = updatedAt
    )
}
