# ===== 禁用混淆，仅缩减（R8 full mode 下混淆会破坏 Gson/Retrofit 反射链）=====
-dontobfuscate

# ===== Retrofit / OkHttp =====
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit service 接口（Retrofit 通过反射创建代理）
-keep interface com.gift.tolife.core.network.OpenAiService { *; }

# ===== Gson 序列化类（字段名必须与 JSON key 一致）=====
-keep class com.gift.tolife.core.network.dto.** { *; }
-keep class com.gift.tolife.core.export.** { *; }
-keep class com.gift.tolife.core.datastore.AppSettings { *; }
-keep class com.gift.tolife.feature.settings.ModelConfigExport { *; }

# ===== WorkManager Worker（通过反射实例化）=====
-keep class com.gift.tolife.core.ai.TagWorker { *; }
-keep class com.gift.tolife.core.ai.TagWorker$WorkerEntryPoint { *; }

# ===== Hilt EntryPoint（通过反射访问）=====
-keep class com.gift.tolife.GiftApp$CleanupEntryPoint { *; }

# ===== Room 枚举 TypeConverter =====
-keepclassmembers enum com.gift.tolife.core.model.** {
    public static **[] values();
}
