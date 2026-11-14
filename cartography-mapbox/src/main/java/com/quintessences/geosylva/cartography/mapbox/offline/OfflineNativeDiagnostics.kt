package com.quintessences.geosylva.cartography.mapbox.offline

import java.util.Locale

/** Stable operation codes only: no resource IDs, URLs, native messages or account data. */
enum class OfflineNativeStage { UNKNOWN, ESTIMATE, STYLE, TILES, VALIDATE, REMOVE }

/** Allowlist mirrors SDK11.34.1 TileRegionErrorType/StylePackErrorType; unknown future values stay explicit. */
enum class OfflineNativeSdkCode {
    UNKNOWN, CANCELED, DOES_NOT_EXIST, TILESET_DESCRIPTOR, DISK_FULL, OTHER, TILE_COUNT_EXCEEDED, PARTIAL_LOAD,
}

/** Diagnostic hint only; it does not establish the root cause or alter retry/download behavior. */
enum class OfflineNativeHint { UNKNOWN, TLS, DNS, AUTH, TIMEOUT }

/** Fixed native validation categories. This is a diagnostic signal, not a conclusion about the requested resource. */
enum class OfflineNativeDetail {
    UNKNOWN, URI_JSON_INVALID, TILEJSON_EMPTY, RASTER_LAYERS_INVALID, TILE_VARIANT_INVALID, DESCRIPTOR_RESOLUTION,
}

/** Compatible no-argument API; the exception retains only safe enums and has no native cause/message. */
class OfflineNativeException(
    val stage: OfflineNativeStage = OfflineNativeStage.UNKNOWN,
    val sdkCode: OfflineNativeSdkCode = OfflineNativeSdkCode.UNKNOWN,
    val hint: OfflineNativeHint = OfflineNativeHint.UNKNOWN,
    val detail: OfflineNativeDetail = OfflineNativeDetail.UNKNOWN,
) : RuntimeException(if (stage == OfflineNativeStage.UNKNOWN && sdkCode == OfflineNativeSdkCode.UNKNOWN && hint == OfflineNativeHint.UNKNOWN && detail == OfflineNativeDetail.UNKNOWN)
    "Offline map resources unavailable"
else "Offline map resources unavailable [stage=${stage.name}, sdk=${sdkCode.name}, hint=${hint.name}, detail=${detail.name}]")

/** Native text is inspected transiently and never retained, logged, exposed as a cause or included in a returned value. */
internal fun offlineNativeFailure(stage: OfflineNativeStage, nativeTypeName: String, nativeMessage: String?): OfflineNativeException =
    classifyOfflineNativeFailure(stage,nativeTypeName,nativeMessage)

/** Native callback boundary, also reusable by opt-in SDK TEST probes. Only fixed enums escape; no text/cause is retained. */
fun classifyOfflineNativeFailure(stage: OfflineNativeStage, nativeTypeName: String, nativeMessage: String?): OfflineNativeException {
    val code = OfflineNativeSdkCode.entries.firstOrNull { it.name == nativeTypeName } ?: OfflineNativeSdkCode.UNKNOWN
    val canClassify = code == OfflineNativeSdkCode.OTHER || code == OfflineNativeSdkCode.TILESET_DESCRIPTOR
    return OfflineNativeException(stage,code,
        if (canClassify) offlineNativeHint(nativeMessage) else OfflineNativeHint.UNKNOWN,
        if (canClassify) offlineNativeDetail(nativeMessage) else OfflineNativeDetail.UNKNOWN)
}

/** Match only known SDK11.34.1 static validation prefixes; never return a fragment of a native message. */
internal fun offlineNativeDetail(nativeMessage: String?): OfflineNativeDetail {
    val text = nativeMessage.orEmpty().lowercase(Locale.ROOT)
    return when {
        "invalid tileset in the tileset descriptoroptions" in text -> OfflineNativeDetail.URI_JSON_INVALID
        "unexpectedly empty tilejson" in text -> OfflineNativeDetail.TILEJSON_EMPTY
        "no valid raster layers were found in tilejson" in text -> OfflineNativeDetail.RASTER_LAYERS_INVALID
        "invalid tileset descriptor, tile variant" in text && "is invalid" in text -> OfflineNativeDetail.TILE_VARIANT_INVALID
        "failed to resolve tileset descriptors" in text -> OfflineNativeDetail.DESCRIPTOR_RESOLUTION
        else -> OfflineNativeDetail.UNKNOWN
    }
}

private val authStatus = Regex("""\b(?:http|status)(?:\s+(?:response|status|code)){0,3}\s*[:=]?\s*(401|403)\b""",RegexOption.IGNORE_CASE)

/** Deliberately returns an enum, never a sanitized fragment which could accidentally preserve a credential. */
internal fun offlineNativeHint(nativeMessage: String?): OfflineNativeHint {
    val text = nativeMessage.orEmpty().lowercase(Locale.ROOT)
    return when {
        listOf("ssl","tls","certificate","certpath","handshake").any(text::contains) -> OfflineNativeHint.TLS
        listOf("unknownhost","unknown host","name resolution","could not resolve","unable to resolve","dns").any(text::contains) -> OfflineNativeHint.DNS
        listOf("unauthorized","forbidden","access denied","invalid token","authentication failed").any(text::contains) || authStatus.containsMatchIn(text) -> OfflineNativeHint.AUTH
        listOf("timeout","timed out","deadline exceeded").any(text::contains) -> OfflineNativeHint.TIMEOUT
        else -> OfflineNativeHint.UNKNOWN
    }
}
