# Phase 0: 工程初始化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建可运行的 Android 项目骨架，包含 Compose + Hilt + Room + DataStore + Navigation 依赖，三页底部导航空壳，暖白主题与基础卡片风格。

**Architecture:** 单 App 模块，包内按 feature/core 分层。`app/` 含 Application、MainActivity、Navigation；`core/ui/theme/` 含主题系统；`feature/{record,memory,settings}/` 含三页空壳。

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06.00, Hilt 2.51.1, Room 2.6.1, Navigation 2.7.7, DataStore 1.1.1, Coil 2.6.0, Retrofit 2.9.0, Lifecycle 2.8.3

## Global Constraints

- JDK 21 (`D:\Android_Studio\jbr`), SDK (`C:\Users\LZH\AppData\Local\Android\Sdk`)
- 编译命令：`gradlew assembleDebug`（需先设置 JAVA_HOME 和 ANDROID_HOME）
- 包名：`com.gift.tolife`
- 最低 SDK：26，目标 SDK：34
- 阶段完成标准：App 可编译运行，三页底部导航可见，主题色彩正确
- 阶段 0 完成后再 git init

---

### Task 1: 创建 Gradle Wrapper 与根构建文件

**Files:**
- Create: `D:\gift\settings.gradle.kts`
- Create: `D:\gift\build.gradle.kts`
- Create: `D:\gift\gradle.properties`
- Create: `D:\gift\local.properties`
- Create: `D:\gift\.gitignore`
- Create: `D:\gift\gradle\wrapper\gradle-wrapper.properties`

**Interfaces:**
- Produces: 项目根配置，供所有后续任务使用

- [ ] **Step 1: 创建 `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GiftToLife"
include(":app")
```

- [ ] **Step 2: 创建根 `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
```

- [ ] **Step 3: 创建 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: 创建 `local.properties`**

```properties
sdk.dir=C\:\\Users\\LZH\\AppData\\Local\\Android\\Sdk
```

- [ ] **Step 5: 创建 `.gitignore`**

```
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
/app/build
/app/release
*.apk
*.aab
*.jks
*.keystore
```

- [ ] **Step 6: 创建 `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 7: 生成 Gradle Wrapper 脚本**

运行：
```cmd
set "JAVA_HOME=D:\Android_Studio\jbr" && set "ANDROID_HOME=C:\Users\LZH\AppData\Local\Android\Sdk" && cd /d D:\gift && gradle wrapper --gradle-version 8.7
```

> 如果 `gradle` 不在 PATH，可手动创建 `gradlew.bat`（见下方手动方案）。

**手动方案（如 gradle 命令不可用）：** 创建 `gradlew.bat`：

```bat
@rem Gradle wrapper startup script for Windows
@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused in Gradle, but we need it for our purposes
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve "." and ".." in APP_HOME to make it shorter
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_HOME%\bin\java.exe" ^
  %DEFAULT_JVM_OPTS% ^
  %JAVA_OPTS% ^
  %GRADLE_OPTS% ^
  "-Dorg.gradle.appname=%APP_BASE_NAME%" ^
  -classpath "%CLASSPATH%" ^
  org.gradle.wrapper.GradleWrapperMain ^
  %*

@rem End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal

:omega
```

> gradle-wrapper.jar 可从 `C:\Users\LZH\.gradle\wrapper\dists\gradle-8.7-bin` 中复制或从 Gradle 官方下载。

- [ ] **Step 8: 提交**

```bash
git init
git add settings.gradle.kts build.gradle.kts gradle.properties local.properties .gitignore gradle/wrapper/ gradlew gradlew.bat
git commit -m "chore: init Gradle wrapper and root build files"
```

---

### Task 2: 创建 App 模块构建文件与 Manifest

**Files:**
- Create: `D:\gift\app\build.gradle.kts`
- Create: `D:\gift\app\src\main\AndroidManifest.xml`

**Interfaces:**
- Consumes: 根构建文件（Task 1 定义的插件版本）
- Produces: App 模块可编译（依赖引入完成）

- [ ] **Step 1: 创建 `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.gift.tolife"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gift.tolife"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:navigation-compose:1.2.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coil
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Retrofit (for future AI API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // WorkManager (for future AI tasks)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
}
```

- [ ] **Step 2: 创建 `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".GiftApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.GiftToLife">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.GiftToLife">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 3: 创建 Android 资源文件**

