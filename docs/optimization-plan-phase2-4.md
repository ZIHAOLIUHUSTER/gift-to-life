# Gift To Life 优化实施计划（Phase 2–4）

> **给执行模型的上下文：** 本文档是深度代码审查后产出的优化计划。Phase 2/3/4 尚未开始。Phase 1 已在 commit `12248a1` 完成（P1 修复 + 死代码清理）。P0 修复已在 commit `cf8fda2` 完成。
>
> **执行规则：** 每个 Phase 结束后必须 `gradlew assembleDebug` 编译通过，然后 `git commit`。先列计划再动手。
>
> **项目路径：** `D:\gift`
> **编译命令：** `set JAVA_HOME=D:\Android_Studio\jbr&& set ANDROID_HOME=C:\Users\LZH\AppData\Local\Android\Sdk&& .\gradlew.bat assembleDebug --no-daemon`
> **当前版本：** v3.0.0, versionCode 30, DB v4

---

## Phase 2：可扩展性优化（支持万条+闪念）

### 2.1 批量导入替换逐条 INSERT

**问题：** `AppDatabase.kt:15-23` 的 `replaceAll()` 在 `forEach` 循环中逐条调用 `entryDao().insert(staged.entry)`，万条导入产生万次 INSERT。

**方案：**
1. 在 `EntryDao` 添加 `@Insert suspend fun insertAll(entries: List<Entry>): List<Long>`
2. 在 `EntryTagDao` 确认已有批量 `insertAll`（已有则复用）
3. `replaceAll()` 改为先 `insertAll` 批量插入所有 Entry 获取 ID 列表，再批量插入所有 EntryTag
4. 注意：Entry 的 autoGenerate ID 需要与 tags 关联，所以需要按顺序映射返回的 ID

**文件：**
- `app/src/main/java/com/gift/tolife/core/database/dao/EntryDao.kt`
- `app/src/main/java/com/gift/tolife/core/database/AppDatabase.kt`

**验收：** 导入 1000 条耗时显著降低；编译通过。

### 2.2 导出/清空用轻量查询替代 getAllEntriesAsList

**问题：** `ExportImportManager.kt` 和 `EntryRepository` 中多处调用 `getAllEntriesAsList()` 加载全量 Entry（含 content 文本）仅为取 `imagePath`。万条记录时加载几 MB 不必要数据。

**方案：**
1. 已有 `getAllImagePaths()` 方法（P0-4 时新增），替换以下调用点：
   - `ExportImportManager.importV2Safe()` 中的 `entryDao.getAllEntriesAsList().mapNotNull { it.imagePath }.toSet()`
   - `ExportImportManager.importLegacyV1Safe()` 中同样位置
   - `ExportImportManager.clearAllEntries()` 中同样位置
2. 全部改为 `entryDao.getAllImagePaths().toSet()`

**文件：**
- `app/src/main/java/com/gift/tolife/core/export/ExportImportManager.kt`

**验收：** grep 确认 `getAllEntriesAsList` 在 ExportImportManager 中不再被调用；编译通过。

### 2.3 随机条目优化 — 替代 OFFSET 扫描

**问题：** `EntryDao.getEntryAtOffset(offset)` 用 `LIMIT 1 OFFSET :offset`，万条时 offset 接近末尾需扫描近全表。

**方案：**
1. 在 `EntryDao` 新增：
   ```kotlin
   @Query("SELECT * FROM entries WHERE type = 'NORMAL' AND isDeleted = 0 AND id >= :minId ORDER BY id ASC LIMIT 1")
   suspend fun getEntryWithMinId(minId: Long): Entry?
   ```
2. `RandomReviewViewModel.fetchRandom()` 改为：
   - 先查 `getActiveEntryCount()` 获取总数 N
   - 随机生成 `targetRank = Random.nextInt(N)`
   - 用 `getEntryAtOffset(targetRank)` 作为简单方案保留（SQLite 对小 offset 尚可）
   - 或者更优：查 `SELECT id FROM entries WHERE type='NORMAL' AND isDeleted=0 ORDER BY id ASC` 拿到 ID 列表（轻量，只取 id 列），然后随机选一个 ID 再 `getById()`

**推荐方案：** 加一个 `getAllActiveIds(): List<Long>` 查询（只查 id 列，极轻量），随机选一个 ID 后 `getById()`。

**文件：**
- `app/src/main/java/com/gift/tolife/core/database/dao/EntryDao.kt`
- `app/src/main/java/com/gift/tolife/feature/memory/RandomReviewViewModel.kt`

