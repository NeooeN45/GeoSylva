import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.quintessences.geosylva.cartographypreview"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.forestry.counter.preview.mapbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "3.0.0-test-mapbox"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Seul un jeton public d'affichage est admissible dans cette APK de recette.
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.isFile) {
            localProperties.load(StringReader(localPropertiesFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")))
        }
        val publicToken = providers.gradleProperty("geosylva.mapboxPublicToken")
            .orElse(providers.environmentVariable("MAPBOX_PUBLIC_ACCESS_TOKEN"))
            .orElse(localProperties.getProperty("MAPBOX_PUBLIC_ACCESS_TOKEN", ""))
            .orElse("").get()
        require(publicToken.isEmpty() || (publicToken.startsWith("pk.") && publicToken.none { it.isWhitespace() || it == '\"' || it == '\\' })) {
            "Mapbox preview expects an empty value or a public pk token; configure it outside the repository."
        }
        buildConfigField("String", "MAPBOX_PUBLIC_ACCESS_TOKEN", "\"$publicToken\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Hôte interne uniquement : aucun artefact release de recette à publier.
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enable = false } }

dependencies {
    implementation(project(":cartography-mapbox"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bundles.compose)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation(libs.mapbox.maps.android.ndk27)
}
