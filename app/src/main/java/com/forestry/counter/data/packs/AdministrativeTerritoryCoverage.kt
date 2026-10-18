package com.forestry.counter.data.packs

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Relations territoriales du snapshot IGN embarqué, jamais une liste de packs publiés. */
internal object AdministrativeTerritoryCoverage {
    const val ASSET_PATH = "packs/departments-labels.geojson"
    private const val SHA256 = "109a9c4a409d918d5f5faa8c23bd873d94957665276e789087524cfac0e238f2"
    private const val MAX_BYTES = 128 * 1024

    fun read(input: InputStream): Map<String, Set<String>> {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Référentiel territorial trop volumineux" }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_BYTES && MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) } == SHA256) { "Référentiel territorial IGN non reconnu" }
        val collection = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(collection.getValue("type").jsonPrimitive.content == "FeatureCollection")
        val byRegion = linkedMapOf<String, MutableSet<String>>()
        val seen = mutableSetOf<String>()
        for (value in collection.getValue("features").jsonArray) {
            val feature = value.jsonObject
            val properties = feature.getValue("properties").jsonObject
            val department = properties.getValue("territory_id").jsonPrimitive.content
            val region = "region:${properties.getValue("region_code").jsonPrimitive.content}"
            require(department.startsWith("department:") && feature.getValue("id").jsonPrimitive.content == department && seen.add(department))
            byRegion.getOrPut(region) { linkedSetOf() }.add(department)
        }
        require(seen.size == 101 && byRegion.size == 18)
        return byRegion.mapValues { (_, departments) -> departments.toSet() }
    }
}
