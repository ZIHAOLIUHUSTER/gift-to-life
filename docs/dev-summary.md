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
- versionCode: 58, versionName: 4.7.6
- APK: `app/release/gift-4.7.6-58-release.apk`，约 2.4 MiB（R8 full mode 已开启）
- 数据库: Migration 1→3→4, `fallbackToDestructiveMigration` 已移除（本版 schema 零改动）
- 代码: 死代码已清理

## v4.7.5 纸本器物感精修（2026-10-18）

- **目标**：把「纸 + 白卡 + 发丝线」的极简骨架做出完成度；不改数据库、备份协议、AI 契约、包名与签名。
- **版式**：主页从 TopAppBar 换成 `PageHeader`（28sp 标题 + 功能眉题：日期 / 年月 / GIFT TO LIFE），配 14dp 短痕；副页统一 20sp + 发丝线。
- **记录**：书写案改 16dp 大圆角 + 极轻投影，聚焦时边框/阴影过渡，保存收成胶囊并带轻触感；记录流按今天/昨天/日期分组，时间用等宽数位；有图记录改右侧 72dp 白边小相片，搜索态保留完整日期。
- **回忆**：随机回顾卡加两张错位底纸、短句居中引语（主色引号）、刷新图标 360° 旋转 + 轻触感；周志/月志/那年今日加 30dp 图标片。
- **设置**：本月摘要改大数字 + 连续天数 + 星期标记热力图 + 今天细环；分组行加 30dp 图标片；SectionLabel 统一短痕。
- **底栏/动效**：自定义细底栏（21dp 图标 + 10sp 标签 + 14×2dp 选中短痕），去掉 Material 默认选中胶囊；NavHost 加淡入淡出与副页 1/8 滑入。
- **工程**：修正外层 Scaffold 与页面内 Scaffold 重复吃状态栏 inset；补全 M3 surface container 槽位，避免默认淡紫泄漏；新增 `ExtendedColors.accentText`（小字主色压暗，对纸底 ≥4.5:1）、`PaperCard`、`PageHeader`、`AppBottomBar`。
- **验证**：251 个 JVM 单测全绿；Debug / Debug AndroidTest / Release APK 构建通过；`git diff -- app/schemas/` 为空；未连接设备，未做真机视觉/大字体验收，模拟器未安装。
- **未动**：桌面小组件与分享卡片仍沿用旧暖白配色；备份协议与 AI 链路未改。

## v4.7.6 桌面小组件精修（2026-10-18）

- **视觉**：随机回顾与快速记录改为和主界面同一套「纸面 + 发丝边 + 主色点睛」；随机回顾用 72dp 圆角相片（1dp 相纸边），快速记录收成小纸签。
- **跟随主题**：读取 `gift_settings` 里的 `theme_variant` / `theme_mode`，六套皮肤与深浅模式都能跟随；换肤/切换深浅后通过显式广播刷新已放置的小组件。
- **尺寸自适应**：随机回顾按 minWidth/minHeight 决定眉题、元信息、照片与正文行数；小尺寸只留正文与「换一条」，大尺寸才露出完整卡片结构。
- **修复**：多个随机回顾实例原先共用同一个打开 PendingIntent，`FLAG_UPDATE_CURRENT` 会让它们都打开最后一条；现在按 widgetId 分配请求码。
- **实现**：新增 `widget/WidgetStyle.kt`（调色板、ImageView.setColorFilter 着色、圆角照片）与可着色的 `widget_card_fill` / `widget_card_border` / `widget_rule` / `ic_widget_refresh`；删除固定暖白的旧 drawable。
- **验证**：251 个 JVM 单测全绿；Debug / Debug AndroidTest / Release 构建通过；schema 无差异。未连接设备，未执行真机小组件尺寸/深色截图验收。

## v4.7.4 轻量多词搜索（2026-10-09）

