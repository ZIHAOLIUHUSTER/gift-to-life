# Gift To Life 记录优先 UI/UX 优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留暖白、低饱和绿色和安静手账气质的前提下，把“打开即记录”做成最短、最可靠的主路径，同时修复搜索、编辑、回忆、总结与设置中的日常体验断点。

**Architecture:** 采用“记录可靠性优先、页面局部重排、共享小组件复用”的渐进方案，不重写导航与数据层。记录编辑使用显式草稿和一次性提交，搜索使用数据库分页与输入防抖，回忆卡片改为内容驱动高度，设置页继续沿用现有页面结构但补齐状态、确认与返回行为。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room、Paging 3、Hilt、WorkManager、JUnit 4、Compose UI Test。

---

## 1. 设计结论

### 1.1 产品优先级

1. 记录必须最快：启动或进入“记录”页后，输入框始终在首屏，保存动作单手可达。
2. 记录必须可靠：保存中不能重复提交；图片删除、替换和正文修改必须在同一次“保存”中提交；离开编辑页不能悄悄丢失修改。
3. 浏览为记录让路：时间线保持简洁，但压缩不必要的空白，让用户更快找到刚才保存的内容。
4. 回忆承担情绪价值：不抢占记录主路径，只在用户主动进入时提供更完整的仪式感。
5. 设置强调可信：保存、测试、导入、恢复和永久删除都必须准确表达真实状态。

### 1.2 保留的视觉资产

- 保留暖白背景、鼠尾草绿主色、低阴影卡片和圆角语言。
- 保留三项底部导航：“回忆 / 记录 / 设置”。
- 保留记录页顶部输入、下方时间线的整体结构。
- 保留“先预览再编辑”的两阶段路径。
- 保留回忆页的摘录感、中文引号和低饱和卡片。

### 1.3 本轮不做

- 不增加账号、云同步、社交、通知或复杂统计。
- 不引入新的大型 UI 框架或图片编辑库。
- 不重做 Room schema，除非为了记录删除时间另开后续任务。
- 不增加多套主题、动态壁纸或装饰动画。
- 不用视觉翻新掩盖备份、并发和发布安全问题；这些仍按独立数据安全计划处理。

## 2. 截图审查摘要

### 2.1 记录页

- 输入区位置正确，但空内容时“保存”仍呈可用状态，点击却没有反馈。
- 图片按钮只有图标，首次使用者不易理解。
- 保存过程中没有进度和防重复提交，图片压缩较慢时可能生成重复记录。
- 外部分享文本可能因为 `remember` 未同步而不出现在输入框。
- 搜索筛选中的“全部”实际表示图片三态，语义不清；搜索无自动聚焦、清空、结果数和空状态。
- 预览页“删除”实际是移至回收站，确认文案却声称无法恢复。
- 编辑页删除或替换图片会立即修改数据库；即使随后关闭编辑页，图片也已经改变。
- 编辑页没有滚动、键盘避让和未保存修改保护。

### 2.2 回忆页

- 固定 `520.dp` 卡片导致无图短文本留下大面积空白，小屏和大字体又可能裁切。
- “本周 / 本月 / 再抽一条”同级，入口含义和主次不清。
- 周/月总结空页面只有“暂无”，没有说明生成日期、记录数量条件或下一步。
- 历史总结只显示三行且不可打开全文。
- 周/月二级页仍显示底部导航，且“回忆”不再选中。

### 2.3 设置页

- 三个入口右侧使用向左箭头，方向错误；图标语义也不准确。
- 模型测试入口使用默认勾号，看起来像“已经测试成功”。
- 两个保存按钮没有成功反馈，`isSaved` 状态未展示。
- 模型配置导入成功后，当前表单可能继续显示旧值。
- 视觉模型测试只发文本请求，不能证明图片能力。
- 数据恢复在选文件前先警告，无法先展示备份内容。
- 回收站“清空”没有二次确认，属于直接永久删除。
- 设置子页只用内部枚举切换，系统返回手势可能跳过设置主页。

## 3. 目标交互

### 3.1 记录主路径

```text
进入记录页
  → 输入框自动获得焦点
  → 输入文字或添加图片
  → 保存按钮变为可用
  → 点击保存后按钮显示处理中并禁止重复点击
  → 数据库写入成功
  → 输入草稿和待选图片清空
  → Snackbar 显示“已记录”
  → 输入框继续保持可输入状态
```

失败时保留正文和图片草稿，并显示可理解的错误消息。任何失败都不能先清空草稿。

### 3.2 编辑主路径

```text
点击记录卡片
  → 预览全文
  → 点击编辑
  → 在本地草稿中修改正文、标签和图片
  → 点击保存
  → 新图片写入私有目录
  → Room 事务更新正文、标签、图片路径和 revision
  → 事务成功后删除旧图片
  → 关闭编辑页并显示“修改已保存”
```

关闭编辑页时，如果草稿发生变化，弹出“放弃修改 / 继续编辑”。图片的删除和替换在用户点击保存前不得影响原记录。

### 3.3 搜索主路径

```text
点击搜索
  → 搜索框自动聚焦
  → 输入停顿 250ms 后刷新分页结果
  → 标签和图片条件改变时滚回结果顶部
  → 显示当前图片条件和空结果提示
  → 关闭搜索后清除全部条件并恢复普通时间线
```

### 3.4 回忆主路径

随机回忆卡只承担“阅读一条记录 + 再抽一条”。周总结、月总结和历史归档移动到卡片下方的“总结与归档”区域，避免在同一操作行混合三种不同任务。

### 3.5 设置主路径

- 配置页使用一份本地草稿和一个“保存更改”动作。
- 模型测试按钮显示文字“测试”，测试中显示进度，结果显示“可用 / 不可用 + 原因”。
- 数据恢复顺序为“选择文件 → 只读校验 → 展示摘要 → 用户确认 → 执行恢复”。
- 永久删除必须显示数量和不可恢复说明。

## 4. 文件结构调整

### 新建文件

