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

/** Compatible no-argument API; the exception retains only safe enums and has no native cause/message. */
class OfflineNativeException(
    val stage: OfflineNativeStage = OfflineNativeStage.UNKNOWN,
    val sdkCode: OfflineNativeSdkCode = OfflineNativeSdkCode.UNKNOWN,
    val hint: OfflineNativeHint = OfflineNativeHint.UNKNOWN,
) : RuntimeException(if (stage == OfflineNativeStage.UNKNOWN && sdkCode == OfflineNativeSdkCode.UNKNOWN && hint == OfflineNativeHint.UNKNOWN)
    "Offline map resources unavailable"
else "Offline map resources unavailable [stage=${stage.name}, sdk=${sdkCode.name}, hint=${hint.name}]")

/** Native text is inspected transiently and never retained, logged, exposed as a cause or included in a returned value. */
internal fun offlineNativeFailure(stage: OfflineNativeStage, nativeTypeName: String, nativeMessage: String?): OfflineNativeException {
    val code = OfflineNativeSdkCode.entries.firstOrNull { it.name == nativeTypeName } ?: OfflineNativeSdkCode.UNKNOWN
    val hint = if (code == OfflineNativeSdkCode.OTHER || code == OfflineNativeSdkCode.TILESET_DESCRIPTOR)
        offlineNativeHint(nativeMessage) else OfflineNativeHint.UNKNOWN
    return OfflineNativeException(stage,code,hint)
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
