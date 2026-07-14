# Gift To Life 🎁

极简 Android 个人闪念记录工具。像在旧便签纸上随手写下一句话——安静、温暖、AI 辅助。

## 功能

### 记录
- 📝 **快速记录** — 打开即写，文本 + 图片（WebP 压缩，max 1920px，质量 85%）
- 📷 **纯图记录** — 没有文字也能保存，视觉模型自动生成描述
- 📤 **系统分享** — 从浏览器、备忘录等 App 直接分享进来
- 🏠 **桌面 Widget** — 一键打开 App

### AI 智能
- 🏷️ **自动打标签** — 保存后异步打标签（闪念/事记/情绪/知识），不阻塞操作
- 👁️ **图像识别** — 纯图记录自动识别内容，描述参与标签和总结生成
- 📊 **周总结** — 每周一生成上周总结，两种风格随机：时光切片 / 情绪天气
- ✉️ **月总结** — 每月 1 号生成上月总结，老友来信风格
- 🔒 **并发安全** — AI 编辑不会覆盖你的手工修改

### 回忆
- 🎲 **随机回顾** — 骰子盲盒式随机抽取，响应式卡片自适应内容
- 📜 **历史归档** — 周/月总结按时间归档，显示时间段和模型

### 搜索
- 🔍 **全文搜索** — 数据库级 LIKE 查询
- 🏷️ **标签筛选** — 多选 Chip，FlowRow 自适应换行
- 🖼️ **图片筛选** — 全部 / 有图 / 无图三态切换

### 数据
- 📦 **导出 v2 ZIP** — `.gtlbackup` 格式，manifest.json + images/，流式读写
- 📥 **导入校验** — 先校验再写入，损坏备份不破坏现有数据
- ♻️ **回收站** — 软删除，标签完整恢复，图片文件同步清理
- 🔄 **兼容 v1** — 旧 JSON 格式只读导入，提示缺少元数据

### 配置
- 🤖 **多模型** — 标签、总结、视觉三个模型独立配置
- 🔌 **测试连通** — 每个模型单独测试，成功✅ / 失败✗ 即时反馈
- 📤 **导入导出** — 模型配置独立导入导出（不含 API Key）

## 技术栈

| 层 | 技术 |
|----|------|
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + Repository |
| DI | Dagger Hilt |
| 数据库 | Room + SQLite（无损 Migration 1→3→4） |
| 图片 | Coil + WebP 压缩 |
| 后台 | WorkManager（标签/视觉异步，唯一任务防重复） |
| 列表 | Paging 3 + SQL 下推（LEFT JOIN 一次查带标签） |
| AI | Retrofit + OkHttp，OpenAI 兼容 Chat Completions API |
| 备份 | ZIP（JsonReader/Writer 流式处理） |

## 构建

```bash
# 环境
set JAVA_HOME=D:\Android_Studio\jbr
set ANDROID_HOME=C:\Users\LZH\AppData\Local\Android\Sdk

# Debug（未混淆，~18MB）
gradlew assembleDebug

# Release（R8 混淆，~1.7MB）  
gradlew assembleReleaseTest
```

需要 JDK 21 + Android SDK 34。

## 开发历程

本项目由 Qwen Code (AI Agent) 与开发者协作完成，经过 **9 阶段 + 15 个修改任务**：

| 阶段 | 内容 |
|------|------|
| 0 | 工程脚手架 |
| 1 | 记录 CRUD |
| 2 | 图片 + WebP |
| 3 | 搜索 + 筛选 |
| 4 | 回忆页 |
| 5 | 设置页 |
| 6 | AI 标签 |
| 7 | 周/月总结 |
| 9 | 导出/导入/分享/Widget/回收站 |
| 修改 | Migration、事务化、Worker 竞态、Paging 3、R8 瘦身 |

设计文档见 `01-产品与架构设计.md`、`02-UI参考设计.md`、`03-阶段实施计划.md`。

## 许可证

MIT
