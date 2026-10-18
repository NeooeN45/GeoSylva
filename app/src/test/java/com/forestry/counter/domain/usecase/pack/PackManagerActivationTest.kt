package com.forestry.counter.domain.usecase.pack

import android.content.Context
import android.content.SharedPreferences
import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackFeatures
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.domain.model.pack.PackMetaInfo
import com.forestry.counter.domain.model.pack.PackStatus
import com.forestry.counter.domain.model.pack.TerritorialContext
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class PackManagerActivationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun should_resolve_active_local_version_without_promising_a_catalogue_update() = runBlocking {
        val files = temporary.newFolder("files")
        activateOldVersion(files)
        val manager = PackManager(context(files))
        val catalogue = manager.packState.value.allPacks.single { it.id == "fr.region.75" }
        assertEquals("0.9.0", catalogue.version)
        assertEquals("0.9.0", catalogue.installedVersion)
        assertEquals(PackStatus.INSTALLED, catalogue.status)
        assertEquals("0.9.0", manager.packState.value.installed.single { it.id == catalogue.id }.version)
        assertTrue(manager.isFeatureEnabled("previous_rule", catalogue.id))

        try {
            manager.installPack(catalogue.id)
            fail("The current catalogue has no downloadable distribution")
        } catch (_: IllegalStateException) {
            assertEquals("0.9.0", manager.packState.value.installed.single { it.id == catalogue.id }.version)
        }
        val restarted = PackManager(context(files))
        val resolved = PackResolver.resolveActivePacks(
            TerritorialContext("75", "17", emptyList(), PackLevel.SOCLE_NATIONAL),
            restarted.packState.value.installed
        )
        assertEquals("0.9.0", resolved.single { it.id == catalogue.id }.version)
        assertEquals(6L, restarted.packState.value.diskUsageBytes)
    }

    @Test
    fun should_deactivate_and_keep_file_bytes_without_reactivating_legacy_preferences() = runBlocking {
        val files = temporary.newFolder("files")
        activateOldVersion(files)
        val legacy = mapOf("installed_pack_ids" to "[\"fr.region.75\"]", "pack_versions" to "{\"fr.region.75\":\"0.9.0\"}")
        val manager = PackManager(context(files, legacy))
        manager.uninstallPack("fr.region.75")

        val restarted = PackManager(context(files, legacy))
        assertFalse(restarted.packState.value.installed.any { it.id == "fr.region.75" })
        assertEquals(6L, restarted.storedSizeBytes())
        assertEquals(0f, restarted.installedSizeMb())
        assertTrue(File(files, "packs/fr.region.75/0.9.0.pack").isFile)
    }

    @Test
    fun should_migrate_legacy_local_files_without_inventing_catalogue_data() {
        val files = temporary.newFolder("files")
        File(files, "packs/fr.region.75/0.9.0.pack").apply { parentFile.mkdirs(); writeText("legacy") }
        val legacy = mapOf("installed_pack_ids" to "[\"fr.region.75\",\"fr.region.76\"]", "pack_versions" to "{\"fr.region.75\":\"0.9.0\",\"fr.region.76\":\"1.0.0\"}")
        val state = PackManager(context(files, legacy)).packState.value

        assertEquals(listOf("fr.region.75"), state.activePacks.map { it.id })
        assertEquals("0.9.0", state.activePacks.single().version)
        assertEquals("", state.activePacks.single().buildDate)
        assertEquals(null, state.activePacks.single().downloadUrl)
        assertEquals(null, state.activePacks.single().checksum)
        assertEquals(PackFeatures(), state.activePacks.single().features)
        assertEquals("Inconnu", state.activePacks.single().metaInfo.author)
        assertTrue(File(files, "packs/installations.json").isFile)
        assertTrue(File(files, "packs/fr.region.75/0.9.0.pack").isFile)
    }

    @Test
    fun should_report_corrupt_registry_without_reactivating_legacy_or_hiding_file_size() {
        val files = temporary.newFolder("files")
        File(files, "packs/fr.region.75/0.9.0.pack").apply { parentFile.mkdirs(); writeText("legacy") }
        File(files, "packs/installations.json").writeText("not-json")
        val legacy = mapOf("installed_pack_ids" to "[\"fr.region.75\"]", "pack_versions" to "{\"fr.region.75\":\"0.9.0\"}")
        val state = PackManager(context(files, legacy)).packState.value

        assertTrue(state.activePacks.isEmpty())
        assertTrue(state.lastError?.contains("illisible") == true)
        assertEquals(6L, state.diskUsageBytes)
        assertEquals("not-json", File(files, "packs/installations.json").readText())
    }

    @Test
    fun should_reactivate_retained_version_offline_after_deactivation_and_restart() = runBlocking {
        val files = temporary.newFolder("files")
        activateOldVersion(files)
        PackManager(context(files)).uninstallPack("fr.region.75")
        val restarted = PackManager(context(files))
        assertEquals("0.9.0", restarted.packState.value.retainedPacks.single().version)
        assertTrue(restarted.packState.value.activePacks.isEmpty())

        restarted.reactivatePack("fr.region.75", "0.9.0")
        assertEquals("0.9.0", restarted.packState.value.activePacks.single().version)
        assertTrue(restarted.isFeatureEnabled("previous_rule", "fr.region.75"))
        assertEquals("active", File(files, "packs/fr.region.75/0.9.0.pack").readText())
    }

    @Test
    fun should_refuse_corrupt_retained_file_and_keep_it_inactive() = runBlocking {
        val files = temporary.newFolder("files")
        activateOldVersion(files)
        val manager = PackManager(context(files))
        manager.uninstallPack("fr.region.75")
        File(files, "packs/fr.region.75/0.9.0.pack").writeText("altered")

        try {
            manager.reactivatePack("fr.region.75", "0.9.0")
            fail("Corrupt local version should not be reactivated")
        } catch (_: java.io.IOException) {
            assertTrue(manager.packState.value.activePacks.isEmpty())
            assertTrue(PackManager(context(files)).packState.value.activePacks.isEmpty())
        }
    }

    @Test
    fun should_show_no_fictional_regional_publication_on_a_new_installation() {
        val state = PackManager(context(temporary.newFolder("files"))).packState.value

        assertTrue(PackResolver.REGIONAL_CATALOG.isEmpty())
        assertTrue(state.allPacks.none { it.level == PackLevel.REGIONAL || it.level == PackLevel.DEPARTEMENTAL })
        assertTrue(state.available.isEmpty())
        assertTrue(state.activePacks.isEmpty())
        assertTrue(state.retainedPacks.isEmpty())
        assertEquals(PackFeatures(), PackResolver.EMBEDDED_NATIONAL_PACK.features)
        assertEquals("", PackResolver.EMBEDDED_NATIONAL_PACK.version)
        assertEquals("", PackResolver.EMBEDDED_NATIONAL_PACK.buildDate)
    }

    @Test
    fun should_preserve_a_legacy_department_file_without_any_catalogue_entry() {
        val files = temporary.newFolder("files")
        File(files, "packs/fr.dept.17/2025.pack").apply { parentFile.mkdirs(); writeText("local-department") }
        val legacy = mapOf(
            "installed_pack_ids" to "[\"fr.dept.17\"]",
            "pack_versions" to "{\"fr.dept.17\":\"2025\"}"
        )
        val manager = PackManager(context(files, legacy))
        val active = manager.packState.value.activePacks.single()

        assertEquals("fr.dept.17", active.id)
        assertEquals(PackLevel.DEPARTEMENTAL, active.level)
        assertEquals("2025", active.version)
        assertEquals(PackFeatures(), active.features)
        assertEquals(null, active.checksum)
        assertTrue(File(files, "packs/fr.dept.17/2025.pack").isFile)
        assertEquals(active, PackManager(context(files)).packState.value.retainedPacks.single())
    }

    private fun activateOldVersion(files: File) {
        val packDirectory = File(files, "packs").apply { mkdirs() }
        val contents = "active"
        // Explicit JVM fixture, never part of the application catalogue.
        val descriptor = GeoPackDescriptor(
            id = "fr.region.75", level = PackLevel.REGIONAL,
            name = "Test local pack", codeINSEE = "75", buildDate = "2026-10",
            sizeKb = 1L, status = PackStatus.AVAILABLE, features = PackFeatures(),
            version = "0.9.0",
            checksum = MessageDigest.getInstance("SHA-256").digest(contents.toByteArray()).joinToString("") { "%02x".format(it) },
            metaInfo = PackMetaInfo(featureFlags = mapOf("previous_rule" to true))
        )
        PackInstallationStore(packDirectory).activate(descriptor, File(packDirectory, "download.part").apply { writeText(contents) })
    }

    private fun context(files: File, legacy: Map<String, String> = emptyMap()): Context {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(any(), any()) } answers { legacy[firstArg()] ?: secondArg() }
        return mockk<Context> {
            every { filesDir } returns files
            every { getSharedPreferences("geosylva_packs", Context.MODE_PRIVATE) } returns preferences
        }
    }
}
