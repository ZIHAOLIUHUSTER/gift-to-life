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
- versionCode: 30, versionName: 3.0.0
- APK: debug ~18MB, release ~1.75MB（R8 已开启）
- 数据库: 无损 Migration 1→3→4, `fallbackToDestructiveMigration` 已移除

## 核心功能
1. 记录 CRUD + 图片（WebP 压缩）
2. AI 标签（WorkManager + revision 并发保护 + 重试）
3. 周/月总结（周一/1号限时生成，时间序采样，48K 预算，去重）
4. 搜索 + 筛选（Paging 3 + 防抖 + FlowRow）
5. 回忆页（460dp 固定卡片，双模板，Crossfade）
6. 那年今日（不限年份，详情页）
7. 记录统计（热力图 + 连续天数 + 使用统计）
8. 导出/导入（v2 ZIP + 哈希校验 + staging + 原子事务）
9. 回收站（软删除 + 标签恢复）
10. 深浅色主题（system/light/dark 响应式切换）
11. Widget + 分享接入

## v3.0.0 补漏修复（2026-07-16）
- P0: 安全原子导入（流式复制 + 路径穿越校验 + SHA-256 + 事务替换）
- P0: 纯图片保存（EntrySavePolicy 统一校验）
- P1: 手工标签不再被 AI 覆盖
- P1: 旧全量链路已移除，Paging 切换完成
- P1: AI 失败重试（RetryableFailure → retry）
- P1: 周/月总结去重（使用 previousWeekRange/previousMonthRange）
- P1: 图片删除顺序（先更新 DB 再删文件）
- P1: 导出排除已删除条目
- P1: 配置导出不含 API Key
- P2: BaseUrlValidator 强制 HTTPS
- P2: 主题响应式切换
- P2: 设置页 BackHandler 修复回退栈
- P2: 枚举序列化注释
- 性能: 开启 R8（APK 18MB → 1.75MB）
- 测试: 新增 TimeUtil 周日边界 + 闰年 + Migration 测试

## 关键文件
- `app/src/main/java/com/gift/tolife/feature/record/` — 记录页
- `app/src/main/java/com/gift/tolife/feature/memory/` — 回忆页
- `app/src/main/java/com/gift/tolife/feature/settings/` — 设置页
- `app/src/main/java/com/gift/tolife/core/database/` — 数据层
- `app/src/main/java/com/gift/tolife/core/ai/` — AI 标签/总结
- `app/src/main/java/com/gift/tolife/core/export/` — 导入导出
- `app/src/main/java/com/gift/tolife/core/network/` — Retrofit + 校验

## 开发规则
- 先列计划再动手
- 每阶段编译通过 + 审查 + git commit
- 不改数据结构时不升 DB 版本
- 找不到工具路径先问