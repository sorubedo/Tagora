# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建与测试命令

```bash
# 编译 debug APK
./gradlew assembleDebug

# 编译 release APK
./gradlew assembleRelease

# 运行本地单元测试（JUnit + coroutines test，位于 app/src/test/）
./gradlew test

# 运行单个测试类
./gradlew test --tests "com.tagora.app.ui.main.MainScreenViewModelTest"

# 运行仪器化测试（需要模拟器/设备，位于 app/src/androidTest/）
./gradlew connectedAndroidTest

# 清理构建
./gradlew clean
```

所有 Gradle 命令已配置腾讯云镜像加速，无需翻墙下载依赖。

## 部署到设备

```bash
# 连接 ADB 设备（以网络调试为例）
adb connect 192.168.43.1:5555

# 使用 android CLI 一键构建、安装并启动（推荐）
android run --device 192.168.43.1:5555 --apks app/build/outputs/apk/debug/app-debug.apk

# 也可分步操作：
./gradlew installDebug                           # 仅安装
adb -s 192.168.43.1:5555 shell am start -n com.tagora.app/.MainActivity  # 仅启动
```

## 技术栈

| 项目 | 版本 |
|------|------|
| Kotlin | 2.3.20 |
| AGP | 9.0.1 |
| Gradle | 9.1.0 |
| Compose BOM | 2026.03.01 |
| Navigation3 | 1.1.2 |
| targetSdk / compileSdk | 36 |
| minSdk | 26 |
| JVM target | 17 |

### 关键依赖

- **Navigation3** (`androidx.navigation3:*`) — 基于 `NavKey` 的新导航库，所有路由键通过 `@Serializable` 标记，支持预测性返回手势（predictive back）。
- **kotlinx-serialization** — JSON 持久化层，所有配置模型和数据模型均使用 `@Serializable`。
- **Material 3** + 动态颜色（Android 12+）。图标使用 `material-icons-core` + `material-icons-extended`，导航图标通过 `ImageVector` 定义在 `MainPage` 枚举中。
- **Lifecycle ViewModel Compose** + **kotlinx-coroutines** 用于响应式 UI 状态管理。

## 项目架构

### 分层概览

```
MainActivity (单 Activity)
  └── MainNavigation (NavDisplay + entryProvider)
        ├── MainScreen (首页，四页抽屉：控制面板/时间线/时间段/任务)
        │     ├── DashboardContent (控制面板：实时时间、激活标签/任务)
        │     ├── DayTimeline (24 小时时间线)
        │     ├── TimePeriodListPage (时间段列表)
        │     └── TaskListContent (任务列表)
        ├── SettingsPage → TagManage / TaskManage / DebugTagActivation
        ├── TimePeriodDetailPage (新增/编辑时间段)
        ├── TagDetailPage (新增/编辑标签)
        ├── TaskDetailPage / TaskConditionPage (新增/编辑任务及条件)
        └── DebugTagActivationPage (仅 DEBUG 构建)
```

### 标签自动激活系统

**TagActivationEngine** (`data/TagActivationEngine.kt`) 是核心引擎：
- 通过 `combine` 合并三种 TimePeriod Flow + 分钟级 ticker，根据系统时间自动计算当前激活的标签 ID 集合
- 三种匹配算法：`matchDailyPeriods`（含跨午夜支持）、`matchWeeklyPeriods`（DayOfWeek，1=周一~7=周日）、`matchDatePeriods`（日期范围含两端）
- 结果取三种类型的**并集**（Set 去重）
- 通过 `RepositoryProvider.getActivationEngine()` 获取全局单例
- 在 `MainScreen` 中通过 `DisposableEffect` 管理生命周期，`ON_RESUME` 时立即刷新

### 导航系统 (`NavigationKeys.kt` + `Navigation.kt`)

- 使用 Navigation3 的 `NavKey` 模式：每个页面定义一个 `@Serializable` 的 `data object` 或 `data class` 作为路由键。
- `Navigation.kt` 中的 `entryProvider` 集中注册所有路由到 Composable 的映射。
- 页面间通过 `backStack.add(navKey)` 导航，通过 `backStack.removeLastOrNull()` 返回。
- `DebugTagActivation` 页面仅在 `BuildConfig.DEBUG` 时注册。

### 数据层

- **Repository 模式**：`TimePeriodRepository` 和 `TaskRepository` 接口定义了数据操作，`Default*` 实现类负责 JSON 文件读写。
- **共享单例**：`RepositoryProvider` 是全局单例工厂，确保所有 ViewModel 通过同一 `MutableStateFlow` 实例共享数据，一处修改全局即时反映。
- **持久化策略**：
  - 数据存储在 `context.filesDir` 下的 JSON 文件（`tags.json`、`periods.json`、`weekly_periods.json`、`date_periods.json`、`tasks.json`）。
  - 首次启动时从 `assets/default_*.json` 加载默认配置并写入 filesDir。
  - 文件损坏时自动回退到默认配置。
  - `ConfigDocumentsProvider` 将 filesDir 下的 JSON 文件暴露给系统文件管理器（SAF DocumentsProvider），支持外部编辑。
- **Flow 驱动**：Repository 使用 `MutableStateFlow.filterNotNull()` 暴露数据流，ViewModel 通过 `combine` 合并多个 Flow，UI 通过 `collectAsStateWithLifecycle()` 收集。

### 数据模型 (`data/model/`)

- **TimePeriod**: 时间段，支持三种类型：
  - `daily` — 每日重复，通过 `startMinute`/`endMinute`（分钟数）定义时间区间
  - `weekly` — 每周重复，通过 `dayOfWeeks`（如 `[1,2,3,4,5]`）定义
  - `date` — 指定日期范围，通过 `startDate`/`endDate`（ISO `yyyy-MM-dd`）定义
- **Tag**: 标签，含 `id`、`name`、`color`，用于标记时间段。
- **Task**: 任务，含 `condition: TaskCondition` 密封接口——支持 `AndCondition`、`OrCondition`、`NotCondition`、`MultiTagCondition` 的树形逻辑组合，通过多态序列化存储。

### UI 层模式

- **ViewModel + StateFlow**: 每个功能模块有自己的 ViewModel（`TimePeriodViewModel`、`TaskViewModel`），通过 `combine` 合并 Repository 的多个 Flow，输出单一 `StateFlow<UiState>`。
- **UiState 密封接口**: 使用 `Loading` / `Error` / `Success` 三态模式。
- **共享组件**: `ui/components/` 下有 `CardGroup`（圆角卡片容器）和 `FormItem`（标签-描述-尾部 三段式表单项），参照 RikkaHub 设计风格。

## 注意事项

- **Navigation3 版本较新**（1.1.2），API 与传统的 Navigation Compose（`NavHost`/`NavController`）完全不同——使用 `NavDisplay` + `entryProvider` + `NavKey`，无 `navController` 概念。
- **ViewModel 依赖注入是手动的**：未使用 Hilt/Koin，ViewModel 通过 `viewModel { TimePeriodViewModel(repository) }` 手动构造。
- **`参考项目/` 目录已 gitignore**，不在构建路径中，仅作参考。
- **gradle.properties 开启了 configuration-cache**，修改 `build.gradle.kts` 后如果出现缓存相关问题，运行 `./gradlew clean` 清除缓存。
- **修改 `assets/default_*.json` 后**，设备上已有数据不会自动更新——需在应用设置页点击"重置为默认"，或清除应用数据。
- **不截图验证**：部署后通过 `android run` 启动，在设备上直接查看效果即可。
