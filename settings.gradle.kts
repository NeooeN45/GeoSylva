pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://api.mapbox.com/downloads/v2/releases/maven") {
            content { includeGroupByRegex("com\\.mapbox.*") }
        }
    }
}

rootProject.name = "GéoSylva"
include(":app")
include(":dem_pack")
include(":capsule-verifier")

// Moteur commun et hôte de recette isolés ; :app ne dépend pas encore de Mapbox.
include(":cartography-mapbox")
include(":cartography-preview")
include(":cartography-ui")
