import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

configurations.all {
    exclude(group = "org.apache.logging.log4j", module = "log4j-api")
}

android {
    namespace = "com.forestry.counter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.forestry.counter"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.3.0"

        val buildId = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        buildConfigField("String", "BUILD_ID", "\"$buildId\"")
        buildConfigField("Long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    val hasReleaseKeystore = keystorePropertiesFile.exists().also { exists ->
        if (exists) {
            val text = keystorePropertiesFile
                .readText(Charsets.UTF_8)
                .removePrefix("\uFEFF")
            keystoreProperties.load(StringReader(text))
        }
    } && listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }

    // Asset Pack pour les tuiles DEM SRTM (offline elevation data)
    assetPacks += ":dem_pack"

}

/*
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}
*/

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)

    // CameraX — viseur caméra AR pour mesure des hauteurs
    implementation(libs.bundles.camera)

    // Navigation
    implementation(libs.navigation.compose)

    // AppCompat for runtime locale changes (AppCompatDelegate)
    implementation(libs.appcompat)

    // DocumentFile for SAF directory access
    implementation(libs.documentfile)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.bundles.datastore)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // CSV Parsing
    implementation(libs.opencsv)

    // Excel (Apache POI - lite version for Android)
    implementation(libs.poi) {
        exclude(group = "org.apache.logging.log4j", module = "log4j-api")
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }
    implementation(libs.poi.ooxml) {
        exclude(group = "org.apache.logging.log4j", module = "log4j-api")
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }
    implementation(libs.poi.ooxml.lite) {
        exclude(group = "org.apache.logging.log4j", module = "log4j-api")
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }

    // Expression Parser
    implementation(libs.exp4j)

    // Core library desugaring (support newer Java APIs on older Android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // WorkManager for scheduled backups
    implementation(libs.work.runtime.ktx)

    // Location (Fused Location Provider)
    implementation(libs.play.services.location)

    // BlurView for backdrop blur (Android 12+ optimized)
    implementation(libs.blurview)

    // MapLibre GL (Map mode)
    implementation(libs.maplibre)

    // OkHttp for HTTP calls (price sync)
    implementation(libs.okhttp)

    // Security dependencies
    // SQLCipher 4.5.4 — chiffrement DB (RGPD compliance, mandatory Phase 0)
    implementation(libs.sqlcipher)
    // AndroidX Security for encrypted file storage
    implementation(libs.security.crypto)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.bundles.android.testing)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
