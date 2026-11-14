package com.forestry.counter.domain.usecase.privacy

import android.content.Context
import com.forestry.counter.data.local.dao.AlerteSanitaireDao
import com.forestry.counter.data.local.dao.ArbreHabitatDao
import com.forestry.counter.data.local.dao.DiagnosticSylvicoleDao
import com.forestry.counter.data.local.dao.ForetDao
import com.forestry.counter.data.local.dao.IbpEvaluationDao
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.ObservationFloreDao
import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.dao.ParcelSyncOperationDao
import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.dao.RipisylveDao
import com.forestry.counter.data.local.dao.StationDao
import com.forestry.counter.data.local.dao.TigeDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Use case centralisant le droit à l'effacement RGPD (Art. 17).
 *
 * Supprime définitivement toutes les données forestières contenant des PII
 * (forêts, parcelles, placettes, tiges, sessions, stations, ripisylves,
 * évaluations IBP, arbres habitat, diagnostics sylvicoles, alertes sanitaires,
 * observations flore) ainsi que les photos associées stockées sur l'appareil.
 *
 * Les données de référence (essences, tarifs, paramètres) sont conservées
 * car elles ne contiennent pas de PII et sont nécessaires au fonctionnement
 * de l'application.
 */
class DeleteAllUserDataUseCase(
    private val foretDao: ForetDao,
    private val parcelleDao: ParcelleDao,
    private val tigeDao: TigeDao,
    private val placetteDao: PlacetteDao,
    private val inventaireSessionDao: InventaireSessionDao,
    private val stationDao: StationDao,
    private val ripisylveDao: RipisylveDao,
    private val ibpEvaluationDao: IbpEvaluationDao,
    private val arbreHabitatDao: ArbreHabitatDao,
    private val diagnosticSylvicoleDao: DiagnosticSylvicoleDao,
    private val alerteSanitaireDao: AlerteSanitaireDao,
    private val observationFloreDao: ObservationFloreDao,
    private val context: Context,
    private val operationDao: ParcelSyncOperationDao,
    private val syncDao: ParcelSyncDao,
    private val hierarchySyncDao: com.forestry.counter.data.local.dao.HierarchySyncDao,
    private val woodObjectDao: com.forestry.counter.data.local.dao.WoodObjectDao,
    private val woodDataGate: com.forestry.counter.data.repository.WoodDataAccessGate,
    private val woodDepotDao: com.forestry.counter.data.local.dao.WoodDepotDao,
    private val fieldDao: com.forestry.counter.data.local.dao.FieldDao,
    private val cartographicMeasurementDao: com.forestry.counter.data.local.dao.CartographicMeasurementDao,
    private val clearHeightReferenceCaptures: suspend () -> Unit = {},
) {

    /**
     * Supprime toutes les données utilisateur de la base et les photos
     * associées. Retourne le nombre total d'opérations de suppression
     * effectuées (utile pour les tests et le logging).
     *
     * Les 11 entités cœur métier disposent désormais du soft delete
     * (Vague C) : leur méthode `deleteAll` ne fait qu'un marquage logique
     * (`deletedAt`). Pour le droit à l'effacement RGPD — qui exige une
     * suppression physique — on appelle donc `hardDeleteAll`.
     *
     * `IbpEvaluationDao` n'a pas de colonne `deletedAt` (hors périmètre
     * Vague C) : son `deleteAll` reste une suppression physique `DELETE FROM`,
     * il n'y a donc pas de `hardDeleteAll` à appeler.
     */
    suspend fun execute(): Int = withContext(Dispatchers.IO) { woodDataGate.purge {
        // The only authorized physical removal of retained operation snapshots.
        // Pending deletes and retries survive ordinary business deletes and logout.
        operationDao.runJournalTransaction {
            cartographicMeasurementDao.deleteAll()
            cartographicMeasurementDao.deleteStoreIdentity()
            fieldDao.deleteChunks()
            fieldDao.deletePhotos()
            fieldDao.deleteDrafts()
            fieldDao.deleteReceiptCommands()
            fieldDao.deleteReceiptCredits()
            fieldDao.deleteReceiptVersions()
            fieldDao.deleteReceipts()
            fieldDao.deleteEntries()
            fieldDao.deleteLinks()
            fieldDao.deleteSites()
            operationDao.hardDeleteAll()
            syncDao.hardDeleteAll()
            hierarchySyncDao.hardDeleteOperations()
            hierarchySyncDao.hardDeleteAssociations()
            woodObjectDao.deleteCommands()
            woodObjectDao.deleteSelections()
            woodObjectDao.deleteObservations()
            woodObjectDao.deleteObjects()
            woodDepotDao.hardDeleteAll()
            foretDao.hardDeleteAll()
            parcelleDao.hardDeleteAll()
            tigeDao.hardDeleteAll()
            placetteDao.hardDeleteAll()
            inventaireSessionDao.hardDeleteAll()
            stationDao.hardDeleteAll()
            ripisylveDao.hardDeleteAll()
            ibpEvaluationDao.deleteAll()
            arbreHabitatDao.hardDeleteAll()
            diagnosticSylvicoleDao.hardDeleteAll()
            alerteSanitaireDao.hardDeleteAll()
            observationFloreDao.hardDeleteAll()
        }
        clearHeightReferenceCaptures()
        deleteAllPhotos()
        val captures = File(context.cacheDir, "field_capture")
        check(!captures.exists() || captures.deleteRecursively()) { "LOCAL_CAPTURE_PURGE_FAILED" }
        val reports = File(context.filesDir, "martelage_reports")
        check(!reports.exists() || reports.deleteRecursively()) { "LOCAL_REPORT_PURGE_FAILED" }
        val backups = listOfNotNull(File(context.filesDir, "backups"), context.getExternalFilesDir("backups"))
        backups.forEach { directory ->
            check(!directory.exists() || directory.deleteRecursively()) { "LOCAL_BACKUP_PURGE_FAILED" }
        }
        DAO_COUNT
    } }

    /**
     * Supprime récursivement le dossier des photos de diagnostic.
     * Les photos sont stockées dans getExternalFilesDir(null)/photos.
     */
    private fun deleteAllPhotos() {
        val externalDirectory = context.getExternalFilesDir(null) ?: return
        val photosDir = File(externalDirectory, PHOTOS_DIR_NAME)
        check(!photosDir.exists() || photosDir.deleteRecursively()) { "LOCAL_PHOTO_PURGE_FAILED" }
    }

    companion object {
        /** Nombre de DAOs purgés par le use case (pour vérification en test). */
        const val DAO_COUNT = 19
        private const val PHOTOS_DIR_NAME = "photos"
    }
}