- **取舍**：不引入嵌入模型或向量库。Gift 本体约 2.4 MiB，最小的中文量化模型 ONNX 约 24 MB 再加推理库，得不偿失；语义检索继续由云盘承担，Gift 只改善关键词检索。
- **匹配**：搜索词按任意空白切分（空格、Tab、换行、全角空格等），各词分别 `LIKE`、全部命中才显示，不再要求连续原句。ASCII 英文字母大小写不敏感（跟随 SQLite 默认语义），中文、`%`、`_`、`\` 按字面匹配；超过 64 个不同词回退整段匹配，整条查询超过 8192 字符直接返回空，避免 SQLite LIKE 模式长度异常。
- **排序（仅搜索时）**：整句连续命中优先 → 各词首次命中的覆盖跨度小者优先 → `createdAt DESC, id DESC`。排序整体下推到 SQL，先于 Paging 的 LIMIT/OFFSET，翻页不会重排。单词搜索保持原时间倒序。
- **预览与高亮**：搜索时列表行不再固定显示正文开头四行，而是从命中附近截取约 160 字（整句优先，其次最早命中词），换行折叠为一行、按需加首尾省略号；各关键词分别高亮，重叠区间合并，UTF-16 代理对不被劈开。点开仍是完整原文；无搜索词时外观不变。
- **实现**：新增 `core/common/SearchText.kt`（纯函数：切词、ASCII 折叠、LIKE 转义、超长降级）；`EntryQuerySqlBuilder` 生成 AND 条件与排序表达式；`TextMatch` 复用同一套规则提供 `preview/ranges`；`EntryCard` 展示片段；`RecordScreen` 高亮改用防抖后的查询，避免新词旧列表错位。未新增依赖、未改 schema、未加界面控件。
- **验证**：251 个 JVM 单测通过（新增 SearchText 15、TextMatch 24、EntryQuerySqlBuilder 5）；`EntryQuerySqlBuilderTest` 把真实 SQL 与绑定参数导出到 `app/build/test-fixtures/lightweight-search.json`，由 `.pi/lab/check-search-sql.py` 用本机 SQLite 执行 14 个用例 + 分页/降级检查全部通过（整句优先、跨度、ASCII 大小写、转义、筛选组合、超长）。新增 9 个内存 Room 用例与 4 个 Compose 高亮用例，无连接设备仅编译未执行。Debug / Release / Debug AndroidTest APK 构建通过，`git diff -- app/schemas/` 为空。

## v4.7.3 输入前缀分类（2026-10-09）

- **输入规则**：只在新记录正文绝对开头识别 `#1` / `#2` / `#3`，不要求后接空格；默认或 `#1` 走原 AI 分类，不强制闪念标签；`#2` 直接标为资料，`#3` 直接标为文摘。`#20` / `#23` 等多位编号、未知前缀、正文中间的前缀保持原义。开头有空白时也不识别，避免改写原始编号。
- **界面**：没有新增分类按钮或控件；输入原文始终保留，现有保存按钮跟随前缀显示「保存资料」或「保存文摘」。仅有指令而无正文/图片时禁用保存；指令＋图片支持纯图记录，保存时正文为空。只处理新记录输入，不重新解释已有记录编辑或备份内容。
- **持久化**：`EntryInputParser` 统一按钮与提交校验；正文和标签经 `EntryTransactions.insertWithTags` 同一事务落库，标签失败回滚整个新记录。保存失败或保存中改稿保留原始前缀和草稿，外部分享沿用原 FIFO 冲突处理。
- **AI 边界**：资料、文摘保存不投递自动标签任务；`TagWorker` 在任何网络调用前检查手动标签，编辑产生的任务和遗留任务也不上传做打标签或图片识别；落库事务再次拒绝手动模式的 AI 增强。文摘原有回顾/周月总结规则不变，仍可能作为总结输入发送给总结服务；资料继续排除在总结及回顾之外。标签不是加密或密码保险箱，已经在途的请求无法撤回。
- **验证**：213 个 JVM 单测通过（新增 19 个解析、保存校验与草稿策略用例）；Debug / Release / Debug AndroidTest APK 均构建成功。新增 4 个前缀 Compose 设备用例，另补 4 个内存 Room 保存/回滚设备用例并更新手动标签防 AI 增强用例；无连接设备，设备测试仅编译未执行。schema 无差异，包名和签名不变；4.7.2 的备份面板保留。

