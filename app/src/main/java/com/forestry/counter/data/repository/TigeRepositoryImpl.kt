package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.mapper.toTige
import com.forestry.counter.data.mapper.toTigeEntity
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TigeRepositoryImpl(
    private val tigeDao: TigeDao
) : TigeRepository {

    override fun getAllTiges(): Flow<List<Tige>> {
        return tigeDao.getAllTiges().map { list -> list.map { it.toTige() } }
    }

    override fun getTigesByParcelle(parcelleId: String): Flow<List<Tige>> {
        return tigeDao.getTigesByParcelle(parcelleId).map { list -> list.map { it.toTige() } }
    }

    override fun getTigesByPlacette(placetteId: String): Flow<List<Tige>> {
        return tigeDao.getTigesByPlacette(placetteId).map { list -> list.map { it.toTige() } }
    }

    override fun getTigeById(tigeId: String): Flow<Tige?> {
        return tigeDao.getTigeByIdFlow(tigeId).map { it?.toTige() }
    }

    override suspend fun insertTige(tige: Tige) {
        tigeDao.insertTige(tige.toTigeEntity())
    }

    override suspend fun insertTiges(tiges: List<Tige>) {
        tigeDao.insertTiges(tiges.map { it.toTigeEntity() })
    }

    override suspend fun updateTige(tige: Tige) {
        tigeDao.updateTige(tige.toTigeEntity())
    }

    override suspend fun deleteTige(tigeId: String) {
        tigeDao.deleteTigeById(tigeId)
    }

    override suspend fun deleteTigesByParcelle(parcelleId: String) {
        tigeDao.deleteTigesByParcelle(parcelleId)
    }

    override suspend fun deleteTigesByPlacette(placetteId: String) {
        tigeDao.deleteTigesByPlacette(placetteId)
    }

    override suspend fun deleteTigesByPlacetteAndEssence(placetteId: String, essenceCode: String) {
        tigeDao.deleteTigesByPlacetteAndEssence(placetteId, essenceCode)
    }

    override suspend fun deleteLatest(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double
    ): Boolean {
        val latest = tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm) ?: return false
        tigeDao.deleteTigeById(latest.tigeId)
        return true
    }

    override suspend fun updateLatestGPS(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double,
        wkt: String,
        precisionM: Double?,
        altitudeM: Double?
    ): Boolean {
        val latest = tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm) ?: return false
        tigeDao.updateTige(
            latest.copy(
                gpsWkt = wkt,
                precisionM = precisionM,
                altitudeM = altitudeM
            )
        )
        return true
    }

    override suspend fun updateLatestHeight(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double,
        heightM: Double?
    ): Boolean {
        val latest = tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm) ?: return false
        tigeDao.updateTige(
            latest.copy(
                hauteurM = heightM
            )
        )
        return true
    }

    override suspend fun setTigeHeight(tigeId: String, heightM: Double?): Boolean {
        val current = tigeDao.getTigeById(tigeId) ?: return false
        tigeDao.updateTige(current.copy(hauteurM = heightM))
        return true
    }

    override suspend fun setTigeGps(
        tigeId: String,
        wkt: String,
        precisionM: Double?,
        altitudeM: Double?
    ): Boolean {
        val current = tigeDao.getTigeById(tigeId) ?: return false
        tigeDao.updateTige(
            current.copy(
                gpsWkt = wkt,
                precisionM = precisionM,
                altitudeM = altitudeM
            )
        )
        return true
    }

    override suspend fun updateLatestCategory(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double,
        category: String?
    ): Boolean {
        val latest = tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm) ?: return false
        tigeDao.updateTige(latest.copy(categorie = category))
        return true
    }

    override suspend fun updateLatestQuality(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double,
        quality: Int?
    ): Boolean {
        val latest = tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm) ?: return false
        tigeDao.updateTige(latest.copy(qualite = quality))
        return true
    }

    override suspend fun updateLatestDefects(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double,
        defects: List<String>?
    ): Boolean {
        val latest = tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm) ?: return false
        val csv = defects?.joinToString(",")
        tigeDao.updateTige(latest.copy(defauts = csv))
        return true
    }

    override suspend fun updateLatestNumero(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double,
        numero: Int?
    ): Boolean {
        val latest = tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm) ?: return false
        tigeDao.updateTige(latest.copy(numero = numero))
        return true
    }

    override suspend fun updateLatestPhoto(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double,
        photoUri: String?
    ): Boolean {
        val latest = tigeDao.getLatestMatching(parcelleId, placetteId, essenceCode, diamCm) ?: return false
        tigeDao.updateTige(latest.copy(photoUri = photoUri))
        return true
    }

    override suspend fun updateTigeQuality(
        tigeId: String,
        qualite: Int?,
        produit: String?,
        qualiteDetail: String?
    ): Boolean {
        val current = tigeDao.getTigeById(tigeId) ?: return false
        tigeDao.updateTige(current.copy(qualite = qualite, produit = produit, qualiteDetail = qualiteDetail))
        return true
    }

    override suspend fun updateTigeSylviculture(
        tigeId: String,
        classeKraft: Int?,
        etatSanitaire: String?,
        vigueur: String?,
        origine: String?,
        isTigeHabitat: Boolean
    ): Boolean {
        val updated = tigeDao.updateTigeSylviculture(
            tigeId = tigeId,
            classeKraft = classeKraft,
            etatSanitaire = etatSanitaire,
            vigueur = vigueur,
            origine = origine,
            isTigeHabitat = isTigeHabitat
        )
        return updated > 0
    }
}
