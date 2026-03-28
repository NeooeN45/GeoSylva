# ============================================================
# GeoSylva ProGuard / R8 rules — release build
# ============================================================

# Keep source file names and line numbers for crash traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all annotations (needed by Room, Serialization, WorkManager)
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# ── Application globale (keep all app code — protège domain/data/presentation) ─
# Empêche R8 de supprimer domain.calculation, domain.usecase, domain.location,
# domain.classification, data.preferences, data.mapper, presentation, etc.
-keep class com.forestry.counter.** { *; }
-keepnames class com.forestry.counter.** { *; }

# ── @Serializable classes (keep serializers + companions) ──────────────────────
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    static ** serializer();
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Room ────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.forestry.counter.data.local.** { *; }
-keep class com.forestry.counter.data.local.dao.** { *; }
-keep class com.forestry.counter.data.repository.** { *; }
-dontwarn androidx.room.paging.**
# Generated Room _Impl classes
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }
# Room Migration anonymous objects must survive shrinking
-keep class com.forestry.counter.data.local.DatabaseMigrations { *; }
-keep class com.forestry.counter.data.local.DatabaseMigrations$* { *; }
-keep class * extends androidx.room.migration.Migration { *; }

# ── Jetpack Startup (InitializationProvider runs BEFORE Application.onCreate) ─
# R8 supprime ces classes car elles ne sont pas appelées directement depuis le code
# mais elles sont instanciées par réflexion via le manifest fusionné → StartupException
-keep class androidx.startup.** { *; }
-keep class * implements androidx.startup.Initializer { *; }
-keepclassmembers class * implements androidx.startup.Initializer {
    <init>();
}
# Initializers ajoutés automatiquement par les dépendances (emoji2, lifecycle, profileinstaller)
-keep class androidx.emoji2.text.EmojiCompatInitializer { *; }
-keep class androidx.emoji2.text.EmojiCompatInitializer$* { *; }
-keep class androidx.lifecycle.ProcessLifecycleInitializer { *; }
-keep class androidx.profileinstaller.ProfileInstallerInitializer { *; }
-keep class androidx.profileinstaller.ProfileInstallerInitializer$* { *; }
-dontwarn androidx.startup.**
-dontwarn androidx.profileinstaller.**
-dontwarn androidx.emoji2.**

# ── WorkManager Workers (instantiated by reflection) ────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.forestry.counter.data.work.** { *; }
-dontwarn androidx.work.**

# ── Kotlin Coroutines (CRITICAL — R8 strips continuations) ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Kotlin Serialization ────────────────────────────────────
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.forestry.counter.**$$serializer { *; }
-keepclassmembers class com.forestry.counter.** {
    *** Companion;
}
-keepclasseswithmembers class com.forestry.counter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
    static ** serializer();
    *** INSTANCE;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── DataStore / Preferences ──────────────────────────────────
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn androidx.datastore.**

# ── Kotlin stdlib & reflection ───────────────────────────────
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# ── Enum safety ──────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Apache POI ───────────────────────────────────────────────
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.batik.**
-dontwarn javax.xml.**
-dontwarn org.w3c.**
-dontwarn org.etsi.**
-dontwarn org.bouncycastle.**
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }

# ── OpenCSV ──────────────────────────────────────────────────
-dontwarn com.opencsv.**
-keep class com.opencsv.** { *; }

# ── exp4j formula parser ─────────────────────────────────────
-dontwarn net.objecthunter.exp4j.**
-keep class net.objecthunter.exp4j.** { *; }

# ── OkHttp ───────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ── MapLibre / Mapbox ────────────────────────────────────────
-keep class com.mapbox.mapboxsdk.** { *; }
-keep class com.mapbox.geojson.** { *; }
-keep class com.mapbox.turf.** { *; }
-dontwarn com.mapbox.**
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# ── BlurView ─────────────────────────────────────────────────
-keep class eightbitlab.com.blurview.** { *; }
-dontwarn eightbitlab.com.blurview.**

# ── Accompanist ──────────────────────────────────────────────
-keep class com.google.accompanist.** { *; }
-dontwarn com.google.accompanist.**

# ── Google Play Services (Location) ──────────────────────────
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── CrashLogger singleton ────────────────────────────────────
-keep class com.forestry.counter.data.logging.CrashLogger { *; }

# ── Strip verbose debug logging in release ───────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
