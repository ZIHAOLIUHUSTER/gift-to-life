# Retrofit
-keepattributes Signature
-keepattributes *Annotation*

# Keep Retrofit service interfaces
-keep,allowobfuscation interface com.gift.tolife.core.network.OpenAiService { *; }

# Gson
-keep class com.gift.tolife.core.network.dto.** { *; }
-keep class com.gift.tolife.core.export.** { *; }
-keep class com.gift.tolife.core.datastore.AppSettings { *; }
-keep class com.gift.tolife.feature.settings.ModelConfigExport { *; }
-keep class com.gift.tolife.core.ai.AiResult { *; }
-keep class com.gift.tolife.core.ai.AiResult$* { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *

# Keep enum values() for Room type converters
-keepclassmembers enum com.gift.tolife.core.model.** { public static **[] values(); }
