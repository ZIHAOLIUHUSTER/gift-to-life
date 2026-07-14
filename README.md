# Gift To Life 🎁

极简 Android 个人闪念记录工具。像在旧便签纸上随手写下一句话——安静、温暖、AI 辅助。

## 功能

- 📝 **快速记录** — 文本 + 图片（WebP 压缩），Widget 一键进入
- 🏷️ **AI 自动打标签** — 闪念 / 事记 / 情绪 / 知识，保存即打
- 👁️ **图像识别** — 纯图记录自动生成文字描述，参与标签和总结
- 📊 **周/月总结** — 时光切片、情绪天气、老友来信三种风格
- 🎲 **随机回顾** — 骰子盲盒，卡片式沉浸回忆
- 🔍 **搜索筛选** — 全文搜索 + 标签多选 + 有无图片
- ♻️ **回收站** — 软删除，可恢复
- 📦 **导出导入** — JSON 格式，图片 base64 内嵌，时间戳保留
- 🏠 **桌面 Widget** — 点击直接打开记录
- 📤 **系统分享** — 从其他 App 分享内容过来

## 技术栈

| 层 | 技术 |
|----|------|
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + Repository + Flow |
| DI | Dagger Hilt |
| 数据库 | Room + SQLite |
| 图片 | Coil + WebP 压缩（max 1920px, 85% 质量） |
| 后台 | WorkManager（标签/视觉识别异步） |
| AI | OpenAI 兼容 Chat Completions API（DeepSeek / SiliconFlow / 自定义） |

## 开发历程

本项目由 Qwen Code (AI Agent) 与一位开发者协作完成，共经历 **9 个阶段**：

| 阶段 | 内容 | Git Tag |
|------|------|---------|
| 0 | 工程脚手架 + Compose/Hilt/Room 依赖 | `v0.1.0-phase0` |
| 1 | 记录 CRUD + 时间流列表 | `v0.2.0-phase1` |
| 2 | 图片选择 + WebP 压缩 | `v0.3.0-phase2` |
| 3 | 搜索 + 标签筛选 | `v0.4.0-phase3` |
| 4 | 回忆页 + 随机回顾 | `v0.5.0-phase4` |
| 5 | 设置页 + DataStore 持久化 | `v0.6.0-phase5` |
| 6 | AI 标签 + 视觉模型 | `v0.7.0-phase6` |
| 7 | 周/月 AI 总结 | `v0.8.0-phase7` |
| 9 | 导出/导入 + 分享 + Widget + 回收站 | `v1.0.0` |

设计文档见 `01-产品与架构设计.md`、`02-UI参考设计.md`、`03-阶段实施计划.md`。

## 构建

```bash
# 设置环境变量
set JAVA_HOME=%JAVA_HOME%
set ANDROID_HOME=%ANDROID_HOME%

# 编译
gradlew assembleDebug

# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

需要 Android SDK 34 + JDK 21。

## 许可证

MIT
