# ProGuard / R8 rules for the Gudgum production-flow app.
#
# Without these, release builds strip kotlinx-serialization's generated
# .serializer() methods on @Serializable DTOs and Retrofit response
# parsing fails — manifests as silently-empty lists in production.

# ──────────────────────────────────────────────────────────────────────
# kotlinx.serialization
# ──────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep all of kotlinx.serialization's runtime
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `Companion` object fields of every @Serializable class
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on those companion objects
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class <2>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` for @Serializable singletons / object classes
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the auto-generated $$serializer classes for all DTOs in this project
-keep,includedescriptorclasses class com.example.gudgum_prod_flow.**$$serializer { *; }

# Belt-and-braces: keep every member of every DTO so field names survive
-keepclassmembers class com.example.gudgum_prod_flow.data.remote.dto.** { *; }

# ──────────────────────────────────────────────────────────────────────
# Retrofit
# ──────────────────────────────────────────────────────────────────────
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Suspend-fun continuation type is read reflectively by retrofit
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep all Retrofit API interfaces in the project
-keep interface com.example.gudgum_prod_flow.data.remote.api.** { *; }

# ──────────────────────────────────────────────────────────────────────
# OkHttp + Okio (warnings only — these are usually shrink-safe)
# ──────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ──────────────────────────────────────────────────────────────────────
# Hilt / Dagger (generated code must stay)
# ──────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedEntryPoint { *; }
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ──────────────────────────────────────────────────────────────────────
# Room
# ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ──────────────────────────────────────────────────────────────────────
# Supabase / Ktor (used by Realtime client)
# ──────────────────────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ──────────────────────────────────────────────────────────────────────
# Project's own @Serializable DTOs (catch-all so nothing gets renamed)
# ──────────────────────────────────────────────────────────────────────
-keepclasseswithmembers,allowobfuscation class com.example.gudgum_prod_flow.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.gudgum_prod_flow.domain.model.** { *; }
-keep,includedescriptorclasses class com.example.gudgum_prod_flow.data.local.entity.** { *; }

# ──────────────────────────────────────────────────────────────────────
# Misc — keep line numbers for crash reports
# ──────────────────────────────────────────────────────────────────────
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
