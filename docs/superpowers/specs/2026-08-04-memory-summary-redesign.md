# 回忆页总结体验重构设计

日期: 2026-08-04
状态: 已批准

## 目标
1. 移除回忆主页的「最近总结」与「历史总结」独立模块，恢复三个 tab 的视觉一致性。
2. 总结的查看/生成全部收进「周总结」「月总结」两个子页。
3. 子页内历史总结可点击打开查看全文（复用 EntryPreviewSheet）。
4. 重构唯一性生成逻辑：移除「仅周一/仅每月1号」限制，改为「任意时间可补生成上一个完整周期」，同时保证每周期唯一、空白周期不生成。
5. 轻量美化展示。

## 1. 回忆主页（MemoryScreen）
- 删除「最近总结」卡片（`summary_preview` item）。
- 删除「历史总结」区块（`summary_header` + `items(allSummaries)`）。
- 保留：随机回顾卡、`周总结`/`月总结` 按钮、那年今日。

## 2. 周/月总结子页（WeekSummaryScreen / MonthSummaryScreen）
- 顶部「总结上周 / 总结上月」按钮**始终显示**（不再受 `canGenerateWeek/Month` 限制）。
- 历史总结列表每条可点击 → `EntryPreviewSheet` 查看全文（`onEdit=null`、`onDelete` 软删除）。
- `SummaryCard` 增加 `.clickable` 与右箭头。

## 3. 唯一性逻辑（SummaryViewModel）
移除 `TimeUtil.isMonday()` / `isFirstDayOfMonth()` 门槛与 `canGenerateWeek/Month` 状态。

```
generateWeekSummary():
  (start, end) = TimeUtil.previousWeekRange()
  doGenerate(start, end, weekPrompt, "上周记录不足 3 条，无法生成总结")

generateMonthSummary():
  (start, end) = TimeUtil.previousMonthRange()
  doGenerate(start, end, MONTH_LETTER, "上月记录不足 3 条，无法生成总结")

doGenerate(start, end, prompt, insufficientMsg):
  entries = getNormalEntriesInRange(start, end)
  if buildUserPrompt(entries) == null -> 提示 insufficientMsg   # 空白周期不生成
  existing = summaries.find { it.summaryStart==start && it.summaryEnd==end }
  if existing != null -> repository.update(...)   # 同周期覆盖 → 唯一
  else -> repository.save(...)
```

- 唯一性：`(summaryStart, summaryEnd)` 精确匹配，重复生成只覆盖。
- 非空：`>= 3 条` 门槛（buildUserPrompt 内部已有该判断）。

## 4. 展示美化（轻量）
- 周总结卡：周期 chip（`M/d - M/d`）。
- 月总结卡：月份名（`M月`）+ 信笺引号排版。
- 预览弹层全文用 `bodyLarge`（已是）。

## 涉及文件
- `feature/memory/MemoryScreen.kt`（删两个区块）
- `feature/memory/WeekSummaryScreen.kt`（按钮常驻 + 可点击 + 预览）
- `feature/memory/MonthSummaryScreen.kt`（同上）
- `feature/memory/SummaryViewModel.kt`（唯一性逻辑）
- `feature/memory/MemoryScreen.kt` 的 `SummaryCard`（clickable + 箭头）

## 不做
- 不新增全屏阅读页（复用预览弹层）。
- 不改 Room schema。
- 不动随机回顾、那年今日。