创建 `app/src/main/res/values/strings.xml`：
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Gift To Life</string>
</resources>
```

创建 `app/src/main/res/values/themes.xml`：
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.GiftToLife" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

创建 `app/src/main/res/mipmap-hdpi/ic_launcher.xml`（自适应图标占位）：
> 先用简单的 XML 占位图标，后续再替换为正式图标。

- [ ] **Step 4: 验证编译**

```cmd
set "JAVA_HOME=D:\Android_Studio\jbr" && set "ANDROID_HOME=C:\Users\LZH\AppData\Local\Android\Sdk" && cd /d D:\gift && gradlew assembleDebug
```

预期：BUILD SUCCESSFUL（App 模块可编译，但无 Activity 代码尚未能运行）

- [ ] **Step 5: 提交**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/
git commit -m "chore: add app module with Compose, Hilt, Room, Navigation dependencies"
```

---

### Task 3: 创建主题系统

**Files:**
- Create: `D:\gift\app\src\main\java\com\gift\tolife\core\ui\theme\Color.kt`
- Create: `D:\gift\app\src\main\java\com\gift\tolife\core\ui\theme\Type.kt`
- Create: `D:\gift\app\src\main\java\com\gift\tolife\core\ui\theme\Shape.kt`
- Create: `D:\gift\app\src\main\java\com\gift\tolife\core\ui\theme\Theme.kt`

**Interfaces:**
- Produces: `GiftTheme` Composable，供所有页面使用
- Produces: `GiftColors`、`GiftTypography`、`GiftShapes` 对象

- [ ] **Step 1: 创建 `Color.kt`**

```kotlin
package com.gift.tolife.core.ui.theme

import androidx.compose.ui.graphics.Color

// 背景：暖白 / 浅米灰
val BackgroundLight = Color(0xFFFAF8F5)
val BackgroundDark = Color(0xFF1A1816)

// 卡片：接近纸张白，略带暖色
val CardLight = Color(0xFFFFFCF8)
val CardDark = Color(0xFF252320)

// 表面 / 输入区
val SurfaceLight = Color(0xFFFFFBF6)
val SurfaceDark = Color(0xFF1E1C19)

// 文字：深灰而非纯黑
val TextPrimaryLight = Color(0xFF3D392E)
val TextPrimaryDark = Color(0xFFE8E4DB)
val TextSecondaryLight = Color(0xFF8B8577)
val TextSecondaryDark = Color(0xFF9E988A)
val TextTertiaryLight = Color(0xFFB8B2A4)
val TextTertiaryDark = Color(0xFF6B6558)

// 强调色：低饱和墨绿
val AccentLight = Color(0xFF5B7B6F)
val AccentDark = Color(0xFF8CB5A4)

// 标签色
val TagFlashThought = Color(0xFF8B9DAF)   // 闪念 - 灰蓝
val TagEvent = Color(0xFFB8A882)          // 事记 - 棕金
val TagEmotion = Color(0xFFC4958A)        // 情绪 - 暖粉
val TagKnowledge = Color(0xFF8AAA8A)      // 知识 - 草绿

// 分割线 / 边框
val DividerLight = Color(0xFFE8E3D8)
val DividerDark = Color(0xFF3D3830)
```

- [ ] **Step 2: 创建 `Type.kt`**

```kotlin
package com.gift.tolife.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GiftTypography = Typography(
    // 页面标题：清楚但不巨大
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // 正文：优先可读性
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    // 时间：弱化显示
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    // 标签：小、轻、可扫读
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 3: 创建 `Shape.kt`**

```kotlin
package com.gift.tolife.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val GiftShapes = Shapes(
    // 卡片：中等圆角
    extraLarge = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(6.dp),
    extraSmall = RoundedCornerShape(4.dp)
)
```

- [ ] **Step 4: 创建 `Theme.kt`**

```kotlin
package com.gift.tolife.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = CardLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = CardLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight,
    outlineVariant = DividerLight.copy(alpha = 0.5f)
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = CardDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
    outlineVariant = DividerDark.copy(alpha = 0.5f)
)

