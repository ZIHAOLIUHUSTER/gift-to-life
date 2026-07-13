---
name: crash-isolation
description: 当 try-catch(Throwable)+UncaughtExceptionHandler 都拦不住闪退时，用模拟替代法逐层隔离根因（先隔离网络层，再隔离协程层，最后隔离 UI 层）
source: auto-skill
extracted_at: '2026-07-13T03:15:31.214Z'
---

# Crash Isolation Diagnostic（闪退隔离诊断）

## 适用场景

App 点击某个按钮或执行某项操作时闪退，且：
- 已用 `try-catch(Throwable)` 包裹了可疑代码
- 已设置 `Thread.setDefaultUncaughtExceptionHandler` 记录异常
- 但闪退仍然发生，没有异常日志

**这种情况说明崩溃不是 Java/Kotlin 异常，而是系统级杀进程**（如缺少权限、native crash、OOM）。

## 诊断步骤

### Step 1：检查权限

Android 最常见的"隐形闪退"原因：**缺少 `INTERNET` 权限**。

```bash
# 检查 Manifest 是否有这一行
<uses-permission android:name="android.permission.INTERNET" />
```

缺失时，OkHttp/Retrofit 发起网络请求会直接触发系统安全策略杀进程，不抛 Java 异常。

其他常见缺失权限：`CAMERA`、`READ_EXTERNAL_STORAGE`、`POST_NOTIFICATIONS`（Android 13+）。

### Step 2：模拟替代法（隔离网络层）

把可能出问题的调用替换为纯模拟（delay + 假结果），看是否还闪退：

```kotlin
// 替换前（疑似崩溃点）
viewModelScope.launch {
    val result = aiClient.chat(model, prompt, input)
    updateUi(result)
}

// 替换后（模拟版本，用于诊断）
viewModelScope.launch {
    delay(2000)  // 模拟网络延迟
    updateUi("模拟成功")  // 假结果
}
```

- **不闪退** → 问题在模拟掉的那一层（此处是网络层）
- **还闪退** → 问题在 UI 层或协程调度层

### Step 3：隔离协程层

如果模拟版仍闪退，去掉协程，改为直接同步调用：

```kotlin
// 去掉 viewModelScope.launch，改为直接执行
_uiState.update { it.copy(result = "测试") }
```

如果还不闪退 → 问题在协程调度。如果闪退 → 问题在 Compose UI 渲染。

### Step 4：隔离 UI 层

如果怀疑 Compose 渲染崩溃，逐个注释 Composable 组件，直到找到崩溃的组件。

## 典型根因速查

| 症状 | 最可能原因 |
|------|-----------|
| 点"测试连接"闪退 | **缺少 INTERNET 权限**（Android） |
| 滚动列表闪退 | LazyColumn key 重复 |
| 打开页面闪退 | Hilt DI 未正确注入 |
| 协程内闪退 | `viewModelScope.launch` 内抛 Error（非 Exception） |
| 数据保存后闪退 | DataStore 并发写入冲突 |
| 完全随机闪退 | 内存不足 / native crash |

## Why This Works

try-catch(Throwable) 只能捕获 JVM 层的异常。系统级杀进程（权限拒绝、native signal、OOM）走的是操作系统内核路径，不会经过 JVM 异常机制。模拟替代法通过**逐层删除代码**来缩小嫌疑范围，比加日志更有效。
