# Gift To Life 开发摘要

## 项目概览
极简 Android 个人闪念记录工具。Kotlin + Jetpack Compose + Room + Hilt。

## 环境
- JDK 21: `D:\Android_Studio\jbr`
- Android SDK: `C:\Users\LZH\AppData\Local\Android\Sdk`
- 项目路径: `D:\gift`
- 编译: `gradlew assembleDebug`
- GitHub: https://github.com/ZIHAOLIUHUSTER/gift-to-life
- Push: `git push origin master`（需 VPN 代理 127.0.0.1:7892）

## 架构
- 包名: `com.gift.tolife`
- 单 App 模块，MVVM + Repository
- 数据库: Room v4, `Entry`(含 isDeleted/summaryModel/entryRevision), `EntryTag`
- 设置: SharedPreferences（`gift_settings`）
- 图片: `filesDir/images/`, WebP 压缩 1920px 85%

## 当前版本
- versionCode: 20, versionName: 2.0.0
- APK: debug ~18MB, release ~18MB（R8 关闭，debug 签名）
- 数据库: 无损 Migration 1→3→4, `fallbackToDestructiveMigration` 已移除

## 核心功能
1. 记录 CRUD + 图片（WebP 压缩）
2. AI 标签（WorkManager + revision 并发保护）
3. 周/月总结（周一/1号限时生成，时间序采样，48K 预算）
4. 搜索 + 筛选（Paging 3 + 防抖 + FlowRow）
5. 回忆页（460dp 固定卡片，双模板，Crossfade）
6. 那年今日（不限年份，详情页）
7. 记录统计（热力图 + 连续天数 + 使用统计）
8. 导出/导入（v2 ZIP + 哈希校验 + staging）
9. 回收站（软删除 + 标签恢复）
10. 深浅色主题（system/light/dark 切换）
11. Widget + 分享接入

## 关键文件
- `app/src/main/java/com/gift/tolife/feature/record/` — 记录页
- `app/src/main/java/com/gift/tolife/feature/memory/` — 回忆页
- `app/src/main/java/com/gift/tolife/feature/settings/` — 设置页
- `app/src/main/java/com/gift/tolife/core/database/` — 数据层
- `app/src/main/java/com/gift/tolife/core/ai/` — AI 标签/总结
- `app/src/main/java/com/gift/tolife/core/export/` — 导入导出
- `app/src/main/java/com/gift/tolife/core/network/` — Retrofit
- `app/src/main/java/com/gift/tolife/navigation/` — 导航

## 开发规则
- 先列计划再动手
- 每阶段编译通过 + 审查 + git commit
- 不改数据结构时不升 DB 版本
- 找不到工具路径先问