@Composable
fun GiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GiftTypography,
        shapes = GiftShapes,
        content = content
    )
}
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/gift/tolife/core/ui/theme/
git commit -m "feat: add theme system (warm paper tones, low-saturation accent)"
```

---

### Task 4: 创建导航与三页空壳

**Files:**
- Create: `D:\gift\app\src\main\java\com\gift\tolife\navigation\Screen.kt`
- Create: `D:\gift\app\src\main\java\com\gift\tolife\navigation\AppNavigation.kt`
- Create: `D:\gift\app\src\main\java\com\gift\tolife\feature\record\RecordScreen.kt`
- Create: `D:\gift\app\src\main\java\com\gift\tolife\feature\memory\MemoryScreen.kt`
- Create: `D:\gift\app\src\main\java\com\gift\tolife\feature\settings\SettingsScreen.kt`

**Interfaces:**
- Consumes: `GiftTheme`（Task 3）、Material3 组件
- Produces: `AppNavigation` Composable、三个 Screen Composable、`Screen` 密封类

- [ ] **Step 1: 创建 `Screen.kt`**

```kotlin
package com.gift.tolife.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Memory : Screen(
        route = "memory",
        label = "回忆",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    )

    data object Record : Screen(
        route = "record",
        label = "记录",
        selectedIcon = Icons.Filled.EditNote,
        unselectedIcon = Icons.Outlined.EditNote
    )

    data object Settings : Screen(
        route = "settings",
        label = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

val bottomNavItems = listOf(Screen.Memory, Screen.Record, Screen.Settings)
```

- [ ] **Step 2: 创建 `AppNavigation.kt`**

```kotlin
package com.gift.tolife.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gift.tolife.feature.memory.MemoryScreen
import com.gift.tolife.feature.record.RecordScreen
import com.gift.tolife.feature.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Record.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Memory.route) { MemoryScreen() }
            composable(Screen.Record.route) { RecordScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
```

- [ ] **Step 3: 创建 `RecordScreen.kt`**

```kotlin
package com.gift.tolife.feature.record

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gift.tolife.core.ui.theme.TextSecondaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "记录页 (待实现)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 4: 创建 `MemoryScreen.kt`**

```kotlin
package com.gift.tolife.feature.memory

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回忆") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "回忆页 (待实现)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 5: 创建 `SettingsScreen.kt`**

```kotlin
package com.gift.tolife.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "设置页 (待实现)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/gift/tolife/navigation/ app/src/main/java/com/gift/tolife/feature/
git commit -m "feat: add bottom navigation with three empty screens"
```

---

### Task 5: 创建 Application 与 MainActivity

**Files:**
- Create: `D:\gift\app\src\main\java\com\gift\tolife\GiftApp.kt`
- Create: `D:\gift\app\src\main\java\com\gift\tolife\MainActivity.kt`

**Interfaces:**
- Consumes: `AppNavigation`（Task 4）、`GiftTheme`（Task 3）
- Produces: 可运行的 App 入口

- [ ] **Step 1: 创建 `GiftApp.kt`**

```kotlin
package com.gift.tolife

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GiftApp : Application()
```

- [ ] **Step 2: 创建 `MainActivity.kt`**

```kotlin
package com.gift.tolife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gift.tolife.core.ui.theme.GiftTheme
import com.gift.tolife.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GiftTheme {
                AppNavigation()
            }
        }
    }
}
```

- [ ] **Step 3: 验证编译**

```cmd
set "JAVA_HOME=D:\Android_Studio\jbr" && set "ANDROID_HOME=C:\Users\LZH\AppData\Local\Android\Sdk" && cd /d D:\gift && gradlew assembleDebug
```

预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/gift/tolife/GiftApp.kt app/src/main/java/com/gift/tolife/MainActivity.kt
git commit -m "feat: add Hilt Application and MainActivity entry point"
```

