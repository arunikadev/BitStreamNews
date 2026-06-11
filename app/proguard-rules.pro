# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Uncomment to preserve line numbers for debugging stack traces:
#-keepattributes SourceFile,LineNumberTable
#-renamesourcefileattribute SourceFile

# ── Retrofit 2 ────────────────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# ── OkHttp 3/4 ───────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Gson (model serialisation) ────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepclassmembers class com.example.bit_stream_news.model.** { *; }
-keep class com.example.bit_stream_news.model.** { *; }

# ── Glide ─────────────────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.AppGlideModule
-dontwarn com.bumptech.glide.**