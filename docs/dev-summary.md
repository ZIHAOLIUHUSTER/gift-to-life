# Gift To Life 开发摘要

## 项目概览
极简 Android 个人闪念记录工具。Kotlin + Jetpack Compose + Room v4 + Hilt。

## 环境
- JDK 21: `D:\Android_Studio\jbr`
- Android SDK: `C:\Users\LZH\AppData\Local\Android\Sdk`
- 项目路径: `D:\gift`
- 编译: `gradlew assembleDebug`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- GitHub: https://github.com/ZIHAOLIUHUSTER/gift-to-life
- Push: `git push origin master`（需 VPN 代理 127.0.0.1:7892）

## 架构
- 包名: `com.gift.tolife`
- 单 App 模块，MVVM + Repository
- 数据库: Room v4, `Entry`(含 isDeleted/summaryModel/entryRevision), `EntryTag`
- 设置: SharedPreferences（`gift_settings`）
- 图片: `filesDir/images/`, WebP 压缩 1920px 85%, EXIF 方向修正, WEBP_LOSSY(API 30+)

## 当前版本
- versionCode: 30, versionName: 3.5.0
- APK: release ~1.67 MB（R8 full mode 已开启）
- 数据库: Migration 1→3→4, `fallbackToDestructiveMigration` 已移除
- 代码: 死代码已清理，净减约 80 行

## 核心功能
1. 记录 CRUD + 图片（WebP 压缩 + EXIF 方向）
2. AI 标签（WorkManager + revision 并发保护 + 编程错误不重试）
3. 周/月总结（周一/1号限时生成，时间序采样，48K 预算，去重）
4. 搜索 + 筛选（Paging 3 + 250ms 防抖 + FlowRow + 空状态）
5. 回忆页（内容驱动高度，Crossfade）
6. 那年今日（单次查询不限年份，详情页）
7. 记录统计（热力图 + 连续天数 + 使用统计）
8. 导出/导入（v2 ZIP 批量导入 + 流式实际解压校验 + SHA-256 + staging + 原子事务）
9. 回收站（软删除 + 标签恢复）
10. 深浅色主题（system/light/dark 响应式切换）
11. Widget + 分享接入

## v3.5.0 质量保障与优化（2026-07-16）

### P0 关键 Bug 修复
- 编辑标签被静默清空（`remember` key 不含 `originalTags` 导致异步加载的标签丢失）
- `editingImageEntry` 泄漏（编辑后 Composer 选图误改旧记录）
- 全局异常处理器吞崩溃（未链式调用 previous handler）
- `removeOrphans()` 从未调用（孤儿图片泄漏，启动时接入清理）
- `permanentlyDeleteAllDeleted` 不清理图片文件

### P1 修复（Phase 1）
- ZIP 炸弹：流式解压跟踪实际字节数，超限即中止
- Bitmap 泄漏：`try-finally` 确保 `recycle()`
- 导入配置后编辑框不刷新：`LaunchedEffect(uiState.settings)`
- AI 测试测旧值：`chatWith()` 接受当前编辑值
- 分享纯图片清空已有草稿：保留 `draftText`
- OnThisDay 删除是空操作：接入 `EntryRepository.softDelete`
- MemoryScreen 预览标签错：维护独立 `previewTags`
- `Converters.toEntryType/toTagType` 安全枚举回退替代 crash
- 导入配置 `updateApiKey`/`updateBaseUrl` 非原子：先校验后保存

### 可扩展性（Phase 2）
- 批量导入：`insertAll(List<Entry>)` 替换逐条 `forEach insert`
- 导出/清空用 `getAllImagePaths()` 替代 `getAllEntriesAsList()`（不再加载 content 到内存）
- 随机条目：`getAllActiveIds()` 随机选 ID 替代 OFFSET 扫描
- LazyColumn：稳定 `key = entry.id`
- SimpleDateFormat：`DateFormats` 统一 ThreadLocal 缓存
- OnThisDay：单次 `getEntriesByMonthDay` 查询替代逐年 N 次往返
- Retrofit service 缓存：`@Synchronized`

### 体积优化（Phase 3）
- R8 full mode 开启（`android.enableR8.fullMode=true`）
- 移除未使用依赖（`lifecycle-runtime-compose`）
- 收窄 ProGuard（`keep core.network.**` → `keep OpenAiService`）
- 移除 `useLegacyPackaging`、`vectorDrawables.useSupportLibrary`

### 体验美化（Phase 4）
- EXIF 方向修正（旋转后压缩）
- `WEBP` → `WEBP_LOSSY`（API 30+）
- 删除未使用颜色调色板（`TextTertiary*`、`Tag*`）
- AI 错误分类：`IllegalArgumentException`/`JsonParseException` 不再重试
- `updateStreak()` 移到 `Dispatchers.IO`
- MemoryScreen 空状态判断避免每重组创建新 List

### 死代码清理
- DAO 删除: `searchByContent`, `getByType`, `permanentlyDeleteOne`, `softDelete`(旧), `restore`(旧), `getEntryAtOffset`, `getActiveEntryCount`
- ViewModel 删除: `removeImage`, `update`, `saveWithTags`, `resolveImagePath`
- 删除: `pendingContentText`（`RecordUiState`）, `SummaryRange.kt`（整个文件）, `UiTestTags.SEARCH_IMAGE_FILTER`
- 删除 45 个过期文档/审查文件/截图（-6197 行）

## 关键文件
- `app/src/main/java/com/gift/tolife/feature/record/` — 记录页（含 EntryEditDraft 草稿模式）
- `app/src/main/java/com/gift/tolife/feature/memory/` — 回忆页
- `app/src/main/java/com/gift/tolife/feature/settings/` — 设置页
- `app/src/main/java/com/gift/tolife/core/database/` — 数据层
- `app/src/main/java/com/gift/tolife/core/ai/` — AI 标签/总结
- `app/src/main/java/com/gift/tolife/core/export/` — 导入导出
- `app/src/main/java/com/gift/tolife/core/network/` — Retrofit + 校验
- `app/src/main/java/com/gift/tolife/core/common/` — ImageUtil + ImageStore + DateFormats + ShareReceiver

## 工具类速查
| 类 | 用途 |
|---|---|
| `DateFormats` | ThreadLocal 缓存的日期格式化（替代散落的 SimpleDateFormat 实例化） |
| `ImageUtil` | WebP 压缩 1920px 85% + EXIF 方向修正 |
| `ImageStore` | 图片目录管理 + 孤儿清理 |
| `EntryQuerySqlBuilder` | 搜索/筛选 SQL 构建（LIKE 转义保护） |
| `BaseUrlValidator` | 强制 HTTPS + 标准化 |

## 开发规则
- `docs/dev-summary.md` 是唯一权威文档（优先于其他 markdown）
- 先列计划再动手
- 每阶段编译通过（`assembleDebug`）+ git commit
- 不改数据结构时不升 DB 版本
- 找不到工具路径先问