- `app/src/main/java/com/gift/tolife/core/ui/component/AppEmptyState.kt`：统一空状态布局。
- `app/src/main/java/com/gift/tolife/core/ui/component/DangerConfirmDialog.kt`：统一永久删除确认。
- `app/src/main/java/com/gift/tolife/core/ui/UiTestTags.kt`：集中管理跨页面测试标记。
- `app/src/main/java/com/gift/tolife/feature/record/EntryEditDraft.kt`：编辑草稿与图片变更状态。
- `app/src/main/java/com/gift/tolife/core/export/BackupInspection.kt`：备份只读摘要与校验结果。
- `app/src/main/java/com/gift/tolife/core/export/BackupArchiveValidator.kt`：有界 ZIP/JSON 校验，不接触正式数据库和图片目录。
- `app/src/main/java/com/gift/tolife/core/network/OpenAiServiceFactory.kt`：按 base URL 创建 Retrofit service，便于并发安全与测试替换。
- `app/src/test/java/com/gift/tolife/feature/record/EntryEditDraftTest.kt`：草稿纯逻辑测试。
- `app/src/androidTest/java/com/gift/tolife/feature/record/RecordScreenTest.kt`：记录、搜索和编辑的关键 UI 测试。
- `app/src/androidTest/java/com/gift/tolife/feature/memory/MemoryScreenTest.kt`：回忆卡与总结空状态测试。
- `app/src/androidTest/java/com/gift/tolife/feature/settings/SettingsScreenTest.kt`：危险操作与配置反馈测试。
- `app/src/test/java/com/gift/tolife/core/export/BackupArchiveValidatorTest.kt`：损坏备份、摘要校验和大小上限测试。
- `app/src/main/res/drawable/ic_chevron_right.xml`：设置入口正确方向图标。
- `app/src/main/res/drawable/ic_delete.xml`：回收站语义图标。
- `app/src/main/res/drawable/ic_backup.xml`：数据管理语义图标。

### 重点修改文件

- `app/src/main/java/com/gift/tolife/feature/record/EntryComposer.kt`
- `app/src/main/java/com/gift/tolife/feature/record/RecordUiState.kt`
- `app/src/main/java/com/gift/tolife/feature/record/RecordViewModel.kt`
- `app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt`
- `app/src/main/java/com/gift/tolife/feature/record/EntryCard.kt`
- `app/src/main/java/com/gift/tolife/feature/record/EntryPreviewSheet.kt`
- `app/src/main/java/com/gift/tolife/feature/record/EditEntryBottomSheet.kt`
- `app/src/main/java/com/gift/tolife/core/database/EntryTransactions.kt`
- `app/src/main/java/com/gift/tolife/core/database/dao/EntryDao.kt`
- `app/src/main/java/com/gift/tolife/core/common/ImageStore.kt`
- `app/src/main/java/com/gift/tolife/feature/memory/MemoryScreen.kt`
- `app/src/main/java/com/gift/tolife/feature/memory/WeekSummaryScreen.kt`
- `app/src/main/java/com/gift/tolife/feature/memory/MonthSummaryScreen.kt`
- `app/src/main/java/com/gift/tolife/navigation/AppNavigation.kt`
- `app/src/main/java/com/gift/tolife/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/gift/tolife/feature/settings/SettingsViewModel.kt`
- `app/src/main/java/com/gift/tolife/core/network/AiClient.kt`
- `app/src/main/java/com/gift/tolife/core/ui/theme/Color.kt`
- `app/build.gradle.kts`

## 5. 实施任务

### Task 1: 建立 UI 测试基础和稳定语义节点

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/gift/tolife/core/ui/UiTestTags.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/EntryComposer.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/memory/MemoryScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: 添加 Compose UI 测试依赖**

在 `dependencies` 中加入：

```kotlin
androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

- [ ] **Step 2: 为关键节点增加稳定测试标记**

使用以下常量，避免测试依赖中文文案：

```kotlin
object UiTestTags {
    const val COMPOSER_INPUT = "composer_input"
    const val COMPOSER_SAVE = "composer_save"
    const val SEARCH_INPUT = "search_input"
    const val SEARCH_IMAGE_FILTER = "search_image_filter"
    const val MEMORY_CARD = "memory_card"
    const val MEMORY_REFRESH = "memory_refresh"
    const val SETTINGS_SAVE = "settings_save"
    const val RECYCLE_CLEAR = "recycle_clear"
}
```

将其放在 `app/src/main/java/com/gift/tolife/core/ui/UiTestTags.kt`，组件分别通过 `Modifier.testTag(UiTestTags.COMPOSER_INPUT)` 等稳定标记使用。

- [ ] **Step 3: 编译 Android 测试源码**

Run:

```powershell
$env:JAVA_HOME='D:\Android_Studio\jbr'
$env:ANDROID_HOME='C:\Users\LZH\AppData\Local\Android\Sdk'
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交测试基础**

```powershell
git add app/build.gradle.kts app/src/main/java/com/gift/tolife/core/ui/UiTestTags.kt app/src/main/java/com/gift/tolife/feature/record/EntryComposer.kt app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt app/src/main/java/com/gift/tolife/feature/memory/MemoryScreen.kt app/src/main/java/com/gift/tolife/feature/settings/SettingsScreen.kt
git commit -m "test: add compose ui test foundation"
```

### Task 2: 让快速记录具备明确状态和防重复保存

