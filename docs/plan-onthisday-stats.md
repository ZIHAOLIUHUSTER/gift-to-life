# 那年今日 + 记录统计 — 实施计划

## 那年今日

### 数据层
- EntryDao 新增 `getEntriesByDateRange(start: Long, end: Long): List<Entry>`
- 查询 `type = 'NORMAL' AND isDeleted = 0 AND createdAt BETWEEN ? AND ?`

### 业务层
- 新建 `OnThisDayViewModel`（HiltViewModel）
- 按年份倒序遍历：今年 → 去年 → ... → 最早记录年份
- 每年计算该年该日的时间范围（处理闰年 2 月 29 日跳过）
- 每批查询结果合并为 `Map<Int, List<Entry>>`（年份 → 条目列表）

### UI 层
- MemoryScreen 新增 `OnThisDaySection` 组件
- 放在总结入口按钮下方
- 每年一个折叠/展开组，显示年份 + 条数
- 条目点击 → 复用 EntryPreviewSheet

### 验证
- 非闰年 2 月 29 日不查询
- 跨年边界正确（12 月 31 日 → 1 月 1 日）
- 按年倒序展示

---

## 记录统计

### 数据层
- EntryDao 新增 `getMonthlyCount(start, end): Int`
- EntryDao 新增 `getMonthlyTimestamps(start, end): List<Long>`
- SettingsDataStore 新增 `lastActiveEpochDay: Long`, `streakCount: Int`

### 业务层
- SettingsViewModel 新增统计方法
- 本月记录数：DAO 查询
- 连续记录天数：
  - 同一天多次记录不增加（比较 lastActiveEpochDay）
  - 跨天首次记录 +1（lastActiveEpochDay = yesterday）
  - 断档后首次记录重置为 1（lastActiveEpochDay < yesterday - 1）
  - 导入后缓存失效，从最近记录向前重建
- 月热力图：查询本月全部时间戳，按本地日期聚合

### UI 层
- SettingsMainPage 顶部新增统计卡片
- 显示：本月 N 条、连续 N 天
- 热力图：31 格 Grid，颜色按计数分级
  - 0: 中性背景
  - 1: 浅绿 #C8E6C9
  - 2-3: 中绿 #81C784
  - 4-6: 深绿 #4CAF50
  - 7+: 最深绿 #2E7D32

### 缓存
- SharedPreferences 两个字段：`last_active_day`, `streak_count`
- 每次保存记录时更新
- 导入后检测不一致时重建

---

## 执行顺序
1. EntryDao 新增查询方法
2. SettingsDataStore 新增缓存字段
3. OnThisDayViewModel + 那年今日 UI
4. SettingsViewModel 统计方法 + 统计卡片
5. 编译验证