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
- versionCode: 37, versionName: 4.3.0
- APK: release ~2.24 MB（R8 full mode 已开启）
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
8. 导出/导入（v2 ZIP；导出临时归档+自检后落盘、含回收站；导入只读预检+导入前快照+可撤销；流式实际解压校验 + SHA-256 + staging + 原子事务）
9. 回收站（软删除 + 标签恢复）
10. 六套系列皮肤（青松/暖纸·赤陶/冷雾/烟粉·酒红/石墨蓝/藕荷紫）+ 深浅色主题（system/light/dark）
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

## v4.2.1–4.2.2 备份链路加固（2026-09-17）

- 导出：临时归档 → 结构自检 → 复制到目标（失败不截断旧备份）；NonCancellable + 进度提示；默认文件名带时间戳
- 回收站条目及图片纳入导出；回执显示总条数/回收站数/图片数/缺图数
- 导入：只读预检弹窗（条数/图片/版本/时间）、空备份二次确认、导入前自动快照（cacheDir，最近 3 份）、一键「撤销上次导入」
- 一条目一份图片文件（导入时逐条复制），避免共享图片被互相删除；导出重名图片自动加后缀
- 配额按解压后实际字节累计；manifest 128 MiB、V1 JSON 256 MiB 读取上限
- 启动孤儿图片清扫加 10 分钟年龄保护，消除与导入（先落盘后提交）的竞态
- 标签双写 `tagIds`（枚举名）+ `tags`（文案），导入优先枚举名、旧备份回退文案，未知标签计数提示
- 模型配置导入全字段校验后统一应用（不再半生效），64 KiB 上限
- 备份 manifest 写入真实 App 版本；系统备份/迁移规则排除数据库与图片目录
- JVM 单测 39 个全绿（备份链路新增 24 个：归档写入/校验、标签编解码、模型配置）

## v4.3.0 系列设计 token 落地（2026-09-17）

与 Date To Life 0.18.0 同源，共用一套 To Life 系列设计 token。

- **皮肤 6 套**：暖纸·赤陶 / 冷雾(雾蓝) / 烟粉·酒红 / **青松**（Gift 默认，即原来的墨绿 `#5B7B6F`）/ 石墨蓝 / 藕荷紫
  - 设置 → 外观 新增「皮肤」选择（色卡 + 单选）；存 SharedPreferences `theme_variant`，老用户默认青松
  - 深色变体由 `MorandiPalette.toDark()` 从浅色派生，不手写 6×19 个色号
- **主题色带 `zone`**：大面积分区/空状态用，色相跟主题、饱和锁绝对值、明度 87%；首个消费者：`AppEmptyState`（空状态加色带圆环，随皮肤）
- **形状**：`GiftShapes` 改为系列 token 6/8/10/12/16（原 4/6/8/12/16，`Shape.kt` 并入 `Theme.kt`）
- **字号**：`headlineLarge` 24 → 26sp（对齐系列 6 档；该档当前无调用点，先把阶梯对齐）
- **状态色**：新增 `LifeStatus`（success/danger/warning/info），AI 连通测试的 ✓/✗ 不再硬编码 `#4CAF50/#E53935`
- **术语（系列术语表 v1）**：「模型配置」→「AI 配置」、「数据管理」→「备份与恢复」、「API 地址」→「服务地址」、「测试」→「测试连接」
- **测试**：新增 `LifeThemeTest`（6 套皮肤 WCAG 验收：正文 ≥7、按钮/次级 ≥4.5、深色点睛 ≥4.5、色带 ≥4.5、皮肤互不相同、未知皮肤名回退默认），JVM 单测 39 → **45 个全绿**

> 与 Date 的差异：Gift 的标签（闪念/事记/情绪/知识）按系列决定不使用彩色，保持中性 chip，因此**未引入** 10 色分类板；Gift 也没有小组件卡片底的烘焙 drawable 需要同步。

### v4.3.1 组件层对齐（2026-09-17）

- 内容卡统一 `shapes.medium`(10)：输入卡、那年今日卡、统计卡、随机回顾卡；
- 输入框 8 → **6dp**（输入档）；主按钮（周/月总结通栏按钮、预览面板编辑）→ `shapes.large`(12) + 高 48；
- **去掉全项目唯一一处卡片阴影**（输入卡 1dp → 0dp，回到系列“0 阴影 + 卡片色差分层”）；
- 保留：行内紧凑按钮（高 40 / 圆角 8）、图片缩略图 clip。
- 完整清单：`D:\work_claude\series_review\08-组件层对齐清单.md`；单测 45 例全绿。

> 挂账：`MemoryScreen` 有 7 条历史遗留 `!!` 警告（HEAD 就存在，与本次改动无关）。

### v4.3.2 圆角图标统一（2026-09-17）

- 图标从「自制方角 vector drawable」统一为 **Material 圆角图标**（`Icons.Rounded.*` / `Icons.AutoMirrored.Rounded.*`），引入 `material-icons-extended`；
- 顺手修掉设置页图标语义错配：**备份与恢复 → `Backup`、回收站 → `Delete`**（原来是“编辑”与“刷新”）、外观 → `Palette`、AI 配置 → `Settings`；
- 底栏图标模型从 `@DrawableRes Int` 改为 `ImageVector`；回忆/记录/设置/周总结/月总结/那年今日 六个页面各配语义图标（之前底栏全是同一个 sparkle）；
- 删除 **15 个**不再引用的 `ic_*.xml`；`ic_casino`（小组件布局用）与启动图标保留；
- **实测体积**：debug 11.50 → **17.48 MB**（全量图标库未缩减，仅影响开发包）；release 2.34 → **2.22 MB**（R8 把删掉的资源也一并清了）。

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
