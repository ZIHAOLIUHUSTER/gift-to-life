# Retrofit
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.gift.tolife.core.network.dto.** { *; }
-keep class com.gift.tolife.core.export.** { *; }
-keep class com.gift.tolife.core.datastore.AppSettings { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