---

### Task 6: 验证与收尾

**Files:**
- Create: `D:\gift\审查\01-阶段0-目标.md`
- Create: `D:\gift\审查\01-阶段0-方法.md`
- Create: `D:\gift\审查\01-阶段0-已完成.md`
- Create: `D:\gift\审查\01-阶段0-待完成.md`

- [ ] **Step 1: 全量编译验证**

```cmd
set "JAVA_HOME=D:\Android_Studio\jbr" && set "ANDROID_HOME=C:\Users\LZH\AppData\Local\Android\Sdk" && cd /d D:\gift && gradlew assembleDebug --no-daemon
```

- [ ] **Step 2: 确认 APK 生成**

检查 `D:\gift\app\build\outputs\apk\debug\app-debug.apk` 存在。

- [ ] **Step 3: 写入审查文件**

**`审查/01-阶段0-目标.md`**：
```markdown
# 阶段 0 目标

- 创建 Android 项目骨架
- 配置 Compose、Hilt、Room、DataStore、Navigation 依赖
- 建立 feature 目录结构
- 建立主题和基础设计系统（暖白/纸质感/低饱和墨绿）
- 三页底部导航空壳（回忆/记录/设置）
- App 可编译运行
```

**`审查/01-阶段0-方法.md`**：
```markdown
# 阶段 0 方法

## 技术选型
- 单 App 模块，包内分层
- Kotlin 1.9.24 + AGP 8.5.2 + JDK 21
- Compose BOM 2024.06.00 + Compose Compiler 1.5.14
- Hilt 2.51.1 DI、Room 2.6.1 数据库、Navigation 2.7.7 路由

## 实现路径
1. Gradle 构建系统配置
2. App 模块依赖引入
3. 主题系统（Color / Type / Shape / Theme）
4. 导航 + 三页空壳
5. Application + MainActivity 入口

## 理由
- 单模块降低初期复杂度，后续可按需拆分
- 版本选择均为已知稳定组合
- 主题配色严格按设计文档的"温暖/安静/私人"定位
```

**`审查/01-阶段0-已完成.md`**：
```markdown
# 阶段 0 已完成

- [x] Gradle Wrapper 与根构建文件
- [x] App 模块 build.gradle.kts 与 Manifest
- [x] Color / Typography / Shape 主题系统
- [x] GiftTheme Composable
- [x] Screen 密封类 + 底部导航
- [x] RecordScreen / MemoryScreen / SettingsScreen 空壳
- [x] GiftApp (Hilt) + MainActivity 入口
- [x] 资源文件（strings.xml, themes.xml, ic_launcher）
- [x] assembleDebug 编译通过
```

**`审查/01-阶段0-待完成.md`**：
```markdown
# 剩余阶段

- [ ] 阶段 1：记录核心链路（CRUD + 列表）
- [ ] 阶段 2：图片支持
- [ ] 阶段 3：搜索与筛选
- [ ] 阶段 4：回忆页
- [ ] 阶段 5：设置页
- [ ] 阶段 6：AI 标签
- [ ] 阶段 7：周/月总结
- [ ] ~~阶段 8：诗词与空状态~~（已取消）
- [ ] 阶段 9：V1.1 小功能（待定）
```

- [ ] **Step 4: git init + 打 tag**

```bash
cd /d D:\gift
git init
git add -A
git commit -m "feat: phase 0 — project scaffold with Compose, Hilt, navigation, theme"
git tag v0.1.0-phase0
```

- [ ] **Step 5: 最终确认**

确认 APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

---

## 计划总结

| 任务 | 产出 | 预计耗时 |
|------|------|----------|
| Task 1 | Gradle 构建系统 | 10 min |
| Task 2 | App 模块 + 依赖 | 10 min |
| Task 3 | 主题系统 | 10 min |
| Task 4 | 导航 + 三页 | 10 min |
| Task 5 | 入口类 | 5 min |
| Task 6 | 验证 + 审查 + git | 10 min |

**编译验证点：** Task 5 完成后 `gradlew assembleDebug`，Task 6 最终验证。
