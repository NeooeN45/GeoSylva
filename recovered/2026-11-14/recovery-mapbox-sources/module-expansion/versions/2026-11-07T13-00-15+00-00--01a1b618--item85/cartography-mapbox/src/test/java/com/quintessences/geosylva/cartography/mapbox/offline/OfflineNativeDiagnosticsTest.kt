package com.quintessences.geosylva.cartography.mapbox.offline

import org.junit.Assert.*
import org.junit.Test

class OfflineNativeDiagnosticsTest {
    private val nativeUrl = "https://TEST-host.invalid/TEST-private-resource?access_token=TEST_SECRET_NATIVE_TOKEN"
    @Test fun `default constructor remains compatible and carries no native cause`() {
        val error=OfflineNativeException()
        assertEquals("Offline map resources unavailable",error.message)
        assertEquals(OfflineNativeStage.UNKNOWN,error.stage);assertEquals(OfflineNativeSdkCode.UNKNOWN,error.sdkCode)
        assertEquals(OfflineNativeHint.UNKNOWN,error.hint);assertNull(error.cause);assertTrue(error.suppressed.isEmpty())
    }
    @Test fun `safe SDK enum codes and operation phase are preserved without accepting arbitrary type strings`() {
        for (name in listOf("CANCELED","DOES_NOT_EXIST","TILESET_DESCRIPTOR","DISK_FULL","OTHER","TILE_COUNT_EXCEEDED","PARTIAL_LOAD")) {
            val error=offlineNativeFailure(OfflineNativeStage.ESTIMATE,name,null)
            assertEquals(name,error.sdkCode.name);assertEquals(OfflineNativeStage.ESTIMATE,error.stage)
        }
        for (stage in listOf(OfflineNativeStage.STYLE,OfflineNativeStage.TILES,OfflineNativeStage.VALIDATE,OfflineNativeStage.REMOVE)) {
            assertEquals(stage,offlineNativeFailure(stage,"OTHER",null).stage)
        }
        val unknown=offlineNativeFailure(OfflineNativeStage.ESTIMATE,nativeUrl,nativeUrl)
        assertEquals(OfflineNativeSdkCode.UNKNOWN,unknown.sdkCode);assertEquals(OfflineNativeHint.UNKNOWN,unknown.hint)
        assertNoNativeText(unknown)
    }
    @Test fun `opaque descriptor or OTHER messages yield fixed transport hints without retaining URLs`() {
        val messages=mapOf(
            "SSL certificate verify failed: $nativeUrl" to OfflineNativeHint.TLS,
            "UnknownHostException resolving $nativeUrl" to OfflineNativeHint.DNS,
            "HTTP response code: 401 requesting $nativeUrl" to OfflineNativeHint.AUTH,
            "HTTP status code=403 requesting $nativeUrl" to OfflineNativeHint.AUTH,
            "request timed out at $nativeUrl" to OfflineNativeHint.TIMEOUT,
            "resource failure at $nativeUrl" to OfflineNativeHint.UNKNOWN)
        for (code in listOf("OTHER","TILESET_DESCRIPTOR")) for ((text,expected) in messages) {
            val error=offlineNativeFailure(OfflineNativeStage.ESTIMATE,code,text)
            assertEquals(expected,error.hint);assertEquals(code,error.sdkCode.name);assertNoNativeText(error)
        }
    }
    @Test fun `concrete non-network SDK errors are never reclassified by incidental native text`() {
        val error=offlineNativeFailure(OfflineNativeStage.TILES,"DISK_FULL","TLS timeout at $nativeUrl")
        assertEquals(OfflineNativeSdkCode.DISK_FULL,error.sdkCode);assertEquals(OfflineNativeHint.UNKNOWN,error.hint)
        assertNoNativeText(error)
        assertEquals(OfflineNativeHint.UNKNOWN,offlineNativeHint(null))
        assertEquals(OfflineNativeHint.UNKNOWN,offlineNativeHint("resource id TEST-4010 had status pending"))
    }
    private fun assertNoNativeText(error: OfflineNativeException) {
        val rendered=error.stackTraceToString()
        assertFalse(rendered.contains("TEST-host.invalid"));assertFalse(rendered.contains("TEST-private-resource"))
        assertFalse(rendered.contains("TEST_SECRET_NATIVE_TOKEN"));assertFalse(rendered.contains("access_token"))
        assertFalse(rendered.contains("https://"));assertNull(error.cause);assertTrue(error.suppressed.isEmpty())
    }
}
