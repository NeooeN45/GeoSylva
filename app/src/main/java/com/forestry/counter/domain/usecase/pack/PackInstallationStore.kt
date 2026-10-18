package com.forestry.counter.domain.usecase.pack

import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackFeatures
import com.forestry.counter.domain.model.pack.PackMetaInfo
import com.forestry.counter.domain.model.pack.PackStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Référence locale de la version active, indépendante du catalogue GSIE.
 *
 * Un fichier vérifié est placé avant l'écriture atomique du registre. Une coupure
 * entre ces étapes laisse un fichier conservé mais n'active jamais un pack fictif.
 * Les anciennes versions restent sur disque pour les résultats qui les citent.
 * Aucune extraction ni validation de format métier n'est simulée par ce store.
 */
internal class PackInstallationStore(private val directory: File) {
    private val stateFile = File(directory, "installations.json")
    private val monitor = monitors.computeIfAbsent(stateFile.absolutePath) { Any() }
    private val json = Json { ignoreUnknownKeys = true }

    fun hasRegistry(): Boolean = stateFile.isFile

    @Serializable
    private data class Registry(
        val formatVersion: Int = 1,
        val installations: List<GeoPackDescriptor> = emptyList(),
        val retainedVersions: List<GeoPackDescriptor> = emptyList()
    )

    fun activePacks(): List<GeoPackDescriptor> = synchronized(monitor) {
        readRegistry().installations.filter { fileFor(it.id, it.version).isFile }
    }

    /** Descripteurs historiques disponibles avec leur fichier local, même désactivés. */
    fun retainedPacks(): List<GeoPackDescriptor> = synchronized(monitor) {
        readRegistry().retainedVersions.filter { fileFor(it.id, it.version).isFile }
    }

    fun getRetained(packId: String, version: String): GeoPackDescriptor? = synchronized(monitor) {
        readRegistry().retainedVersions.find { it.id == packId && it.version == version }
    }

    /** Migration unique : une version explicite ET son fichier doivent exister. */
    fun migrateLegacy(
        installedIds: Set<String>,
        versions: Map<String, String>,
        catalogue: List<GeoPackDescriptor>
    ) = synchronized(monitor) {
        if (stateFile.exists()) return@synchronized
        val migrated = catalogue.mapNotNull { descriptor ->
            if (descriptor.id !in installedIds) return@mapNotNull null
            val version = versions[descriptor.id] ?: return@mapNotNull null
            val file = runCatching { fileFor(descriptor.id, version) }.getOrNull()
            if (file?.isFile != true) return@mapNotNull null
            val metadataKnown = version == descriptor.version
            descriptor.copy(
                version = version,
                installedVersion = version,
                status = PackStatus.INSTALLED,
                // Le catalogue actuel ne décrit pas forcément l'ancien contenu.
                checksum = descriptor.checksum.takeIf { metadataKnown },
                buildDate = descriptor.buildDate.takeIf { metadataKnown }.orEmpty(),
                downloadUrl = descriptor.downloadUrl.takeIf { metadataKnown },
                contentKind = descriptor.contentKind.takeIf { metadataKnown },
                archiveBytes = descriptor.archiveBytes.takeIf { metadataKnown },
                territoryId = descriptor.territoryId.takeIf { metadataKnown },
                signedManifest = descriptor.signedManifest.takeIf { metadataKnown },
                sizeKb = file.length() / 1024L + if (file.length() % 1024L == 0L) 0L else 1L,
                features = if (metadataKnown) descriptor.features else PackFeatures(),
                metaInfo = if (metadataKnown) descriptor.metaInfo else PackMetaInfo(
                    author = "Inconnu",
                    license = "Inconnue",
                    minAppVersion = "",
                    notes = "Version locale héritée ; métadonnées historiques indisponibles"
                )
            )
        }
        writeRegistry(Registry(installations = migrated, retainedVersions = migrated))
    }

    fun fileFor(packId: String, version: String): File {
        requireSafeComponent(packId)
        requireSafeComponent(version)
        return File(directory, "$packId/$version.pack")
    }