**验收：** 随机回顾功能正常；编译通过。

### 2.4 LazyColumn 添加稳定 key

**问题：** `RecordScreen.kt` 中 `items(lazyItems.itemCount) { index -> }` 未提供 `key`，滚动和列表更新时无法按 `entry.id` 复用。

**方案：**
```kotlin
items(
    count = lazyItems.itemCount,
    key = { index -> lazyItems[index]?.entry?.id ?: index }
) { index ->
```

**文件：**
- `app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt`

**验收：** 编译通过；快速滚动不闪烁。

### 2.5 SimpleDateFormat 缓存

**问题：** `EntryCard.kt`、`EntryPreviewSheet.kt`、`EditEntryBottomSheet.kt`、`MemoryScreen.kt`、`OnThisDayScreen.kt`、`SettingsScreen.kt` 每次调用都 `new SimpleDateFormat`。在 `EntryCard`（列表高频项）影响最明显。

**方案：**
1. 新建 `app/src/main/java/com/gift/tolife/core/common/DateFormats.kt`：
   ```kotlin
   object DateFormats {
       val dateTime: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
           SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
       }
       val date: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
           SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
       }
       fun formatDateTime(ts: Long): String = dateTime.get()!!.format(Date(ts))
       fun formatDate(ts: Long): String = date.get()!!.format(Date(ts))
   }
   ```
2. 所有 `private fun formatTime/ts` 函数替换为调用 `DateFormats.formatDateTime()`
3. 删除各文件中的 `private fun formatTime` / `private fun formatFullTime`

**文件：**
- 新建 `core/common/DateFormats.kt`
- `feature/record/EntryCard.kt`
- `feature/record/EntryPreviewSheet.kt`
- `feature/record/EditEntryBottomSheet.kt`
- `feature/memory/MemoryScreen.kt`
- `feature/memory/OnThisDayScreen.kt`
- `feature/settings/SettingsScreen.kt`

**验收：** grep 确认无 `SimpleDateFormat(` 散落实例化；编译通过。

### 2.6 OnThisDay 单次查询替代逐年循环

**问题：** `OnThisDayViewModel.load()` 从 `thisYear-1` 循环到 `firstYear`，每年一次 `getEntriesByDateRange()`，N 年 N 次查询。

**方案：**
1. 在 `EntryDao` 新增：
   ```kotlin
   @Query("""
       SELECT * FROM entries
       WHERE type = 'NORMAL' AND isDeleted = 0
         AND (strftime('%m-%d', createdAt / 1000, 'unixepoch', 'localtime') = :monthDay)
       ORDER BY createdAt ASC
   """)
   suspend fun getEntriesByMonthDay(monthDay: String): List<Entry>
   ```
2. `OnThisDayViewModel.load()` 改为：
   - 格式化今天为 `"MM-dd"` 字符串
   - 一次性查询所有匹配条目
   - 按 `Calendar.YEAR` 分组到 `Map<Int, List<Entry>>`
   - Feb 29 闰年处理：如果今天不是闰年 Feb 28，则查 `"02-29"` 会返回空（正确行为）

**注意：** `strftime` 的 `localtime` 转换依赖设备时区，与 `TimeUtil` 一致。需测试跨年边界。

**文件：**
- `app/src/main/java/com/gift/tolife/core/database/dao/EntryDao.kt`
- `app/src/main/java/com/gift/tolife/feature/memory/OnThisDayViewModel.kt`

**验收：** OnThisDay 显示正确；多年数据只需一次查询；编译通过。

### 2.7 V1 导入流式 JSON 解析（可选，视优先级）

**问题：** `ExportImportManager.importLegacyV1Safe()` 用 `String(it.readBytes())` 将整个 JSON 文件读入内存，大备份 OOM。

**方案：** 用 `com.google.gson.stream.JsonReader` 流式解析。

**评估：** V1 格式已很少使用，可标记为低优先级。如果不做，至少在 `copyToTempFile` 的 2 GiB 限制基础上加一个更低的 V1 专用限制（如 50 MB）。

**文件：**
- `app/src/main/java/com/gift/tolife/core/export/ExportImportManager.kt`

### Phase 2 编译 + 提交

```powershell
git add -A
git commit -m "perf: scalability for 10K+ entries (Phase 2)

- Batch insert for import (insertAll)
- getAllImagePaths replaces getAllEntriesAsList for path collection
- Random entry: ID list + random pick instead of OFFSET scan
- LazyColumn stable key by entry.id
- SimpleDateFormat cached via ThreadLocal
- OnThisDay: single query by month-day instead of per-year loop"
```