## v4.7.2 备份页信息面板恢复（2026-10-09）

- **真机反馈修正**：4.7.0 把原有六项统计压成折叠文字，按钮又嵌在独立描边卡内，破坏了之前的信息面板层级；本版取消该页统计折叠，恢复两列三行的数字＋标签面板（记录/使用天数、图片/总结、图片占用/日均）。
- **视觉**：统计在上、备份操作在下；一张白纸内容面，墨色数字、弱标签、细分隔，保持 22dp 页面边距和 18dp 内距。去掉按钮外多余卡片，导出是主按钮、导入是次按钮，撤销导入及快照信息只在存在快照时显示。页面可滚动，按钮至少 48dp，大字体允许增高。
- **实现**：新增 `feature/settings/BackupContent.kt` 纯展示组件；`SettingsScreen.kt` 保留原文件选择器、预检与空备份强确认、撤销确认、进度与禁用状态。没有修改 ViewModel、备份协议、文件处理或数据库，资料标签功能保留。
- **验证**：194 个 JVM 单测通过，Debug / Release / Debug AndroidTest APK 构建通过；新增 5 个纯假数据 Compose 设备用例，覆盖六项默认可见、操作回调、深色忙碌禁用、320dp＋1.5 倍字号及空数据。当前无连接设备、未执行设备测试或新页面真机截图验收。schema 无差异、包名和签名不变，可覆盖安装。

## v4.7.1 资料标签（2026-10-09）

- **用途**：新增手动标签 `TagType.RESOURCE`（资料），与文摘并列；沿用现有文字、单图 WebP 压缩、搜索、编辑、回收站和备份，不新增标题字段或独立资料页。
- **入口**：保存后打开记录 → 编辑 → 选中「资料」→ 保存；搜索页筛选标签中可选「资料」。允许与其他标签共存，AI 重打标签只替换原四种 AI 标签，不覆盖资料或文摘。
- **回忆边界**：总结输入、月志背景、总结关联记录、待总结条数、随机回顾（含小组件）及那年今日均排除含资料标签的记录；总数、图片数、月度热力图统计仍包含资料。已生成的历史总结不会自动改写。
- **数据兼容**：只加枚举值和查询条件，Room v4 / schema / 包名 / 签名不变；新版本备份使用稳定 ID `RESOURCE`，恢复也兼容中文标签「资料」。旧版本不认识新标签，不建议退回旧版继续编辑或备份资料。
- **验证**：194 个 JVM 单测全部通过；Debug、Release、Debug AndroidTest APK 构建通过；使用独立 SQLite fixture 执行实际 DAO SQL 验证排除条件及统计/导出不受影响。新增 6 个独立内存 Room 设备用例和 1 个手动标签 Compose 用例，因没有连接设备仅完成编译，未执行；`git diff -- app/schemas/` 为空，Release 签名与 4.7.0 一致。
- **边界**：未增加加密或密码保险箱能力，原有 AI 标签/图片描述链路仍可能处理资料内容（配置了服务时）；资料标签本身不构成隐私隔离。APK 可覆盖安装，安装前建议先导出备份，不卸载旧版。

## v4.7.0 常用任务减负与行为修复（2026-10-01）

保留纸面配色、22dp 页面留白、热力图、340dp 随机牌框与定高真堆叠；本轮主要减少点击、等待和状态判断，不重新换色。

