package com.forestry.counter.data.packs

import com.forestry.counter.capsule.json.CanonicalJson
import com.forestry.counter.capsule.json.StrictJsonParser
import com.forestry.counter.domain.model.pack.*
import com.forestry.counter.domain.usecase.pack.PackResolver
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.Locale
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Artefact réel IGN construit par Python ; seules les clés restent dédiées aux tests. */
class AdministrativePackContractTest {
    @get:Rule val temporary = TemporaryFolder()
    private val json = Json
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/packs-interop/$name")) { "Fixture absente : $name" }.use { it.readBytes() }
    private fun signed(): SignedManifestDto = json.decodeFromString(resource("manifest.json").toString(Charsets.UTF_8))
    private fun contract() = AdministrativePackContract(Base64.getEncoder().encodeToString(resource("signing-public-key.der")))
    private fun artifact() = temporary.newFile().apply { writeBytes(resource("artifact.pack")) }
    private fun fails(block: () -> Unit) { assertTrue("L'entrée doit être refusée", runCatching(block).isFailure) }

    @Test fun pythonSignedIgnArtifactCanBeReadWithoutEmbeddedMapFallback() {
        val descriptor = contract().descriptor(signed().metadata(), "https://gsie.example/api/v1/packs/artifact")
        val content = contract().read(artifact(), descriptor)
        assertEquals("department:16", content.territoryId)
        assertEquals("Licence Ouverte / Etalab 2.0", content.licence)
        assertEquals(1, json.parseToJsonElement(content.boundariesJson).jsonObject.getValue("features").jsonArray.size)
        assertTrue(content.boundariesJson.contains("Charente"))
        assertEquals(29352L, descriptor.archiveBytes)
    }
    @Test fun realAsciiInseeCodeIsAcceptedWhenReaderStartsUnderArabicLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            // Nouvelle initialisation statique du lecteur, indépendante de l'ordre des tests.
            val readerName = AdministrativePackContract::class.java.name
            val isolated = object : ClassLoader(javaClass.classLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (!name.startsWith(readerName)) return super.loadClass(name, resolve)
                    synchronized(this) {
                        val loaded = findLoadedClass(name) ?: requireNotNull(parent.getResourceAsStream(name.replace('.', '/') + ".class")).use {
                            val bytes = it.readBytes(); defineClass(name, bytes, 0, bytes.size)
                        }
                        if (resolve) resolveClass(loaded)
                        return loaded
                    }
                }
            }
            val type = isolated.loadClass(readerName)
            val reader = type.getConstructor(String::class.java).newInstance(Base64.getEncoder().encodeToString(resource("signing-public-key.der")))
            val manifest = type.getMethod("verify", SignedPackMetadata::class.java).invoke(reader, signed().metadata()) as AdministrativeManifest
            assertEquals("department:16", manifest.territoryId)
        } finally { Locale.setDefault(previous) }
    }
    @Test fun missingTrustedKeyNeverFallsBackToAServerKey() {
        assertFalse(AdministrativePackContract("").isConfigured)
        fails { AdministrativePackContract("").verify(signed().metadata()) }
    }
    @Test fun tamperedSignedNameIsRejected() {
        val metadata = signed().metadata()
        fails { contract().verify(metadata.copy(manifestJson = metadata.manifestJson.replace("Charente", "Gard"))) }
    }
    @Test fun differentTrustedKeyIsRejected() {
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        fails { AdministrativePackContract(Base64.getEncoder().encodeToString(key.public.encoded)).verify(signed().metadata()) }
    }
    @Test fun signedExternalDownloadPathIsRejected() {
        val (newContract, metadata) = resignManifest { this["download_path"] = JsonPrimitive("https://external.example/artifact") }
        fails { newContract.verify(metadata) }
    }
    @Test fun signedUnsupportedSourceIsRejected() {
        val (newContract, metadata) = resignManifest { this["source_id"] = JsonPrimitive("unqualified-source") }
        fails { newContract.verify(metadata) }
    }
    @Test fun alteredFileDoesNotActivateWithAnUnchangedSignedManifest() {
        val file = artifact()
        file.writeBytes(file.readBytes().also { it[20] = (it[20].toInt() xor 1).toByte() })
        fails { contract().read(file, contract().descriptor(signed().metadata(), null)) }
    }
    @Test fun descriptorCannotChangeSignedIdentityOrSize() {
        val descriptor = contract().descriptor(signed().metadata(), null)
        fails { contract().read(artifact(), descriptor.copy(archiveBytes = requireNotNull(descriptor.archiveBytes) + 1)) }
        fails { contract().read(artifact(), descriptor.copy(territoryId = "department:30")) }
    }
    @Test fun signedArtifactWithDifferentTerritoryIsRejected() {
        malformedArchive { this["territory_id"] = JsonPrimitive("department:30") }
    }
    @Test fun signedArtifactWithUnqualifiedProvenanceIsRejected() {
        malformedArchive {
            this["provenance"] = JsonObject(getValue("provenance").jsonObject.toMutableMap().apply { this["licence"] = JsonPrimitive("unknown") })
        }
    }
    @Test fun signedArtifactWithAnUnclosedRingIsRejected() {
        malformedArchive {
            val collection = getValue("boundaries").jsonObject.toMutableMap()
            val feature = collection.getValue("features").jsonArray.single().jsonObject.toMutableMap()
            feature["geometry"] = json.parseToJsonElement("""{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,2]]]}""")
            collection["features"] = JsonArray(listOf(JsonObject(feature)))
            this["boundaries"] = JsonObject(collection)
        }
    }
    @Test fun signedArtifactWithLabelOutsideContourIsRejected() {
        malformedArchive {
            val collection = getValue("labels").jsonObject.toMutableMap()
            val feature = collection.getValue("features").jsonArray.single().jsonObject.toMutableMap()
            feature["geometry"] = json.parseToJsonElement("""{"type":"Point","coordinates":[170,80]}""")
            collection["features"] = JsonArray(listOf(JsonObject(feature)))
            this["labels"] = JsonObject(collection)
        }
    }
    @Test fun geographicPacksNeverBecomeForestRuleSources() {
        val descriptor = contract().descriptor(signed().metadata(), null).copy(status = PackStatus.INSTALLED)
        val active = PackResolver.resolveActivePacks(TerritorialContext("75", "16", emptyList(), PackLevel.SOCLE_NATIONAL), listOf(descriptor))
        assertEquals(listOf(PackResolver.EMBEDDED_NATIONAL_PACK), active)
        val result = PackResolver.resolve(listOf(descriptor), getter = { "forest-rule" }, default = "fallback")
        assertEquals("fallback", result.value)
    }
    @Test fun atomicCacheRoundTripAndCorruptCacheAreDistinctFromInstallations() {
        val file = File(temporary.newFolder(), "catalog.json")
        val cache = SignedPackCatalogCache(file)
        cache.write(listOf(signed().metadata()))
        assertEquals(signed().metadata(), cache.read().single())
        file.writeText("{broken")
        fails { cache.read() }
        assertTrue(file.exists())
    }

    private fun resignManifest(change: MutableMap<String, JsonElement>.() -> Unit): Pair<AdministrativePackContract, SignedPackMetadata> {
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val values = json.parseToJsonElement(signed().manifestJson).jsonObject.toMutableMap().apply(change)
        val bytes = CanonicalJson.encode(StrictJsonParser.parse(JsonObject(values).toString().toByteArray()))
        val signature = Signature.getInstance("Ed25519").run { initSign(key.private); update(bytes); sign() }
        val keyId = sha(key.public.encoded)
        return AdministrativePackContract(Base64.getEncoder().encodeToString(key.public.encoded)) to
            SignedPackMetadata(bytes.toString(Charsets.UTF_8), Base64.getEncoder().encodeToString(signature), keyId)
    }
    private fun malformedArchive(change: MutableMap<String, JsonElement>.() -> Unit) {
        val values = json.parseToJsonElement(resource("artifact.pack").toString(Charsets.UTF_8)).jsonObject.toMutableMap().apply(change)
        val bytes = JsonObject(values).toString().toByteArray()
        val file = temporary.newFile().apply { writeBytes(bytes) }
        val (newContract, metadata) = resignManifest {
            this["archive_bytes"] = JsonPrimitive(bytes.size)
            this["sha256"] = JsonPrimitive(sha(bytes))
        }
        fails { newContract.read(file, newContract.descriptor(metadata, null)) }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