---

## Phase 3：体积最小化

### 3.1 开启 R8 full mode

**方案：** 在 `gradle.properties` 添加：
```
android.enableR8.fullMode=true
```

**风险：** 需确认 ProGuard 规则完备，否则可能裁剪掉反射使用的类。当前 keep 规则已覆盖 Gson DTO、Room Entity、Retrofit 接口、枚举 values()。开启后编译 + 检查 release APK 是否正常。

**文件：** `gradle.properties`

### 3.2 移除未使用依赖

**`lifecycle-runtime-compose`：** 全项目 grep `collectAsStateWithLifecycle` 返回 0 结果。该依赖未被使用。

**方案：** 从 `app/build.gradle.kts` dependencies 中删除：
```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
```

**文件：** `app/build.gradle.kts`

### 3.3 收窄 ProGuard keep 规则

**问题：** `proguard-rules.pro` 中 `-keep class com.gift.tolife.core.network.** { *; }` 整包 keep，阻止 R8 优化网络层。

**方案：** 替换为精确 keep：
```proguard
# Retrofit service interface
-keep,allowobfuscation interface com.gift.tolife.core.network.OpenAiService { *; }

# Gson DTOs (already kept)
-keep class com.gift.tolife.core.network.dto.** { *; }

# Remove the broad -keep class com.gift.tolife.core.network.** { *; }
```

**文件：** `app/proguard-rules.pro`

### 3.4 清理构建配置

**移除 `useLegacyPackaging`：** 项目无原生库依赖，`jniLibs { useLegacyPackaging = true }` 无实际作用。

**移除 `vectorDrawables.useSupportLibrary`：** `minSdk = 26`，系统原生支持矢量图，无需 compat 库。

**方案：** 从 `app/build.gradle.kts` 中删除：
```kotlin
vectorDrawables {
    useSupportLibrary = true
}
```
和
```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = true
    }
    ...
}
```
保留 `packaging { resources { excludes += ... } }`。

**文件：** `app/build.gradle.kts`

### 3.5 Release 签名配置（重要提醒）

**当前状态：** release 使用 debug 签名。这不是体积问题但影响分发。

**建议：** 创建 `keystore.properties`（不入库），在 `build.gradle.kts` 中读取正式签名。此为独立任务，不在本 Phase 范围内，但需记录为待办。

### Phase 3 编译 + 提交

```powershell
# 先编译 debug 确认无问题
.\gradlew.bat assembleDebug --no-daemon

# 再编译 release 确认 R8 full mode + ProGuard 规则正确
.\gradlew.bat assembleRelease --no-daemon

git add -A
git commit -m "perf: minimize APK size (Phase 3)

- Enable R8 full mode
- Remove unused lifecycle-runtime-compose dependency
- Narrow ProGuard keep from network.** to specific DTOs + service
- Remove unnecessary useLegacyPackaging and vectorDrawables config"
```

**验收：** 对比 release APK 大小，应小于当前 1.75 MB。

---

## Phase 4：体验美化

### 4.1 EXIF 方向修正

**问题：** `ImageUtil.copyToPrivateDir` 不处理 EXIF 旋转元数据。竖拍照片压缩后显示旋转 90°。

**方案：**
1. 在 `ImageUtil` 解码 bitmap 后，读取 EXIF 旋转角度
2. 用 `Matrix` 旋转 bitmap
3. 再压缩写入

```kotlin
import androidx.exifinterface.media.ExifInterface

// 在 decode bitmap 之后、compress 之前：
val exif = context.contentResolver.openInputStream(sourceUri)?.use { 
    ExifInterface(it) 
}
val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
    else -> 0f
}
val finalBitmap = if (rotation != 0f) {
    val matrix = Matrix().apply { postRotate(rotation) }
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
} else {
    bitmap
}
```

**依赖：** 需添加 `implementation("androidx.exifinterface:exifinterface:1.3.7")`

**文件：**
- `app/build.gradle.kts`（添加依赖）
- `app/src/main/java/com/gift/tolife/core/common/ImageUtil.kt`

**验收：** 竖拍照片压缩后方向正确。

### 4.2 WEBP 弃用格式更新

**问题：** `ImageUtil.kt` 用 `@file:Suppress("DEPRECATION")` + `Bitmap.CompressFormat.WEBP`（API 30 起弃用）。