- **记录**：空/短输入器最小高 96→56dp；分享接入切回记录，未保存草稿遇到新分享时 FIFO 排队、经选择处理，保存失败或保存中改稿不会被覆盖；清除筛选保留关键词，关闭搜索清理防抖源。
- **预览/编辑**：长文预览操作区固定可见；编辑正文在前、标签可换行，新图片预览跟随草稿 URI；分享/复制/原图保存收进按需菜单。软删除用 Snackbar 撤销；保存中拒绝关闭，脏稿取消放弃后仍可继续编辑。
- **回忆**：已有上周/上月总结整行直达牌桌，历史有独立低强调入口；随机刷新显式 48dp 点击区。全文按钮和返回先收起、不退出牌桌；滑动/翻面教学用过后持久隐藏，正文不再把所有数字加主色粗体。
- **手动总结**：`ManualSummaryScheduler/Worker` 一次性持久任务、冻结范围与时区、同范围 KEEP 去重；离页不取消，成功不等待 Snackbar。历史重写绑定原 id/范围/updatedAt，落库事务复核源记录 id/entryRevision，导入替换或编辑后不应用旧结果。自动任务启动 KEEP、终态追加后继，重试不替换自身。
- **配置/设置**：统一 VM 草稿驱动输入、导入、连接测试和服务状态；整体验证后七字段单次保存，空 Key 可保存但测试就地提示未配置；连接测试按需展开；数据管理页备份置顶、详细统计折叠，主页热力图仍可见；外观保持 3×2 网格，删除冗余浅小字说明。
- **历史时区**：月名按范围中点确定；背景月份只采用与原始边界一致且无歧义的范围，不在旅行后猜错背景。
- **验证**：192 个 JVM 单测通过（基线 106，本轮新增 86）；Debug、Release、Debug AndroidTest APK 编译通过，`git diff -- app/schemas/` 为空；包名/DB v4/签名不变，可覆盖安装。旧 `gift-4.6.1-51-release.apk` 保留。
- **边界**：没有设备，未执行 AndroidTest、真机视觉、大字体、通知或杀进程恢复验收；供应商已接收请求但本地尚未落库时进程被杀，恢复仍可能再次调用 API，不能承诺网络调用恰好一次。

## v4.6.1 留白与文本节奏修正（2026-09-27 真机反馈）

4.6.0 装机后反馈：页面左右留白小、正文几乎顶到屏幕边、整页发闷。原因是全项目页面留白统一用了 16dp。

- 新增 `core/ui/theme/Spacing.kt`，全项目只认三个刻度：
  - `PageGutter = 22.dp`：页面左右留白（列表行、卡片、分组面、顶栏标题对齐）；
  - `ContentPadding = 18.dp`：卡片/分组/弹层内部留白；
  - `RowPaddingV = 16.dp`：列表行上下留白（原 12dp）。
- 字数节奏：`bodyLarge` 行高 24 → **26sp**；`bodySmall` 12 → **13sp**（行高 19sp）；
  记录行的正文与元信息行间距 6 → 8dp。
- 牌桌卡左右留白 20 → 22dp（与页面留白对齐，露出宽度仍为 14dp）。
- 删除 `gift-4.6.0-50-release.apk`（被本版取代）。
- 设计预览 `docs/visual-refactor-preview.html` 已同步（`.feed-item` / `.group` / `.row` / `.appbar` / `.composer` 等）。
- 验证：`testDebugUnitTest` 全绿，`assembleRelease` 通过，`git diff -- app/schemas/` 为空。

## v4.6.0 「温润器物感」视觉重构（2026-09-27）

依据 `docs/visual-refactor-spec.md`（执行稿）与 `docs/visual-refactor-preview.html`（设计预览）实施，
V0–V4 全部落地。**不改数据库/备份/AI 契约，schema 零改动，可直接覆盖安装。**