    fun activate(
        descriptor: GeoPackDescriptor,
        verifiedDownload: File,
        checkCancellation: () -> Unit = {}
    ) = synchronized(monitor) {
        checkCancellation()
        val expectedSha = descriptor.checksum?.trim()?.lowercase()
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            ?: throw IOException("Checksum SHA-256 absent du pack ${descriptor.id}")
        val previous = readRegistry()
        val retained = previous.retainedVersions.find { it.id == descriptor.id && it.version == descriptor.version }
        if (retained?.checksum != null && retained.checksum.trim().lowercase() != expectedSha) {
            throw IOException("Identité de version différente du descripteur conservé")
        }
        if (!verifiedDownload.isFile || sha256(verifiedDownload, checkCancellation) != expectedSha) {
            throw IOException("Fichier vérifié absent ou altéré pour ${descriptor.id}")
        }
        if (descriptor.archiveBytes != null && verifiedDownload.length() != descriptor.archiveBytes) {
            throw IOException("Taille du fichier différente du descripteur signé")
        }
        val target = fileFor(descriptor.id, descriptor.version)
        val parent = target.parentFile ?: throw IOException("Stockage du pack inaccessible")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Impossible de créer le stockage du pack ${descriptor.id}")
        }
        if (target.isFile && sha256(target, checkCancellation) != expectedSha) {
            throw IOException("La version ${descriptor.version} existe avec un contenu différent")
        }
        checkCancellation()
        val sameFile = verifiedDownload.canonicalFile == target.canonicalFile
        if (target.isFile) {
            if (!sameFile) verifiedDownload.delete()
        } else {
            // Le downloader peut avoir fermé son flux sans synchronisation disque.
            FileOutputStream(verifiedDownload, true).use { it.fd.sync() }
            Files.move(verifiedDownload.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        // Pour une version connue, conserve ses métadonnées historiques immuables.
        // Un ancien descripteur sans SHA peut acquérir uniquement le SHA vérifié.
        val active = (retained ?: descriptor).copy(
            status = PackStatus.INSTALLED, installedVersion = descriptor.version,
            checksum = retained?.checksum ?: expectedSha
        )
        val historical = previous.retainedVersions.filterNot { it.id == active.id && it.version == active.version } + active
        writeRegistry(previous.copy(
            installations = previous.installations.filterNot { it.id == active.id } + active,
            retainedVersions = historical
        ))
        // Le marqueur de reprise n'a plus de fragment associé après activation.
        if (!sameFile) File(verifiedDownload.parentFile, "${verifiedDownload.name}.etag").delete()
    }

    /** Retire l'activation ; conserve fichiers et références historiques. */
    fun deactivate(packId: String) = synchronized(monitor) {
        val previous = readRegistry()
        writeRegistry(previous.copy(installations = previous.installations.filterNot { it.id == packId }))
    }

    fun activeSizeBytes(): Long = synchronized(monitor) {
        readRegistry().installations.sumOf { fileFor(it.id, it.version).takeIf(File::isFile)?.length() ?: 0L }
    }

    fun storedSizeBytes(): Long = synchronized(monitor) {
        if (!directory.isDirectory) return@synchronized 0L
        directory.walkTopDown().filter { it.isFile && (it.name.endsWith(".pack") || it.name.endsWith(".part")) }
            .sumOf(File::length)
    }

    private fun readRegistry(): Registry {
        if (!stateFile.exists()) return Registry()
        val registry = try {
            json.decodeFromString<Registry>(stateFile.readText())
        } catch (error: Exception) {
            throw IOException("Registre local des packs illisible", error)
        }
        if (registry.formatVersion != 1 || registry.installations.map { it.id }.distinct().size != registry.installations.size) {
            throw IOException("Registre local des packs incompatible")
        }
        if (registry.retainedVersions.map { it.id to it.version }.distinct().size != registry.retainedVersions.size) {
            throw IOException("Versions historiques du registre incompatibles")
        }
        (registry.installations + registry.retainedVersions).forEach { fileFor(it.id, it.version) }
        // Lecture compatible du premier registre local, avant ajout de l'historique.
        val historical = registry.retainedVersions + registry.installations.filter { active ->
            registry.retainedVersions.none { it.id == active.id && it.version == active.version }
        }
        return registry.copy(retainedVersions = historical)
    }

    private fun writeRegistry(registry: Registry) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Stockage des packs inaccessible")
        val temporary = File(directory, "installations.json.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.encodeToString(registry).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun sha256(file: File, checkCancellation: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun requireSafeComponent(value: String) {
        require(value.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]*")) && value != "." && value != "..") {
            "Identifiant ou version de pack invalide"
        }
    }

    companion object {
        private val monitors = ConcurrentHashMap<String, Any>()
    }
}