**方案：**
```kotlin
val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
    Bitmap.CompressFormat.WEBP_LOSSY
} else {
    @Suppress("DEPRECATION")
    Bitmap.CompressFormat.WEBP
}
```
移除文件级 `@file:Suppress("DEPRECATION")`。

**文件：** `app/src/main/java/com/gift/tolife/core/common/ImageUtil.kt`

### 4.3 色彩体系清理

**问题：** `Color.kt` 中以下颜色定义未被 `Theme.kt` 引用，也未在 UI 中使用：
- `TextTertiaryLight` / `TextTertiaryDark`
- `TagFlashThought` / `TagEvent` / `TagEmotion` / `TagKnowledge`

**方案：** 删除这些未使用的颜色定义。保留 `DividerLight/Dark`（在 Theme.kt 中使用）。

**验证：** 删除前 grep 确认无引用：
```
grep_search TextTertiary → 仅 Color.kt
grep_search TagFlashThought → 仅 Color.kt
```

**文件：** `app/src/main/java/com/gift/tolife/core/ui/theme/Color.kt`

### 4.4 AI 错误分类 — 不重试编程错误

**问题：** `AiClient.kt` 的 `chatWith` 和 `describeImage` 末尾 `catch (t: Throwable)` 把 NPE、JsonParseException 等编程错误当作 `RetryableFailure`，WorkManager 会重试 5 次。

**方案：** 把最后的 catch 分为两层：
```kotlin
} catch (e: IllegalArgumentException) {
    AiResult.PermanentFailure("Invalid argument: ${e.message}")
} catch (e: IllegalStateException) {
    AiResult.PermanentFailure("Invalid state: ${e.message}")
} catch (e: com.google.gson.JsonParseException) {
    AiResult.PermanentFailure("Parse error: ${e.message}")
} catch (t: Throwable) {
    AiResult.RetryableFailure(t)
}
```

**文件：** `app/src/main/java/com/gift/tolife/core/network/AiClient.kt`

**验收：** 编译通过；AI 调用 NPE 不再重试。

### 4.5 MemoryScreen 空状态列表拼接优化（微优化）

**问题：** `MemoryScreen.kt` 中 `summaryState.weekSummaries + summaryState.monthSummaries` 每次重组都创建新 List。

**方案：** 改为：
```kotlin
if (randomState.entry == null && summaryState.weekSummaries.isEmpty() && summaryState.monthSummaries.isEmpty()) {
```

**文件：** `app/src/main/java/com/gift/tolife/feature/memory/MemoryScreen.kt`

### 4.6 updateStreak 移到 IO 线程（微优化）

**问题：** `RecordViewModel.updateStreak()` 在 `viewModelScope`（默认 Main 调度器）中做同步 SharedPreferences 读写。

**方案：** 把 `updateStreak()` 包在 `withContext(Dispatchers.IO)` 中，或在 `saveDraft` 的 launch 中切换调度器。

**文件：** `app/src/main/java/com/gift/tolife/feature/record/RecordViewModel.kt`

### Phase 4 编译 + 提交

```powershell
.\gradlew.bat assembleDebug --no-daemon

git add -A
git commit -m "feat: UX polish and robustness (Phase 4)

- EXIF orientation: read and apply rotation before WebP compress
- WEBP_LOSSY on API 30+ (replaces deprecated WEBP)
- Remove unused color palette (TextTertiary, Tag* colors)
- AI error classification: don't retry programming errors
- MemoryScreen: avoid list allocation in empty-state check
- updateStreak: move SP I/O off main thread"
```

---

## 附录：已知但不在本轮范围的问题

| 问题 | 原因 |
|------|------|
| API Key 明文存于 SharedPreferences | 需引入 EncryptedSharedPreferences，改动较大 |
| 备份 ZIP 无加密 | 需选型加密方案 |
| Release 用 debug 签名 | 需用户创建正式 keystore |
| FTS4 全文搜索 | LIKE '%query%' 在万条以下尚可，FTS 需新建虚拟表 + 触发器 + Migration，复杂度高 |
| 周/月总结只能当天生成 | 需改产品逻辑允许补生成 |
| 总结静默截断 48K | 需改为分段总结或告知用户 |
| Prompt 注入风险 | 个人日记低危，暂不处理 |
| 分享文本无长度限制 | 需在 consumeSharedContent 加截断 |
| Retrofit service cache 已加 @Synchronized | Phase 1 已修复 |

---

## 当前 git 状态

```
最新 commit: 12248a1 (Phase 1 完成)
P0 修复: cf8fda2
工作区: clean
分支: master, ahead of origin by 2 commits
```