### V0 全局
- `GiftTypography` **补齐全部 15 档**（旧版只覆盖 6 档，title 各档会回落到 M3 默认）；
  中文一律 `letterSpacing = 0`，最高字重 600（只给页面大标题）。
- 底栏：去掉 12% 主色大胶囊（`indicatorColor = Transparent`），改用颜色区分选中态；
  上方加一条 1dp 发丝线分层，`tonalElevation = 0`。
- 空状态 `AppEmptyState`：删除 88dp 实心主色圆与外层卡片 → 24dp 星形标记（主色 70%）+ 标题 + 一句引导。

### V1 记录页
- 输入器改为**单一内容面**：删掉「外层 Card + 内层描边 TextField」双重容器，
  文字区透明（`BasicTextField`），聚焦只靠光标 + 卡片外边界变色；输入区最小 96dp。
- 图片关闭按钮：28dp 视觉 + **48dp 触控区** + 黑色 45% scrim。
- 记录列表从「每条一张白卡」改为**连续时间流**：透明行 + 1dp 发丝分隔线，
  正文 `bodyLarge` 最多 4 行，标签与时间合并为一行弱文字（例：`事记 · 知识 · 9月24日 23:05`）；
  搜索高亮从 22% 降到 16% 主色。
- 搜索：删掉常驻的 4 个标签 chip + 3 个图片 chip + “清除筛选”；
  改为搜索框 + 带状态点的筛选入口，筛选面板 `ModalBottomSheet` 按需打开、关闭即生效；
  有筛选时只显示一行摘要 + “清除”。

### V2 回忆页（含切换抖动修复）
- 随机回顾卡改为**定高外壳 340dp + 固定内容区**：
  旧版抖动来自三件事：`Crossfade` 同时叠两份完整布局、内容框与下方 `Spacer` 各占一半权重（元信息行不锚底）、
  长文溢出把按钮往下推；另外按钮点击后禁用 300ms 会吞连点。
  现在：只保留一份内容实例、内容区 `weight(1f) + clipToBounds`、元信息行与按钮锚定底部、按钮永不禁用；
  图片位固定 140dp（不因加载完成而撑开）。
- 短文（≤48 字）19sp 居中，长文左对齐按高度截断，截断时才出现“查看全文”。
- 周志 / 月志 / 那年今日 从「两个大按钮 + 散落卡片」合成**一个分组内容面**里的三行（每行 ≥56dp）。
- 空状态去掉外层大卡；新增“去记录”直达动作。

### V3 设置与 AI 配置
- 设置主页：从「大统计卡 + 5 张入口卡」改为「摘要卡 + 2 个分组面」；
  **热力图保留**（用户明确要求），并入摘要卡：`本月 N 条 · 连续 N 天` + 7 列月热力图（18dp 格子、主色四档 28/50/72/92%）
  + 一行图例 `活跃 N 天 · 日均 N 条`。
- AI 配置：默认只留自动回顾、日常识别（地址/Key/标签模型/视觉模型）、总结模型；
  独立总结服务、思考强度、导入导出收进可折叠的「高级设置」；
  三个“保存”合并为页面底部**唯一主按钮**（保存后 Snackbar 回执）。
- 图标统一中性 20dp（不再五颜六色的主色点阵）；内容卡统一 10dp + 1dp `outlineVariant` 描边。

### V4 总结牌桌减噪
- 大圆角 20 → 16dp；顶部色条 3 → 2dp；当前牌阴影 8 → 4dp；
- 事实/引语/洞察不再各用 `LifeStatus` 状态色，统一主色小标记；
- 数据徽章从主色淡底改为中性 `surfaceVariant`；
- 页脚收敛为一个提示（例：`点开读全文 · 还有约 6 行`），去掉手势图标与并列的第二条提示；
- 信件全文行高 32 → 28sp。

