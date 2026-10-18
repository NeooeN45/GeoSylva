plugins {
    kotlin("jvm") version "1.9.23"
}

repositories { mavenCentral() }
kotlin { jvmToolchain(17) }

// Compile the real application sources and their tests. This focused check does
// not build Android, validate Room migrations, or exercise UI/server adapters.
val domainPaths = listOf(
    "com/forestry/counter/domain/journey/**",
    "com/forestry/counter/domain/wood/**",
    "com/forestry/counter/domain/calculation/reception/**",
)
sourceSets {
    main {
        kotlin.setSrcDirs(listOf("../../app/src/main/java"))
        kotlin.setIncludes(domainPaths)
    }
    test {
        kotlin.setSrcDirs(listOf("../../app/src/test/java"))
        kotlin.setIncludes(domainPaths)
        resources.setSrcDirs(listOf("../../app/src/test/resources"))
        resources.include("reception/**")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

tasks.test {
    useJUnit()
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
}