**Files:**
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordUiState.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordViewModel.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/EntryComposer.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt`
- Test: `app/src/androidTest/java/com/gift/tolife/feature/record/RecordScreenTest.kt`

- [ ] **Step 1: 写保存按钮状态测试**

```kotlin
@Test
fun saveButton_isDisabledForEmptyComposer_andEnabledForText() {
    var value by mutableStateOf("")
    composeRule.setContent {
        EntryComposer(
            value = value,
            onValueChange = { value = it },
            pendingImageUri = null,
            isSaving = false,
            onSave = {},
            onPickImage = {},
            onClearImage = {}
        )
    }

    composeRule.onNodeWithTag(UiTestTags.COMPOSER_SAVE).assertIsNotEnabled()
    composeRule.onNodeWithTag(UiTestTags.COMPOSER_INPUT).performTextInput("今天很好")
    composeRule.onNodeWithTag(UiTestTags.COMPOSER_SAVE).assertIsEnabled()
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

Expected: `EntryComposer` 缺少 `isSaving` 参数或空内容按钮仍为可用。

- [ ] **Step 3: 扩展记录 UI 状态**

在 `RecordUiState` 增加：

```kotlin
val draftText: String = "",
val draftVersion: Long = 0,
val isSaving: Boolean = false,
val saveError: String? = null
```

`draftText` 是记录输入正文的唯一状态源。`EntryComposer` 不再自己保存另一份正文。正文、待选图片或外部分享每发生一次变化，都将 `draftVersion + 1`。保存成功只清除已提交的那一版草稿；用户在保存期间继续输入形成的新版本必须保留。

保存使用 ViewModel 内的原子门闩，不能只依赖按钮重组：

```kotlin
private val saveMutex = Mutex()

fun saveDraft() {
    if (!saveMutex.tryLock()) return
    viewModelScope.launch {
        try {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            val snapshot = _uiState.value
            saveEntry(snapshot.draftText, snapshot.pendingImageUri)
            _uiState.update {
                if (it.draftVersion == snapshot.draftVersion) {
                    it.copy(
                        draftText = "",
                        pendingImageUri = null,
                        pendingContentText = null,
                        isSaving = false,
                        draftVersion = it.draftVersion + 1
                    )
                } else {
                    it.copy(isSaving = false)
                }
            }
            _events.emit(RecordEvent.EntrySaved)
        } catch (throwable: Throwable) {
            _uiState.update {
                it.copy(isSaving = false, saveError = throwable.message)
            }
            _events.emit(RecordEvent.ShowSnackbar("保存失败，请重试"))
        } finally {
            saveMutex.unlock()
        }
    }
}
```

`saveEntry` 代表现有图片复制与 `repository.save` 逻辑。只有两者都成功后才清草稿。

`onValueChange`、`selectImage`、`clearImage` 和 `ShareReceiver` 都必须同时递增 `draftVersion`。新增测试：开始保存 A 后输入 B，A 保存成功时输入框仍显示 B。

- [ ] **Step 4: 改造输入组件状态**

`EntryComposer` 新签名：

```kotlin
@Composable
fun EntryComposer(
    value: String,
    onValueChange: (String) -> Unit,
    pendingImageUri: Uri?,
    isSaving: Boolean,
    onSave: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit
)
```

保存按钮逻辑：

```kotlin
val canSave = !isSaving && (value.isNotBlank() || pendingImageUri != null)

Button(
    enabled = canSave,
    onClick = onSave,
    modifier = Modifier.testTag(UiTestTags.COMPOSER_SAVE)
) {
    if (isSaving) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        Text("保存")
    }
}
```

- [ ] **Step 5: 把图片入口从无文字图标改为明确动作**

```kotlin
TextButton(onClick = onPickImage) {
    Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null)
    Spacer(Modifier.width(6.dp))
    Text(if (pendingImageUri == null) "添加图片" else "更换图片")
}
```

- [ ] **Step 6: 正确同步外部分享并默认聚焦**

`OutlinedTextField` 必须直接使用受控状态：

```kotlin
OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .testTag(UiTestTags.COMPOSER_INPUT)
)

LaunchedEffect(Unit) {
    focusRequester.requestFocus()
}
```

`ShareReceiver` 收到文本后调用 `_uiState.update { it.copy(draftText = shared.text.orEmpty()) }`。因为输入框由 `draftText` 驱动，分享文本会立即显示。启动路由继续保持 `Screen.Record.route`，保存成功后输入框保持焦点，便于连续记录。

- [ ] **Step 7: 保存成功后给出低打扰反馈**

新增事件：

```kotlin
data object EntrySaved : RecordEvent()
```

`RecordScreen` 收到后显示短 Snackbar“已记录”。不要自动跳页，不要弹对话框。

- [ ] **Step 8: 运行测试**

新增 ViewModel 并发测试：对同一 ViewModel 连续调用两次 `saveDraft()`，Fake Repository 的 `insertCount` 必须等于 1；第一次保存未结束前 `isSaving` 必须为 true。

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest --no-daemon
```

Expected: 单元测试和记录页 UI 测试全部通过。

- [ ] **Step 9: 提交快速记录改造**

```powershell
git add app/src/main/java/com/gift/tolife/feature/record app/src/androidTest/java/com/gift/tolife/feature/record
git commit -m "feat: make quick capture stateful and reliable"
```

### Task 3: 将图片编辑改为草稿后一次提交

**Files:**
- Create: `app/src/main/java/com/gift/tolife/feature/record/EntryEditDraft.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/EditEntryBottomSheet.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordViewModel.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/core/database/EntryTransactions.kt`
- Modify: `app/src/main/java/com/gift/tolife/core/database/dao/EntryDao.kt`
- Modify: `app/src/main/java/com/gift/tolife/core/common/ImageStore.kt`
- Test: `app/src/test/java/com/gift/tolife/feature/record/EntryEditDraftTest.kt`

**Entry condition:** 当前仓库的 `Entry` 和 Room schema v4 已包含 `entryRevision`，本 Task 复用该字段，不新增 schema。执行前检查 `app/src/main/java/com/gift/tolife/core/model/Entry.kt` 与 `app/schemas/com.gift.tolife.core.database.AppDatabase/4.json`；若目标分支缺少该字段，停止本 Task 并先建立独立 Migration，不能只在 Kotlin Entity 中加列。

- [ ] **Step 1: 写草稿状态测试**

```kotlin
class EntryEditDraftTest {
    private val entry = Entry(id = 9, content = "原文", imagePath = "/images/old.webp")

    @Test
    fun removingImage_marksDraftDirty_withoutChangingOriginalEntry() {
        val draft = EntryEditDraft.from(entry).copy(imageChange = ImageChange.Remove)

        assertTrue(draft.isDirtyComparedWith(entry, emptySet()))
        assertEquals("/images/old.webp", entry.imagePath)
    }

    @Test
    fun unchangedDraft_isNotDirty() {
        val draft = EntryEditDraft.from(entry)

        assertFalse(draft.isDirtyComparedWith(entry, emptySet()))
    }
}
```

- [ ] **Step 2: 定义编辑草稿**

```kotlin
sealed interface ImageChange {
    data object Keep : ImageChange
    data object Remove : ImageChange
    data class Replace(val uri: Uri) : ImageChange
}

data class EntryEditDraft(
    val entryId: Long,
    val expectedRevision: Long,
    val content: String,
    val tags: Set<TagType>,
    val originalImagePath: String?,
    val imageChange: ImageChange = ImageChange.Keep
) {
    companion object {
        fun from(entry: Entry, tags: Set<TagType> = emptySet()) = EntryEditDraft(
            entryId = entry.id,
            expectedRevision = entry.entryRevision,
            content = entry.content,
            tags = tags,
            originalImagePath = entry.imagePath
        )
    }

    fun isDirtyComparedWith(entry: Entry, originalTags: Set<TagType>): Boolean {
        return content != entry.content ||
            tags != originalTags ||
            imageChange != ImageChange.Keep
    }
}
```

`isDirtyComparedWith` 必须比较正文、标签和 `imageChange`，不能只比较正文。

- [ ] **Step 3: 编辑页只修改草稿**

删除图片按钮执行：

```kotlin
onDraftChange(draft.copy(imageChange = ImageChange.Remove))
```

替换图片选择完成后执行：

```kotlin
onDraftChange(draft.copy(imageChange = ImageChange.Replace(uri)))
```

点击这些按钮时不得调用 `removeImage` 或 `replaceImage`。

- [ ] **Step 4: 增加滚动、键盘避让和固定保存区**

编辑内容区使用：

```kotlin
ModalBottomSheet(
    onDismissRequest = onRequestDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(formatFullTime(originalEntry.createdAt))
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagType.entries.forEach { tag ->
                    FilterChip(
                        selected = tag in draft.tags,
                        onClick = {
                            val tags = if (tag in draft.tags) draft.tags - tag else draft.tags + tag
                            onDraftChange(draft.copy(tags = tags))
                        },
                        label = { Text(tag.label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val imageModel: Any? = when (val change = draft.imageChange) {
                ImageChange.Keep -> draft.originalImagePath?.let(::File)
                ImageChange.Remove -> null
                is ImageChange.Replace -> change.uri
            }
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "记录图片",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    contentScale = ContentScale.Fit
                )
            }
            OutlinedTextField(
                value = draft.content,
                onValueChange = { onDraftChange(draft.copy(content = it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text("记录内容") }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onSave) { Text("保存") }
        }
    }
}
```

保存按钮放在滚动区外部的底部操作行。标签使用 `FlowRow`，不得假设四个标签永远能在一行显示。

- [ ] **Step 5: 一次性提交草稿**

新增 `RecordViewModel.saveEdit(draft)`：

1. `Keep`：沿用旧路径。
2. `Remove`：数据库提交路径为 `null`。
3. `Replace`：先把新图片写到 `cacheDir/edit-stage/`；失败时保持编辑页和原记录不变。
4. 将暂存图片移动到 `filesDir/images/` 的唯一文件名，得到候选新路径。
5. DAO 更新条件必须包含 `id = :id AND entryRevision = :expectedRevision AND isDeleted = 0`，并将 revision 加一。
6. Room 事务同时更新正文、标签和图片路径。影响行数不是 1 时返回 `RevisionConflict`，删除候选新图片并提示“记录已更新，请重新打开后编辑”。
7. 事务成功后再尝试删除旧图片；删除失败不回滚用户修改，由无主图片清理回收。
8. App 启动后的 IO 任务调用现有 `ImageStore.removeOrphans(referencedPaths)`，回收崩溃或删除失败留下的文件。
9. 用户修改了标签时，不投递 AI 标签任务；只有正文或图片变化且标签未变化时，才以新 revision 投递任务，避免 AI 覆盖人工标签。
10. 成功后发出 `EntryUpdated` 事件。

Room 与文件系统不能组成真正的原子事务，因此验收口径是：任何失败都不能丢失原记录或原图片；新产生的孤儿文件允许短暂存在，但必须在补偿路径或下一次无主图片清理中删除。

- [ ] **Step 6: 关闭时保护未保存修改**

```kotlin
if (draft.isDirtyComparedWith(originalEntry, originalTags)) {
    showDiscardDialog = true
} else {
    onDismiss()
}
```

对话框按钮固定为“继续编辑”和“放弃修改”。

- [ ] **Step 7: 运行测试**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest --no-daemon
```

Expected: 删除图片后关闭编辑页，原记录图片仍存在；点击保存后才删除旧图片。

- [ ] **Step 8: 提交编辑草稿改造**

```powershell
git add app/src/main/java/com/gift/tolife/feature/record app/src/main/java/com/gift/tolife/core/database app/src/test/java/com/gift/tolife/feature/record
git commit -m "fix: commit entry edits as a single draft"
```

### Task 4: 修正预览删除语义并支持撤销

**Files:**
- Modify: `app/src/main/java/com/gift/tolife/feature/record/EntryPreviewSheet.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordViewModel.kt`

- [ ] **Step 1: 将所有软删除文案统一为“移至回收站”**

确认对话框内容：

```text
标题：移至回收站？
正文：这条记录可以稍后在设置 → 回收站中恢复。
确认：移至回收站
取消：取消
```

- [ ] **Step 2: 删除后提供撤销**

事件携带被删除记录 ID：

```kotlin
data class EntryMovedToRecycleBin(val entryId: Long) : RecordEvent()
```

Snackbar：

```kotlin
val result = snackbarHostState.showSnackbar(
    message = "已移至回收站",
    actionLabel = "撤销",
    duration = SnackbarDuration.Long
)
if (result == SnackbarResult.ActionPerformed) {
    viewModel.restore(entryId)
}
```

- [ ] **Step 3: 把预览操作区固定在底部**

正文和图片放在可滚动区域，底部只保留“移至回收站”和“编辑”。长记录无需滚到底才能编辑。

- [ ] **Step 4: 验证行为**

手工验证：短文本、长文本、纯图、图文记录都能在不滚到底的情况下看到操作区；撤销后标签和图片保持不变。

- [ ] **Step 5: 提交删除体验修正**

```powershell
git add app/src/main/java/com/gift/tolife/feature/record
git commit -m "fix: align recycle-bin wording and undo behavior"
```

### Task 5: 重做搜索栏和筛选表达

**Files:**
- Create: `app/src/main/java/com/gift/tolife/core/ui/component/AppEmptyState.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordViewModel.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/core/model/EntryQuery.kt`
- Test: `app/src/androidTest/java/com/gift/tolife/feature/record/RecordScreenTest.kt`

- [ ] **Step 1: 写图片三态语义测试**

```kotlin
@Test
fun imageFilter_exposesAllThreeLabels() {
    composeRule.setContent { ImageFilter(selected = null, onSelected = {}) }

    composeRule.onNodeWithText("全部").assertExists()
    composeRule.onNodeWithText("有图").assertExists()
    composeRule.onNodeWithText("无图").assertExists()
}
```

- [ ] **Step 2: 创建可复用空状态组件**

```kotlin
@Composable
fun AppEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
```

- [ ] **Step 3: 搜索输入增加 250ms 防抖**

ViewModel 保留即时 `searchQuery` 供输入框显示，再让搜索模式控制防抖流的生命周期：

```kotlin
private val searchText = MutableStateFlow("")
private val searchMode = MutableStateFlow(false)

val debouncedSearchText = combine(searchMode, searchText) { active, text ->
    active to text
}.flatMapLatest { (active, text) ->
    if (!active) {
        flowOf("")
    } else {
        flow {
            delay(250)
            emit(text)
        }
    }
}
    .distinctUntilChanged()
```

将防抖后的值写入 `entryQuery.searchText`。`closeSearch()` 先设置 `searchMode.value = false`，该变化通过 `flatMapLatest` 取消排队中的旧关键词，再立即恢复 `EntryQuery()`。

新增协程测试：输入“旧关键词”后在 250ms 内关闭搜索，推进虚拟时间 300ms，最终 `entryQuery` 必须仍为 `EntryQuery()`，旧关键词不得回写。

在使用 `debounce` 的类或属性上添加 `@OptIn(FlowPreview::class)`；使用 `FlowRow` 的 Composable 添加 `@OptIn(ExperimentalLayoutApi::class)`。

- [ ] **Step 4: 搜索框自动聚焦并增加清空按钮**

进入搜索模式后请求焦点并显示软键盘。输入非空时显示尾部清空图标，点击只清空文字，不清空标签和图片条件。

- [ ] **Step 5: 用分段控件表达图片条件**

显示固定标题“图片”，下面三个可见选项：

```text
全部 | 有图 | 无图
```

不要继续用一个“全部”按钮循环切换隐藏状态。

- [ ] **Step 6: 标签筛选使用可换行布局**

使用 `FlowRow`，标签选中态保持现有浅绿色。筛选区底部显示“清除筛选”，只有存在非默认条件时才启用。

- [ ] **Step 7: 增加空结果与加载状态**

```kotlin
when {
    loadState.refresh is LoadState.Loading -> LoadingIndicator()
    loadState.refresh is LoadState.Error -> RetryState(onRetry = lazyPagingItems::retry)
    lazyPagingItems.itemCount == 0 -> AppEmptyState(
        title = "没有找到记录",
        description = "换个关键词或清除部分筛选条件"
    )
}
```

- [ ] **Step 8: 筛选变化后滚回顶部**

使用 `LazyListState`。搜索词防抖完成、标签变化或图片条件变化后调用 `scrollToItem(0)`；关闭搜索后也回到普通列表顶部。

- [ ] **Step 9: 运行测试并提交**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest --no-daemon
git add app/src/main/java/com/gift/tolife/feature/record app/src/main/java/com/gift/tolife/core/model app/src/androidTest/java/com/gift/tolife/feature/record
git commit -m "feat: clarify search and filter interactions"
```

### Task 6: 提升时间线密度但保留图片气质

**Files:**
- Modify: `app/src/main/java/com/gift/tolife/feature/record/EntryCard.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt`

- [ ] **Step 1: 调整普通记录卡片**

- 图片最大高度从 `180.dp` 收敛至 `148.dp`。
- 正文从最多 5 行调整为最多 4 行。
- 卡片内部垂直间距减少约 4dp，但触控区不得小于 48dp。
- 标签使用 `FlowRow` 或最多显示两个并以 `+N` 收束。
- 时间保持弱化，但对比度必须满足可读性。

- [ ] **Step 2: 搜索结果使用更紧凑媒体策略**

搜索模式下图片最大高度为 `112.dp`，正文最多 3 行；普通时间线仍使用 `148.dp`。通过显式参数传递：

```kotlin
enum class EntryCardDensity { TIMELINE, SEARCH }
```

- [ ] **Step 3: 验证典型内容**

至少检查：纯文本一行、纯文本十行、横图、竖图、纯图、两个标签、四个标签、系统字体 1.3 倍。

- [ ] **Step 4: 提交时间线调整**

```powershell
git add app/src/main/java/com/gift/tolife/feature/record
git commit -m "style: improve timeline information density"
```

### Task 7: 将回忆卡改为内容驱动高度

**Files:**
- Modify: `app/src/main/java/com/gift/tolife/feature/memory/MemoryScreen.kt`
- Test: `app/src/androidTest/java/com/gift/tolife/feature/memory/MemoryScreenTest.kt`

- [ ] **Step 1: 写短文本卡片不固定高度测试**

将 `RandomReviewCard` 从 `private` 调整为 `internal`，并测试短文本卡片不再固定为 `520.dp`。

```kotlin
@Test
fun shortMemory_doesNotUseLegacyFixedHeight() {
    composeRule.setContent {
        MaterialTheme {
            RandomReviewCard(
                entry = Entry(content = "短句"),
                tags = emptyList(),
                onRefresh = {}
            )
        }
    }

    val height = composeRule
        .onNodeWithTag(UiTestTags.MEMORY_CARD)
        .getUnclippedBoundsInRoot()
        .height
    assertTrue(height < 520.dp)
}
```

- [ ] **Step 2: 移除固定高度**

将 `.height(520.dp)` 改为：

```kotlin
Modifier
    .fillMaxWidth()
    .heightIn(min = 240.dp)
    .animateContentSize()
```

无图短文本不强行撑满屏幕；长文本仍限制 6 行并显示“查看全文”。

- [ ] **Step 3: 图片采用稳定但不过高的媒体区**

- 横图默认 `16:9`、最大高度 `180.dp`。
- 竖图保持卡片宽度并使用 `ContentScale.Fit`，背景用浅色占位，不裁掉主体。
- 图片可点击进入现有大图预览。

- [ ] **Step 4: 元信息允许换行**

标签使用 `FlowRow`，日期独立一行或在空间不足时自动换行。系统字体 2.0 倍时不能横向溢出。

- [ ] **Step 5: 让“再抽一条”成为卡片唯一主操作**

随机卡片内部移除“本周 / 本月”，保留右对齐的“再抽一条”。刷新期间禁用并显示轻量进度，不让旧内容瞬间消失。

- [ ] **Step 6: 运行测试并提交**

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
git add app/src/main/java/com/gift/tolife/feature/memory app/src/androidTest/java/com/gift/tolife/feature/memory
git commit -m "feat: make memory cards content responsive"
```

### Task 8: 重做总结入口、空状态和全文阅读

**Files:**
- Modify: `app/src/main/java/com/gift/tolife/core/ui/component/AppEmptyState.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/memory/MemoryScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/memory/WeekSummaryScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/memory/MonthSummaryScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/navigation/AppNavigation.kt`

- [ ] **Step 1: 扩展统一空状态组件供总结页使用**

```kotlin
@Composable
fun AppEmptyState(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

沿用 Task 5 已创建的组件；如需图标，增加可选 `Painter?` 参数。布局只包含一个小图标、标题、两行内说明和可选按钮，避免增加装饰插画资源。

- [ ] **Step 2: 在随机卡片下增加“总结与归档”**

提供两个明确入口：

```text
周总结  上周暂无总结 / 已生成 7/7–7/13
月总结  上月暂无总结 / 已生成 6月
```

可生成时显示小圆点或“可生成”，不可生成时仍允许进入查看历史。

- [ ] **Step 3: 周总结空状态说明规则**

非周一：

```text
尚无周总结
每周一可以总结上一周；至少需要 3 条记录。
下一次可生成：7月20日
```

周一但不足 3 条时显示“上周只有 2 条记录，还需要 1 条”。周一且满足条件时显示主按钮“生成上周总结”。

- [ ] **Step 4: 月总结空状态说明规则**

规则与周总结对应，显示每月 1 日、上月记录数量和下一次可生成日期。

- [ ] **Step 5: 历史总结支持全文**

`SummaryCard` 保持三行摘要，但整卡可点击。点击后使用底部页或独立详情页显示完整总结、周期、模型和生成时间。

- [ ] **Step 6: 修正二级页面底部导航**

`AppNavigation` 只在三个一级页面显示底部导航：

```kotlin
val mainRoutes = setOf(Screen.Memory.route, Screen.Record.route, Screen.Settings.route)
val showBottomBar = currentDestination?.route in mainRoutes
```

周/月总结页隐藏底部导航，保留顶栏返回。

- [ ] **Step 7: 全局按时间排序总结**

定义统一归档类型，不让 UI 猜测列表元素：

```kotlin
enum class SummaryKind { WEEK, MONTH }

data class SummaryArchiveItem(
    val entry: Entry,
    val kind: SummaryKind
) {
    val stableKey: String get() = "${kind.name}:${entry.id}"
}

val archives = buildList {
    weekSummaries.forEach { add(SummaryArchiveItem(it, SummaryKind.WEEK)) }
    monthSummaries.forEach { add(SummaryArchiveItem(it, SummaryKind.MONTH)) }
}.sortedByDescending { it.entry.summaryStart ?: it.entry.createdAt }
```

渲染时以 `stableKey` 作为 LazyColumn key。不要只按 `id` 去重；若同一 `Entry` 被错误分类到两组，保留两项能暴露分类问题，而不是静默丢数据。

- [ ] **Step 8: 测试并提交**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest --no-daemon
git add app/src/main/java/com/gift/tolife/core/ui/component app/src/main/java/com/gift/tolife/feature/memory app/src/main/java/com/gift/tolife/navigation
git commit -m "feat: clarify summary generation and archives"
```

### Task 9: 让设置配置具备可信的草稿、保存和测试状态

**Files:**
- Create: `app/src/main/java/com/gift/tolife/core/network/OpenAiServiceFactory.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/gift/tolife/core/network/AiClient.kt`
- Test: `app/src/androidTest/java/com/gift/tolife/feature/settings/SettingsScreenTest.kt`

- [ ] **Step 1: 用单一配置草稿替代五个分散状态**

```kotlin
data class ModelConfigDraft(
    val apiKey: String,
    val baseUrl: String,
    val tagModel: String,
    val summaryModel: String,
    val visionModel: String
)
```

模型测试状态使用显式类型：

```kotlin
sealed interface ModelTestState {
    data object Idle : ModelTestState
    data object Running : ModelTestState
    data object Success : ModelTestState
    data class Failure(val reason: String) : ModelTestState
}
```

ViewModel 同时持有 `savedConfig` 和 `draftConfig`，派生：

```kotlin
val hasUnsavedChanges: Boolean
    get() = draftConfig != savedConfig
```

- [ ] **Step 2: 合并保存动作**

删除两个分散的“保存”，在配置内容末尾提供一个“保存更改”。只有 `hasUnsavedChanges` 为 true 时启用。保存成功发出 Snackbar“配置已保存”。

- [ ] **Step 3: 修正导入后的表单同步**

导入解析成功后同时更新持久化设置、`savedConfig` 和 `draftConfig`，页面立即显示导入值。导入失败不得改动当前草稿。

- [ ] **Step 4: 把测试入口改为显式文字按钮**

每个模型输入框右侧显示：

```text
测试 → 测试中… → 可用
             ↘ 不可用：HTTP 401
```

不要在尚未测试时显示勾号。

- [ ] **Step 5: 测试使用当前草稿配置**

为 `AiClient` 增加不读取 SharedPreferences 的显式配置入口：

```kotlin
data class AiConnectionConfig(
    val apiKey: String,
    val baseUrl: String
)

suspend fun chatWithConfig(
    config: AiConnectionConfig,
    model: String,
    systemPrompt: String,
    userMessage: String,
    disableThinking: Boolean = false
): AiResult<String>

suspend fun describeImageWithConfig(
    config: AiConnectionConfig,
    model: String,
    imageBytes: ByteArray,
    mimeType: String = "image/png"
): AiResult<String>
```

设置页测试传 `draftConfig.apiKey` 和 `draftConfig.baseUrl`，不要求用户先保存。该入口必须基于本次调用的 `baseUrl` 创建或取得线程安全的 service；不得读写当前无同步的 `cachedBaseUrl/cachedService` 组合，避免并发测试把请求发到错误端点。

- [ ] **Step 6: 视觉模型走真实图片请求**

运行时生成一个 32×32 的双色 PNG，避免新增图片资源：

```kotlin
private fun createVisionProbe(): ByteArray {
    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint().apply { color = android.graphics.Color.rgb(94, 125, 112) }
    canvas.drawRect(0f, 0f, 16f, 32f, paint)
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }
}
```

调用 `describeImageWithConfig(draftConnection, draftConfig.visionModel, createVisionProbe())`。成功标准是收到非空描述；普通文本聊天成功不能标记视觉模型“可用”。为 `AiClient` 注入可替换的 `OpenAiServiceFactory`，测试中使用 Fake Service，不发真实网络请求。

- [ ] **Step 7: 增加键盘和离开保护**

配置页根布局增加 `.imePadding()`；输入框设置 IME Next/Done。定义共享离开入口：

```kotlin
fun requestLeaveSettingsPage() {
    if (uiState.hasUnsavedChanges) {
        showDiscardChangesDialog = true
    } else {
        navigateToSettingsMain()
    }
}
```

配置页顶栏返回和系统返回都调用它。确认“放弃修改”后先把草稿恢复为 `savedConfig`，再调用 `navigateToSettingsMain()`。

- [ ] **Step 8: 运行测试并提交**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest --no-daemon
git add app/src/main/java/com/gift/tolife/feature/settings app/src/main/java/com/gift/tolife/core/network app/src/androidTest/java/com/gift/tolife/feature/settings
git commit -m "feat: make model configuration state trustworthy"
```

### Task 10: 修正设置入口、返回行为和回收站危险操作

**Files:**
- Create: `app/src/main/res/drawable/ic_chevron_right.xml`
- Create: `app/src/main/res/drawable/ic_delete.xml`
- Create: `app/src/main/res/drawable/ic_backup.xml`
- Create: `app/src/main/java/com/gift/tolife/core/ui/component/DangerConfirmDialog.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: 替换设置入口图标**

- 行尾统一使用向右 `chevron`。
- 模型配置使用密钥或调节图标。
- 数据管理使用备份图标。
- 回收站使用垃圾桶图标。
- 右侧可附加“已配置”或“3 条”，但不得与箭头混淆。

- [ ] **Step 2: 接管设置子页系统返回**

```kotlin
BackHandler(enabled = currentPage != SettingsPage.MAIN) {
    if (currentPage == SettingsPage.MODEL_CONFIG) {
        requestLeaveSettingsPage()
    } else {
        currentPage = SettingsPage.MAIN
    }
}
```

`requestLeaveSettingsPage()` 复用 Task 9 的同一实现，不允许顶栏和系统返回各写一份判断。

- [ ] **Step 3: 创建统一永久删除对话框**

```kotlin
@Composable
fun DangerConfirmDialog(
    title: String,
    description: String,
    confirmLabel: String,
    isProcessing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
)
```

确认按钮使用错误色。确认按钮设置 `enabled = !isProcessing`，取消按钮同样设置 `enabled = !isProcessing`；处理中显示小型进度并禁止关闭对话框。

- [ ] **Step 4: 回收站清空增加数量确认**

文案必须包含实际数量：

```text
永久删除 3 条记录？
相关图片也会被删除，此操作无法撤销。
取消 | 永久删除 3 条
```

- [ ] **Step 5: 改善回收站条目表达**

- 纯图记录显示缩略图或“图片记录”。
- 正文显示最多两行。
- 时间标注“创建于 MM-dd HH:mm”。
- 保留“恢复”；单条永久删除可作为后续次级动作。

- [ ] **Step 6: 改善空状态**

```text
回收站为空
移除的记录会暂时保留在这里。
```

- [ ] **Step 7: 测试并提交**

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
git add app/src/main/java/com/gift/tolife/feature/settings app/src/main/java/com/gift/tolife/core/ui/component app/src/main/res/drawable
git commit -m "fix: protect destructive settings actions"
```

### Task 11: 将数据恢复改为先检查后确认

**Files:**
- Create: `app/src/main/java/com/gift/tolife/core/export/BackupInspection.kt`
- Create: `app/src/main/java/com/gift/tolife/core/export/BackupArchiveValidator.kt`
- Create: `app/src/test/java/com/gift/tolife/core/export/BackupArchiveValidatorTest.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/gift/tolife/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/gift/tolife/core/export/ExportImportManager.kt`

**Entry condition:** 本 Task 自身必须先完成备份导入 P0 安全加固，再连接恢复 UI；不得把安全加固留给外部计划。所有 ZIP 条目采用有上限的流式读取、验证 `imageSha256`、新图片在数据库成功前只存在于临时目录、任一失败路径清理临时文件。

- [ ] **Step 1: 定义只读备份摘要**

```kotlin
data class BackupInspection(
    val formatVersion: Int,
    val entryCount: Int,
    val imageCount: Int,
    val exportedAt: Long?,
    val warnings: List<String>
)
```

- [ ] **Step 2: 写有界校验器失败测试**

测试夹具在测试临时目录中动态创建，至少覆盖：

```kotlin
@Test
fun inspect_rejectsImageWhoseShaDoesNotMatchManifest() {
    val backup = backupFixture(
        manifestSha256 = "000000",
        actualImageBytes = "real-image".encodeToByteArray()
    )

    assertFailsWith<BackupValidationException> {
        validator.inspect(backup)
    }
}

@Test
fun inspect_stopsWhenExpandedEntryExceedsLimit() {
    val backup = compressedBackupWithExpandedImageBytes(MAX_SINGLE_IMAGE_BYTES + 1)

    assertFailsWith<BackupLimitExceededException> {
        validator.inspect(backup)
    }
}
```

`backupFixture` 和 `compressedBackupWithExpandedImageBytes` 都定义在 `BackupArchiveValidatorTest.kt`，使用 `ZipOutputStream` 创建临时 ZIP，不依赖仓库中的二进制夹具。

- [ ] **Step 3: 实现有界流复制和摘要校验**

```kotlin
private fun copyBounded(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long,
    digest: MessageDigest? = null
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return total
        total += read
        if (total > maxBytes) throw BackupLimitExceededException(maxBytes)
        output.write(buffer, 0, read)
        digest?.update(buffer, 0, read)
    }
}
```

manifest、旧版 JSON 和图片全部使用有界读取。每个声明图片必须存在且 SHA-256 相符；ZIP 中未声明的图片产生 warning，不写入正式目录。

v1 兼容策略必须明确区分：v2 ZIP 校验 manifest、声明图片和 SHA-256；v1 是单 JSON 内嵌 Base64，没有独立 manifest/hash，因此采用“有界兼容导入”。先限制整个 JSON 文件大小，再在 Base64 解码前按字符串长度估算解码后大小，超过单图或总量上限立即拒绝；解码后再次检查实际字节数。`BackupInspection.warnings` 显示“旧版备份不含完整性摘要，将按兼容模式恢复”。不得因为缺少 v2 hash 拒绝所有合法 v1 文件。

新增两项测试：合法小型 v1 能生成摘要并导入；超限 Base64 v1 在解码前被拒绝且数据库未变化。

- [ ] **Step 4: 增加只读检查 API**

```kotlin
suspend fun inspectBackup(uri: Uri): BackupInspection
```

该方法只复制到临时文件并校验格式、版本、条目数、图片声明、每张图片大小和 SHA-256，不写数据库、不写正式图片目录。所有读取必须在超过上限时立即停止，不能先 `readBytes()` 再检查大小。检查结束删除临时文件。

- [ ] **Step 5: 暂存图片并实现补偿式提交**

导入时图片先解压到 `cacheDir/import-stage-<uuid>/`。完整校验通过后按以下固定顺序执行：

1. 为每张图片生成 `filesDir/images/` 下的唯一候选路径并移动候选文件。
2. 用候选路径构建 `StagedImportEntry`。
3. 在单个 Room 事务中替换数据库。
4. 事务失败时立即删除全部候选文件，旧数据库和旧图片保持不变。
5. 事务成功后删除旧图片。
6. App 启动时运行 `removeOrphans`，回收进程在步骤 1–3 之间终止所留下的候选文件。
7. 无论成功失败，都在 `finally` 删除 stage 目录。

- [ ] **Step 6: 调整 UI 流程**

数据管理页先打开文件选择器。检查成功后显示：

```text
备份时间：2026-07-15 14:00
记录：1,284 条
图片：326 张
格式：Gift To Life v2

恢复将替换当前设备上的全部记录。
取消 | 恢复此备份
```

- [ ] **Step 7: 显示进度和结果**

恢复期间禁用按钮并显示“正在校验 / 正在写入 / 正在整理图片”。成功显示导入数量；失败说明现有数据未改变。

- [ ] **Step 8: 验证失败路径**

分别使用：正常 v2、旧 v1、损坏 JSON、缺 manifest、缺图片、超限图片。所有失败路径都必须保持原数据库和图片不变。

- [ ] **Step 9: 运行测试并提交恢复流程调整**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest --no-daemon
```

Expected: 摘要不匹配、超限、缺文件和损坏格式测试全部通过；UI 失败路径保留原数据。

```powershell
git add app/src/main/java/com/gift/tolife/feature/settings app/src/main/java/com/gift/tolife/core/export app/src/test/java/com/gift/tolife/core/export
git commit -m "feat: inspect backups before restoring data"
```

### Task 12: 微调颜色层级、字体和可访问性

**Files:**
- Modify: `app/src/main/java/com/gift/tolife/core/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/gift/tolife/core/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/gift/tolife/core/ui/theme/Type.kt`
- Modify: touched Compose screens from Tasks 2–11

- [ ] **Step 1: 提高表面层级差**

保持暖白和绿色色相，只拉开背景、卡片和输入框的明度差。优先调整 `surfaceVariant`，不要引入新的高饱和强调色。

- [ ] **Step 2: 加深说明文字**

正文和必要说明不得使用过低 alpha。禁用状态可以弱化，但备份警告、生成条件、时间和错误原因必须清楚可读。

- [ ] **Step 3: 统一形状**

- 主卡片：12dp 圆角。
- 输入框和普通按钮：8dp 圆角。
- 标签：小胶囊或 6dp 圆角。
- 不把所有区域都包成大卡片；设置页说明文字可直接放在背景上。

- [ ] **Step 4: 校验触控与无障碍语义**

- 所有点击目标至少 48dp。
- API Key 图标说明根据状态变化为“显示 API Key / 隐藏 API Key”。
- 图标按钮不可只写“切换”“按钮”。
- 动态结果通过可读文字表达，不能只靠颜色和勾号。

- [ ] **Step 5: 运行 Lint**

```powershell
.\gradlew.bat lintDebug --no-daemon
```

Expected: 不新增无障碍、硬编码、触控尺寸或 Compose 状态警告。

- [ ] **Step 6: 提交视觉微调**

```powershell
git add app/src/main/java/com/gift/tolife/core/ui/theme/Color.kt app/src/main/java/com/gift/tolife/core/ui/theme/Theme.kt app/src/main/java/com/gift/tolife/core/ui/theme/Type.kt app/src/main/java/com/gift/tolife/feature/record/EntryComposer.kt app/src/main/java/com/gift/tolife/feature/record/EntryCard.kt app/src/main/java/com/gift/tolife/feature/record/RecordScreen.kt app/src/main/java/com/gift/tolife/feature/record/EntryPreviewSheet.kt app/src/main/java/com/gift/tolife/feature/record/EditEntryBottomSheet.kt app/src/main/java/com/gift/tolife/feature/memory/MemoryScreen.kt app/src/main/java/com/gift/tolife/feature/memory/WeekSummaryScreen.kt app/src/main/java/com/gift/tolife/feature/memory/MonthSummaryScreen.kt app/src/main/java/com/gift/tolife/feature/settings/SettingsScreen.kt
git commit -m "style: refine visual hierarchy and accessibility"
```

### Task 13: 完整回归和真机体验验收

**Files:**
- Modify: `docs/release-checklist.md`

- [ ] **Step 1: 运行静态与单元验证**

```powershell
$env:JAVA_HOME='D:\Android_Studio\jbr'
$env:ANDROID_HOME='C:\Users\LZH\AppData\Local\Android\Sdk'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Expected: 全部成功；Lint 不新增错误。

- [ ] **Step 2: 运行设备测试**

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

Expected: 记录、搜索、编辑、回忆、设置测试全部通过。

- [ ] **Step 3: 执行真机矩阵**

| 场景 | 必须满足 |
|---|---|
| 360×640dp 小屏 | 编辑页保存按钮不被键盘遮挡 |
| 当前设备竖屏 | 截图中的三组页面无裁切和错误空白 |
| 横屏 | 底部导航、弹层和输入框可用 |
| fontScale 1.0 | 视觉基线稳定 |
| fontScale 1.3 | 标签自动换行，按钮不重叠 |
| fontScale 2.0 | 所有关键操作可滚动到达 |
| 纯文字短记录 | 保存、预览、编辑、删除、撤销完整 |
| 2,000 字长记录 | 预览和编辑可滚动，操作区可达 |
| 横图/竖图/纯图 | 不丢图、不误裁主体、可查看大图 |
| 快速连续点击保存 | 只创建一条记录 |
| 外部分享文本/图片 | 草稿正确显示，原有输入处理符合设计 |
| 搜索连续输入 | 无明显闪烁，250ms 后更新 |
| 回收站清空 | 必须二次确认并显示数量 |
| 数据恢复失败 | 原记录和图片完全不变 |

- [ ] **Step 4: 对照原始截图重新截图**

按 `image/记录界面`、`image/回忆界面`、`image/设置界面` 相同路径和内容状态拍摄新版截图。重点比较：首屏信息密度、空状态、按钮语义、键盘遮挡和固定高度空白。

- [ ] **Step 5: 更新发布清单**

在 `docs/release-checklist.md` 增加：

```markdown
- [ ] 空草稿时保存按钮禁用
- [ ] 保存中不可重复提交
- [ ] 编辑图片仅在保存后生效
- [ ] 软删除文案为“移至回收站”且支持撤销
- [ ] 回收站清空有数量确认
- [ ] 搜索自动聚焦、可清空、图片三态可见
- [ ] 回忆卡无固定高度空白或裁切
- [ ] 周/月总结空状态解释生成条件
- [ ] 模型测试使用当前草稿配置
- [ ] 数据恢复先检查再确认
- [ ] fontScale 1.3 和 2.0 可完成全部关键操作
```

- [ ] **Step 6: 最终提交**

```powershell
git add docs/release-checklist.md
git commit -m "docs: add record-first ui acceptance baseline"
```

## 6. 执行顺序与发布切片

### Slice A：记录可靠性

包含 Task 1–4。完成后即可单独发布，直接改善重复保存、草稿同步、图片误删和回收站语义。不得等待视觉微调再发布这些修复。

### Slice B：查找效率

包含 Task 5–6。完成后搜索条件可理解，时间线更适合数千条记录。

### Slice C：回忆体验

包含 Task 7–8。移除固定 520dp，补齐总结入口、空状态和全文阅读。

### Slice D：设置可信度

包含 Task 9–11。修复保存反馈、模型测试、系统返回、永久删除和恢复流程。

### Slice E：统一收尾

包含 Task 12–13。只做视觉层级、可访问性和完整回归，不再加入新功能。

## 7. 总体验收标准

- 用户从进入记录页到完成纯文字记录只需要“输入 + 保存”。
- 空内容时保存按钮明确禁用；保存中不可重复触发。
- 保存失败不会清除草稿、待选图片或当前焦点。
- 图片删除和替换在点击编辑页“保存”前不改变原记录。
- 所有软删除入口准确说明可从回收站恢复。
- 搜索图片条件不再依赖循环猜测；空结果有解释。
- 回忆卡短内容不出现大面积固定空白，长内容和大字体不裁切关键操作。
- 周/月总结页面在任何日期都能解释当前状态和下一步。
- 历史总结可以阅读全文。
- 设置保存和模型测试的状态与实际行为一致。
- 回收站清空、数据覆盖等不可逆动作均需明确二次确认。
- 320–360dp 宽、小屏、横屏和 fontScale 2.0 下仍可完成记录、编辑、搜索、恢复和配置。
- 本轮修改不改变现有数据格式，不降低备份兼容性，不增加 APK 中的大型依赖。

## 8. 给执行模型的约束

- 每个 Task 独立完成、测试和提交，不并行编辑同一 Compose 文件。
- 先写失败测试或可复现步骤，再改实现。
- 不在 UI 任务中顺带更换数据库、网络库或导航框架。
- 保留用户工作区中的未跟踪文件，不执行 `git reset --hard` 或覆盖用户修改。
- 如果视觉实现与本文冲突，以“记录最快、草稿不丢、危险操作准确”为最高判断标准。
- 如果某项需要扩大数据模型或新增迁移，先停止该项并单独写迁移设计，不把 schema 变更夹在 UI 提交中。
