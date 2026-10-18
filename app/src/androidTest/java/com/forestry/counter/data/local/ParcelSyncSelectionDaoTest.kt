package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.entity.ParcelSyncEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Transactions réelles Room/SQLite sur une base de test exclusivement en mémoire.
 * Toutes les parcelles et identités sont des fixtures synthétiques. Ces tests ne
 * qualifient ni SQLCipher, ni une migration, ni l'atomicité d'une déconnexion.
 */
@RunWith(AndroidJUnit4::class)
class ParcelSyncSelectionDaoTest {
    private lateinit var database: ForestryDatabase
    private lateinit var queue: ParcelSyncDao
    private lateinit var queryExecutor: ExecutorService
    private lateinit var transactionExecutor: ExecutorService

    @Before
    fun setup() {
        queryExecutor = Executors.newFixedThreadPool(2)
        transactionExecutor = Executors.newFixedThreadPool(2)
        // Le contexte du paquet de test ne demande ni la base pilote ni une clé.
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            ForestryDatabase::class.java,
        )
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .build()
        queue = database.parcelSyncDao()
    }

    @After
    fun teardown() {
        database.close()
        queryExecutor.shutdownNow()
        transactionExecutor.shutdownNow()
    }

    @Test
    fun reconfirmer_preserve_conflit_et_operation_en_attente() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            for (state in listOf("CONFLICT", "PENDING")) {
                val parcelId = "fixture-parcelle-$state"
                database.parcelleDao().insertParcelle(parcel(parcelId))
                val existing = row(ACCOUNT_A, parcelId, "fixture-operation-existante-$state")
                    .copy(
                        state = state,
                        serverVersion = 7,
                        retryCount = 3,
                        lastAttemptAt = 120L,
                        lastSuccessAt = 90L,
                        nextAttemptAt = 500L,
                        lastErrorCode = "FIXTURE_EXISTANTE",
                    )
                queue.upsert(existing)

                val inserted = queue.insertSelectedIfUnbound(
                    listOf(row(ACCOUNT_A, parcelId, "fixture-operation-nouvelle-$state")),
                )

                assertEquals(0, inserted)
                assertEquals(existing, queue.get(ACCOUNT_A, parcelId))
                assertEquals(listOf(ACCOUNT_A), queue.getAccountIdsForParcel(parcelId))
            }
        }
    }

    @Test
    fun confirmations_concurrentes_de_comptes_distincts_ne_creent_qu_un_lien() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            database.parcelleDao().insertParcelle(parcel())
            val start = CompletableDeferred<Unit>()
            val confirmations = listOf(ACCOUNT_A, ACCOUNT_B).map { accountId ->
                async(Dispatchers.Default) {
                    start.await()
                    accountId to runCatching {
                        queue.insertSelectedIfUnbound(listOf(row(accountId)))
                    }
                }
            }
            start.complete(Unit)
            val results = confirmations.awaitAll()
            val winner = results.single { it.second.isSuccess }
            val loser = results.single { it.second.isFailure }

            assertEquals(1, winner.second.getOrThrow())
            assertTrue(loser.second.exceptionOrNull() is IllegalStateException)
            assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", loser.second.exceptionOrNull()?.message)
            assertEquals(listOf(winner.first), queue.getAccountIdsForParcel(PARCEL_ID))
            assertEquals(row(winner.first), queue.get(winner.first, PARCEL_ID))
            assertNull(queue.get(loser.first, PARCEL_ID))
        }
    }

    @Test
    fun fusion_du_compte_b_bloque_confirmation_a_jusqua_commit_puis_la_refuse() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val original = parcel()
            val pulled = original.copy(name = "Fixture reçue du compte B", updatedAt = 200L)
            database.parcelleDao().insertParcelle(original)
            val parcelWritten = CompletableDeferred<Unit>()
            val allowCommit = CompletableDeferred<Unit>()
            val pullB = async(Dispatchers.Default) {
                queue.withParcelMergeTransaction {
                    database.parcelleDao().updateParcelle(pulled)
                    parcelWritten.complete(Unit)
                    // La confirmation A commence pendant l'intervalle écriture–liaison.
                    allowCommit.await()
                    queue.upsert(row(ACCOUNT_B).copy(state = "SYNCED", serverVersion = 4))
                }
            }
            parcelWritten.await()
            val confirmationA = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { queue.insertSelectedIfUnbound(listOf(row(ACCOUNT_A))) }
            }

            try {
                val prematureCompletion = withTimeoutOrNull(BLOCKED_OBSERVATION_MS) {
                    confirmationA.await()
                }
                assertNull("La confirmation A doit attendre la transaction de fusion B", prematureCompletion)
                assertFalse(confirmationA.isCompleted)
            } finally {
                allowCommit.complete(Unit)
            }
            pullB.await()
            val refused = confirmationA.await()

            assertTrue(refused.exceptionOrNull() is IllegalStateException)
            assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", refused.exceptionOrNull()?.message)
            assertEquals(pulled, database.parcelleDao().getParcelleById(PARCEL_ID))
            assertEquals(listOf(ACCOUNT_B), queue.getAccountIdsForParcel(PARCEL_ID))
            assertEquals(row(ACCOUNT_B).copy(state = "SYNCED", serverVersion = 4), queue.get(ACCOUNT_B, PARCEL_ID))
            assertNull(queue.get(ACCOUNT_A, PARCEL_ID))
        }
    }

    @Test
    fun echec_callback_annule_ecriture_parcelle_et_liaison_dans_la_meme_transaction() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val original = parcel()
            database.parcelleDao().insertParcelle(original)
            val expected = IllegalStateException("FIXTURE_ECHEC_FUSION")

            val failure = runCatching {
                queue.withParcelMergeTransaction {
                    database.parcelleDao().updateParcelle(
                        original.copy(name = "Fixture à annuler", updatedAt = 200L),
                    )
                    queue.upsert(row(ACCOUNT_B))
                    throw expected
                }
            }.exceptionOrNull()

            assertSame(expected, failure)
            assertEquals(original, database.parcelleDao().getParcelleById(PARCEL_ID))
            assertTrue(queue.getAccountIdsForParcel(PARCEL_ID).isEmpty())
            assertNull(queue.get(ACCOUNT_B, PARCEL_ID))
        }
    }

    private fun row(
        accountId: String,
        parcelId: String = PARCEL_ID,
        operationId: String = "fixture-operation-$accountId",
    ) = ParcelSyncEntity(
        accountId = accountId,
        parcelId = parcelId,
        operation = "UPSERT",
        operationId = operationId,
        state = "PENDING",
        serverVersion = null,
        retryCount = 0,
        queuedAt = 100L,
        lastAttemptAt = null,
        lastSuccessAt = null,
        nextAttemptAt = 100L,
        lastErrorCode = null,
    )

    private fun parcel(parcelId: String = PARCEL_ID) = ParcelleEntity(
        parcelleId = parcelId,
        forestOwnerId = null,
        foretId = null,
        name = "Parcelle synthétique de test",
        surfaceHa = null,
        shape = null,
        slopePct = null,
        aspect = null,
        access = null,
        altitudeM = null,
        objectifType = null,
        objectifVal = null,
        tolerancePct = null,
        samplingMode = null,
        sampleAreaM2 = null,
        targetSpeciesCsv = null,
        srid = null,
        remarks = null,
        codeInseeCommune = null,
        nomCommune = null,
        sectionCadastrale = null,
        numeroCadastral = null,
        contenanceCadastraleHa = null,
        geometrieIgnWkt = null,
        natureCadastraleCode = null,
        localisationMode = null,
        codeSer = null,
        nomSer = null,
        createdAt = 100L,
        updatedAt = 100L,
    )

    private companion object {
        const val ACCOUNT_A = "fixture-compte-A"
        const val ACCOUNT_B = "fixture-compte-B"
        const val PARCEL_ID = "fixture-parcelle-partagee"
        const val TEST_TIMEOUT_MS = 10_000L
        const val BLOCKED_OBSERVATION_MS = 250L
    }
}
