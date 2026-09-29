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
        versionCode = 11
        versionName = "3.0.0"

        // Stable by default: wall-clock values here invalidate every incremental build
        // and make two builds of the same source produce different artifacts.
        val buildId = providers.gradleProperty("geosylva.buildId")
            .orElse(providers.environmentVariable("GEOSYLVA_BUILD_ID"))
            .orElse("dev")
            .get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val buildTimestampMs = providers.gradleProperty("geosylva.buildTimestampMs")
            .orElse(providers.environmentVariable("GEOSYLVA_BUILD_TIMESTAMP_MS"))
            .orElse("0")
            .get().toLongOrNull() ?: error("geosylva.buildTimestampMs must be an integer")
        buildConfigField("Long", "BUILD_TIMESTAMP", "${buildTimestampMs}L")
        buildConfigField("String", "BUILD_ID", "\"$buildId\"")

        // Clé API MapTiler (tuiles vectorielles + terrain 3D)
        // Récupérée depuis local.properties ou variable d'environnement
        val localPropsFile = rootProject.file("local.properties")
        val localProps = Properties()
        if (localPropsFile.exists()) {
            localProps.load(StringReader(localPropsFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")))
        }
        val maptilerKey = localProps.getProperty("MAPTILER_KEY")
            ?: System.getenv("MAPTILER_KEY")
            ?: ""
        buildConfigField("String", "MAPTILER_KEY", "\"$maptilerKey\"")

        fun buildConfigString(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        val gsieApiBaseUrl = localProps.getProperty("GSIE_API_BASE_URL")
            ?: System.getenv("GSIE_API_BASE_URL")
            ?: ""
        val googleWebClientId = localProps.getProperty("GOOGLE_WEB_CLIENT_ID")
            ?: System.getenv("GOOGLE_WEB_CLIENT_ID")
            ?: ""
        buildConfigField(
            "String",
            "GSIE_API_BASE_URL",
            "\"${buildConfigString(gsieApiBaseUrl)}\""
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${buildConfigString(googleWebClientId)}\""
        )

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

    // Expose les schemas JSON Room (app/schemas/) aux tests instrumentés
    // afin que MigrationTestHelper puisse créer une DB à une version antérieure.
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

val APP_VERSION_METADATA_FILE = rootProject.file("docs/APP_VERSION.md")
val ROOM_DATABASE_SOURCE_FILE =
    file("src/main/java/com/forestry/counter/data/local/ForestryDatabase.kt")
val ROOM_DATABASE_VERSION_PATTERN =
    Regex("""@Database\s*\([\s\S]*?\bversion\s*=\s*(\d+)\b""")
val APP_VERSION_NAME = android.defaultConfig.versionName
    ?: throw org.gradle.api.GradleException("La versionName Android doit être définie.")
val APP_VERSION_CODE = android.defaultConfig.versionCode
    ?: throw org.gradle.api.GradleException("Le versionCode Android doit être défini.")

fun renderVersionMetadata(): String {
    val roomDatabaseVersion = ROOM_DATABASE_VERSION_PATTERN
        .find(ROOM_DATABASE_SOURCE_FILE.readText(Charsets.UTF_8))
        ?.groupValues
        ?.getOrNull(1)
        ?: throw org.gradle.api.GradleException(
            "Impossible de détecter la version Room dans ${ROOM_DATABASE_SOURCE_FILE.path}."
        )
    return """
        # Métadonnées de version GeoSylva

        > Fichier généré par `:app:generateVersionMetadata`. Ne pas modifier manuellement.

        | Élément | Valeur | Source |
        |---|---|---|
        | Version applicative (`versionName`) | `$APP_VERSION_NAME` | `app/build.gradle.kts` |
        | Code applicatif (`versionCode`) | `$APP_VERSION_CODE` | `app/build.gradle.kts` |
        | Schéma Room | `$roomDatabaseVersion` | `app/src/main/java/com/forestry/counter/data/local/ForestryDatabase.kt` |
    """.trimIndent() + "\n"
}

fun normalizeLineEndings(value: String): String =
    value.replace("\r\n", "\n").replace("\r", "\n")

tasks.register("generateVersionMetadata") {
    inputs.property("versionName", APP_VERSION_NAME)
    inputs.property("versionCode", APP_VERSION_CODE)
    inputs.file(ROOM_DATABASE_SOURCE_FILE)
    outputs.file(APP_VERSION_METADATA_FILE)
    doLast {
        APP_VERSION_METADATA_FILE.writeText(renderVersionMetadata(), Charsets.UTF_8)
    }
}

tasks.register("verifyVersionMetadata") {
    inputs.property("versionName", APP_VERSION_NAME)
    inputs.property("versionCode", APP_VERSION_CODE)
    inputs.file(ROOM_DATABASE_SOURCE_FILE)
    doLast {
        val actual = if (APP_VERSION_METADATA_FILE.isFile) {
            normalizeLineEndings(APP_VERSION_METADATA_FILE.readText(Charsets.UTF_8))
        } else {
            null
        }
        if (actual != renderVersionMetadata()) {
            throw org.gradle.api.GradleException(
                "docs/APP_VERSION.md est absent ou périmé. " +
                    "Exécuter ./gradlew :app:generateVersionMetadata."
            )
        }
    }
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
    implementation(libs.coil.compose)
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

    // Note — la vidéo de l'écran de connexion n'utilise aucune dépendance :
    // `TextureView` + `MediaPlayer` de la plateforme suffisent pour une boucle
    // locale et muette. Voir `presentation/components/VideoBackdrop.kt`.

    // OkHttp for HTTP calls (price sync)
    implementation(libs.okhttp)

    // Security dependencies
    // SQLCipher 4.5.4 — chiffrement DB (RGPD compliance, mandatory Phase 0)
    implementation(libs.sqlcipher)
    // AndroidX Security for encrypted file storage
    implementation(libs.security.crypto)

    // Identité Quintessences : contrat GSIE + connexion Google officielle
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.bundles.android.testing)
    // MigrationTestHelper (tests de migration Room instrumentés)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