### 验证
- `testDebugUnitTest` 全绿（88 例）；`assembleRelease` 通过；
- `git diff -- app/schemas/` 为空（DB 版本仍为 4，可覆盖安装）；
- 产物：`app/release/gift-4.6.0-50-release.apk`（已被 4.6.1 取代）
- 回滚包仍为 `app/release/gift-4.2.0-33-rollback.apk`（DB 版本未变，可直接回装且数据保留）；
- **未做真机/模拟器验收**（本机无 AVD 与连接设备），视觉需在真机上复核。
- 本版也把「那年今日」结果列表统一成了连续时间流（透明行 + 发丝线）。

## 核心功能
1. 记录 CRUD + 图片（WebP 压缩 + EXIF 方向）
2. AI 标签（WorkManager + revision 并发保护 + 编程错误不重试）
3. 周志/月志（**4.4.0 重做**：卡片 + 信件 JSON 契约、闪卡牌桌、双供应商、周一/月初后台自动生成 + 通知、三个月背景、48K 均匀抽样）
4. 搜索 + 筛选（空白分词 AND + 整句/跨度排序 + 命中片段与多词高亮；Paging 3 + 250ms 防抖 + 空状态）
5. 回忆页（内容驱动高度，Crossfade）
6. 那年今日（单次查询不限年份，详情页）
7. 记录统计（热力图 + 连续天数 + 使用统计）
8. 导出/导入（v2 ZIP；导出临时归档+自检后落盘、含回收站；导入只读预检+导入前快照+可撤销；流式实际解压校验 + SHA-256 + staging + 原子事务）
9. 回收站（软删除 + 标签恢复）
10. 六套系列皮肤（青松/暖纸·赤陶/冷雾/烟粉·酒红/石墨蓝/藕荷紫）+ 深浅色主题（system/light/dark）
11. Widget + 分享接入

## v4.4.0 周志/月志重做（2026-09-24）

- **提示词**：旧版要求"诗意总结"却不要求引用事实 → 产出空泛意象。新版改为事实锚定卡片契约，见 `docs/ai-chain.md`。
- **呈现**：`SummaryDeckScreen` 闪卡牌桌（封面数据徽章 → 事实/引语/洞察卡 → 信件整屏阅读），卡片背面是本地记录拼出的"现场"。
- **供应商**：标签/视觉一组，总结可独立一组（留空跟随），见"AI 配置"页。
- **自动生成**：`SummaryWorker` 每周一/每月 1 号 09:00，写完发通知，通知点开直达牌桌。
- **参数**：`readTimeout` 60→180s、`max_tokens` 1024→2048、总结带 `response_format=json_object`。
- **兼容**：卡片 JSON 存进原有 `content` 字段，**数据库版本仍是 4，schema 零改动**，覆盖安装安全；旧纯文本总结按段落切卡展示。
- 新增 27 个单测（卡片解析 / 统计 / 提示词 / 牌组 / 排期），全项目 88 个单测全绿。

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

### v4.3.3 体验与品牌收尾（2026-09-17）

- **搜索命中高亮**：新增纯函数 `TextMatch.ranges`（忽略大小写、不重叠）+ `EntryCard` 用 `AnnotatedString` 把命中字符标主题色底色；6 例单测；
- **启动屏**（core-splashscreen）：墨黑底 + 奶油星形，消除冷启动白屏；`installSplashScreen()` 在 `super.onCreate` 之前；
- **动效规范**：新增 `LifeMotion`（FAST 150 / BASE 200 / SLOW 250 / LIST 200）；随机回顾卡 Crossfade 300 → **200**；
- **大字体 1.3x**：主按钮（周/月总结）从固定 `height(48)` 改为 `heightIn(min = 48)`，字体放大时不再裁字；
- **图标母题统一**（两个 App 一起）：墨黑底 `#181D26` + 系列色圆角几何块（Gift = 奶油星形；Date = 青松/雾蓝/赤陶三色条）；
- **关于页**：版本号（BuildConfig）+ 同系列入口（点一下打开 Date To Life，未安装给 Toast）+ MIT 许可；新增 `core/common/SiblingApp` 互推工具。

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

