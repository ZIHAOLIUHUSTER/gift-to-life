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
# 2026-09-24：周/月志的后台生成
-keep class com.gift.tolife.core.ai.SummaryWorker { *; }
-keep class com.gift.tolife.core.ai.SummaryWorker$WorkerEntryPoint { *; }
# 手动生成同样通过 WorkManager 和 Hilt EntryPoint 在进程重建后恢复
-keep class com.gift.tolife.core.ai.ManualSummaryWorker { *; }
-keep class com.gift.tolife.core.ai.ManualSummaryWorker$Dependencies { *; }
# 默认 ViewModelProvider 使用无参构造，保留 Intent 去重状态的实例化入口
-keep class com.gift.tolife.LaunchIntentState { public <init>(); }

# ===== Hilt EntryPoint（通过反射访问）=====
-keep class com.gift.tolife.GiftApp$CleanupEntryPoint { *; }

# ===== Room 枚举 TypeConverter =====
-keepclassmembers enum com.gift.tolife.core.model.** {
    public static **[] values();
}
