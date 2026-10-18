package com.forestry.counter.data.service

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Checks only the installed target APK manifest. No service start, location injection or database access. */
@RunWith(AndroidJUnit4::class)
class MartelageGpsManifestTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun service() = context.packageManager.getServiceInfo(
        ComponentName(context, MartelageGpsService::class.java), 0,
    )
    @Suppress("DEPRECATION")
    private fun permissions() = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty().toSet()

    @Test fun installedGpsServiceIsPrivateAndEnabled() {
        val info = service()
        assertEquals(context.packageName, info.packageName)
        assertEquals(MartelageGpsService::class.java.name, info.name)
        assertTrue(info.enabled)
        assertFalse(info.exported)
    }

    @Test fun installedApkDeclaresForegroundLocationAndNotificationPermissions() {
        val names = permissions()
        for (permission in listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE,
            "android.permission.FOREGROUND_SERVICE_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
        )) assertTrue("Missing manifest permission: $permission", permission in names)
    }

    @Test fun installedServiceHasLocationForegroundTypeIncludingApi28ManifestProof() {
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue(service().foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0)
        } else {
            // API 28 does not expose ServiceInfo.foregroundServiceType; inspect the actual compiled XML.
            var found = false
            context.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "manifest") {
                        assertEquals(context.packageName, parser.getAttributeValue(null, "package"))
                    }
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "service") {
                        val name = parser.getAttributeValue(ANDROID, "name")
                        if (name == MartelageGpsService::class.java.name || name == ".data.service.MartelageGpsService") {
                            // Android framework location foreground-service flag is 0x8.
                            assertTrue(parser.getAttributeIntValue(ANDROID, "foregroundServiceType", 0) and 0x8 != 0)
                            assertNotNull(parser.getAttributeValue(ANDROID, "exported"))
                            assertFalse(parser.getAttributeBooleanValue(ANDROID, "exported", true))
                            found = true
                        }
                    }
                    parser.next()
                }
            }
            assertTrue("Location service must exist in the target APK manifest", found)
        }
    }

    @Test fun foregroundTrackingDoesNotRequestAnUnrelatedBackgroundLocationGrant() {
        assertFalse("android.permission.ACCESS_BACKGROUND_LOCATION" in permissions())
    }

    companion object { private const val ANDROID = "http://schemas.android.com/apk/res/android" }
}
