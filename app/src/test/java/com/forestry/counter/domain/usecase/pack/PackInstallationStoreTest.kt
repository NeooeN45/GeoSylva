package com.forestry.counter.domain.usecase.pack

import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackFeatures
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.domain.model.pack.PackMetaInfo
import com.forestry.counter.domain.model.pack.PackStatus
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PackInstallationStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun should_reactivate_legacy_checksum_with_uppercase_hexadecimal_without_network() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        val descriptor = pack("A", "first").let { it.copy(checksum = requireNotNull(it.checksum).uppercase()) }
        val localFile = store.fileFor(descriptor.id, descriptor.version)
        requireNotNull(localFile.parentFile).mkdirs()
        localFile.writeText("first")
        store.migrateLegacy(setOf(descriptor.id), mapOf(descriptor.id to "A"), listOf(descriptor))
        store.deactivate(descriptor.id)

        val retained = requireNotNull(PackInstallationStore(directory).getRetained(descriptor.id, "A"))
        store.activate(retained, localFile)

        assertEquals("A", PackInstallationStore(directory).activePacks().single().version)
        assertEquals("first", localFile.readText())
    }

    @Test
    fun should_keep_active_version_and_historical_file_after_update_and_restart() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        val first = pack("A", "first", feature = true)
        store.activate(first, downloaded(directory, "first"))
        val second = pack("B", "second", feature = false)
        store.activate(second, downloaded(directory, "second"))

        val restarted = PackInstallationStore(directory)
        assertEquals("B", restarted.activePacks().single().version)
        assertFalse(restarted.activePacks().single().metaInfo.featureFlags.getValue("diagnostic"))
        assertEquals("first", restarted.fileFor(first.id, "A").readText())
        assertEquals(11L, restarted.storedSizeBytes())
        assertEquals(6L, restarted.activeSizeBytes())
    }

    @Test
    fun should_keep_A_active_when_B_fails_checksum_verification() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        store.activate(pack("A", "first"), downloaded(directory, "first"))
        expectIOException { store.activate(pack("B", "second"), downloaded(directory, "altered")) }

        assertEquals("A", PackInstallationStore(directory).activePacks().single().version)
        assertFalse(store.fileFor("fr.region.75", "B").exists())
    }

    @Test
    fun should_keep_A_active_when_registry_write_fails_after_B_file_is_saved() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        store.activate(pack("A", "first"), downloaded(directory, "first"))
        assertTrue(File(directory, "installations.json.tmp").mkdir())
        expectIOException { store.activate(pack("B", "second"), downloaded(directory, "second")) }

        assertEquals("A", PackInstallationStore(directory).activePacks().single().version)
        assertEquals("second", store.fileFor("fr.region.75", "B").readText())
    }

    @Test
    fun should_reject_replacing_historical_version_with_different_content() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        store.activate(pack("A", "first"), downloaded(directory, "first"))
        store.activate(pack("B", "second"), downloaded(directory, "second"))
        expectIOException { store.activate(pack("A", "changed"), downloaded(directory, "changed")) }

        assertEquals("B", store.activePacks().single().version)
        assertEquals("first", store.fileFor("fr.region.75", "A").readText())
    }

    @Test
    fun should_cancel_activation_without_deleting_fragment_or_active_version() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        store.activate(pack("A", "first"), downloaded(directory, "first"))
        val fragment = downloaded(directory, "second")
        try {
            store.activate(pack("B", "second"), fragment) { throw CancellationException("pause") }
            fail("Activation should be cancelled")
        } catch (_: CancellationException) {
            assertEquals("second", fragment.readText())
            assertEquals("A", store.activePacks().single().version)
        }
    }

    @Test
    fun should_migrate_legacy_version_only_when_its_file_exists_and_not_invent_features() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        val catalogue = listOf(pack("B", "second", feature = true), pack("B", "other", id = "fr.region.76"))
        store.fileFor("fr.region.75", "A").apply { parentFile.mkdirs(); writeText("first") }
        store.migrateLegacy(setOf("fr.region.75", "fr.region.76"), mapOf("fr.region.75" to "A", "fr.region.76" to "B"), catalogue)

        val migrated = store.activePacks().single()
        assertEquals("A", migrated.version)
        assertEquals("A", migrated.installedVersion)
        assertEquals(PackFeatures(), migrated.features)
        assertTrue(migrated.metaInfo.featureFlags.isEmpty())
        assertEquals("", migrated.buildDate)
        assertEquals(null, migrated.downloadUrl)
        assertEquals(1L, migrated.sizeKb)
        assertEquals("Inconnu", migrated.metaInfo.author)
        assertEquals("Inconnue", migrated.metaInfo.license)
        assertEquals("first", store.fileFor(migrated.id, "A").readText())
        store.deactivate(migrated.id)
        store.migrateLegacy(setOf(migrated.id), mapOf(migrated.id to "A"), catalogue)
        assertTrue(store.activePacks().isEmpty())
    }

    @Test
    fun should_deactivate_without_deleting_historical_or_active_files() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        store.activate(pack("A", "first"), downloaded(directory, "first"))
        store.deactivate("fr.region.75")

        assertTrue(PackInstallationStore(directory).activePacks().isEmpty())
        assertEquals(0L, store.activeSizeBytes())
        assertEquals(5L, store.storedSizeBytes())
    }

    @Test
    fun should_not_mark_a_deleted_file_active_after_restart() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        store.activate(pack("A", "first"), downloaded(directory, "first"))
        assertTrue(store.fileFor("fr.region.75", "A").delete())
        assertTrue(PackInstallationStore(directory).activePacks().isEmpty())
    }

    @Test
    fun should_recover_metadata_of_A_and_B_after_update_deactivation_and_restart() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        val first = pack("A", "first", feature = true)
        val second = pack("B", "second", feature = false)
        store.activate(first, downloaded(directory, "first"))
        store.activate(second, downloaded(directory, "second"))
        store.deactivate(first.id)

        val restarted = PackInstallationStore(directory)
        assertTrue(restarted.activePacks().isEmpty())
        assertEquals(setOf("A", "B"), restarted.retainedPacks().map { it.version }.toSet())
        val old = restarted.getRetained(first.id, "A") ?: error("A metadata missing")
        val newer = restarted.getRetained(second.id, "B") ?: error("B metadata missing")
        assertEquals(first.checksum, old.checksum)
        assertEquals(first.features, old.features)
        assertEquals(first.metaInfo, old.metaInfo)
        assertEquals(second.checksum, newer.checksum)
        assertEquals(second.features, newer.features)
        assertEquals(second.metaInfo, newer.metaInfo)
    }

    @Test
    fun should_reactivate_from_existing_target_without_deleting_it_or_mutating_old_metadata() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        val first = pack("A", "first", feature = true)
        store.activate(first, downloaded(directory, "first"))
        store.deactivate(first.id)
        val existing = store.fileFor(first.id, first.version)
        store.activate(first.copy(features = PackFeatures(), buildDate = "changed"), existing)

        assertEquals("first", existing.readText())
        val reactivated = PackInstallationStore(directory).activePacks().single()
        assertEquals(first.features, reactivated.features)
        assertEquals(first.buildDate, reactivated.buildDate)
    }

    @Test
    fun should_keep_metadata_but_refuse_reactivation_when_retained_file_is_corrupt() {
        val directory = temporary.newFolder("packs")
        val store = PackInstallationStore(directory)
        val first = pack("A", "first")
        store.activate(first, downloaded(directory, "first"))
        store.deactivate(first.id)
        val file = store.fileFor(first.id, "A")
        file.writeText("altered")
        expectIOException { store.activate(store.getRetained(first.id, "A") ?: error("Missing metadata"), file) }

        val restarted = PackInstallationStore(directory)
        assertTrue(restarted.activePacks().isEmpty())
        assertEquals(first.checksum, restarted.getRetained(first.id, "A")?.checksum)
        assertEquals("altered", file.readText())
    }

    @Test
    fun should_preserve_both_activations_when_two_store_instances_write_concurrently() {
        val directory = temporary.newFolder("packs")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val firstFile = downloaded(directory, "first", "first.part")
            val secondFile = downloaded(directory, "second", "second.part")
            val first = executor.submit {
                start.await()
                PackInstallationStore(directory).activate(pack("A", "first"), firstFile)
            }
            val second = executor.submit {
                start.await()
                PackInstallationStore(directory).activate(pack("B", "second", id = "fr.region.76"), secondFile)
            }
            start.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertEquals(setOf("fr.region.75", "fr.region.76"), PackInstallationStore(directory).activePacks().map { it.id }.toSet())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun pack(version: String, contents: String, feature: Boolean = true, id: String = "fr.region.75") = GeoPackDescriptor(
        id = id, level = PackLevel.REGIONAL, name = "Test", codeINSEE = "75", version = version,
        buildDate = "2026-10", sizeKb = 999L, status = PackStatus.AVAILABLE,
        checksum = MessageDigest.getInstance("SHA-256").digest(contents.toByteArray()).joinToString("") { "%02x".format(it) },
        features = PackFeatures(hasFloraDatabase = feature), metaInfo = PackMetaInfo(featureFlags = mapOf("diagnostic" to feature))
    )

    private fun downloaded(directory: File, contents: String, name: String = "download.part") = File(directory, name).apply { writeText(contents) }

    private fun expectIOException(action: () -> Unit) {
        try {
            action()
            fail("Expected IOException")
        } catch (_: IOException) {
            // Expected I/O failure keeps the previous durable activation.
        }
    }
}