### v4.3.4 纸张感修订（2026-09-17，真机反馈）

用户真机反馈：**“温润感消失、信息被绿色色块包住、主题色滥用”**。根因是 token v1 把主色相灌进了整个中性骨架：

- 青松背景 `#F1F4F1`（灰绿）、卡片 `#D8DFD9`（**比背景更暗更绿**）—— 卡片不“浮起来”反而“陷进去”；
- 修正（两 App 同步）：**背景 = 纸、卡片 = 白纸、主色只做点睛**；卡片一律比纸更亮，对比 ΔL 5.8–7.5%；
- 6 套皮肤新纸色：暖纸 `#F9F3E8` / 冷雾 `#F2F6FA` / 烟粉 `#FAF3F2` / 青松 `#F5F7EF` / 石墨蓝 `#F4F6F7` / 藕荷紫 `#F9F6FA`；
- 卡片改白：Gift 的 10 处 `surfaceVariant` → `surfaceContainer`（输入框保留浅灰底）；
- 底栏改白纸卡（`surfaceContainer`），与 Date 一致；
- 外观页皮肤色块从 28dp 实心主色方块 → 迷你纸张预览（纸底 + 白卡 + 一点主色）；
- 对比度全项重验：正文 10.7–11.4、次级 4.85–5.20、按钮 4.66–7.09、色带文字 8.3–9.0（WCAG AA 以上）。

### v4.3.5 图标重设计（2026-09-17，真机反馈）

用户反馈：“不喜欢黑色背景、两个 App 图标信息没统一”。改为 **A 纸页卡**：

- 结构 = **纸底 + 白卡 + 主色记号**（与 App 内“纸 + 白卡 + 点睛”完全同构）；
- Gift：纸 `#F5F7EF` + 白卡 `#FDFEFA` + 青松绿星形 `#5B7B6F`；
- 矢量几何与预览图同源（108 视口，白卡 25.4–82.6 / 圆角 11.9，记号居中）；
- 启动屏改用同一个前景矢量（删掉单独画的 `ic_splash_logo.xml`），底色 `splash_background`：浅色纸底 / **values-night 墨底**（暗色下不再闪光）。

### 4.5.0 总结牌桌改版（定高牌框）

之前是"卡片高度随内容"：短卡变成飘在大片空白里的一条横条，邻卡只露 8dp 还被屏幕边缘切掉，
背面还会露出记录原文里的 markdown（`## 标题`）。改版要点（`SummaryDeckScreen.kt`）：

- **定尺卡框**：宽 = 屏宽 − 40dp，高 = min(可用高 × 0.68, 520dp)，圆角 20dp。
- **真堆叠**：邻卡露出 14dp（几何偏移补偿了缩放带来的内收）、缩放 0.94、透明度 0.7；
  当前卡阴影 8dp + 0.5dp 描边。
- **三段结构**：顶部标签条 ／ 内容区（吃掉中间高度，底部信息条自然贴底）／ 底部信息条。
- **字号三档**：正文 ≤20 字用 22sp、≤60 字用 17sp、更长 15sp（`bodyTierOf`）。
- **超长处理**：按"内容区高度 ÷ 行高"算最多几行，超出整行裁剪并在页脚提示「还有约 N 行 · 点开读全文」，
  点开进整屏阅读（信件卡点卡片本体也能进）。
- **背面**：同样定尺，最多 2 条前后记录，其余折成「还有 N 条」；
  原文经 `core/common/PlainText` 清洗（去 `##`、`**`、`>`、`-`，压空行，一行式预览）。
- 引语定位的规范化也顺手放宽：记录原文里的 `#*_>\`` 不再妨碍引语匹配（`SummaryDeckBuilder.NOISE`）。
