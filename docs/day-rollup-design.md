# 按天分段汇总 · 数据表设计方案

> 版本：v0.2（随 NOX 0.2.0 落地）
> 场景：同一个自然日内有多次分段睡眠记录（夜间主睡眠 / 午睡 / 傍晚小睡……），
> 每段正向计时，按天把各段时长汇总相加。
> 本文即实现依据 —— 字段、SQL、公式与代码一一对应。

---

## 0. 一句话设计

**一段睡眠 = 一行记录（分段即多行，不是多列）；归属日随收口一次定死；当日累计是派生值，实时聚合、不冗余存储。**

```
sleep_session（每行 = 一段）
┌───────────┬─────────┬──────────┬──────────┬─────────┐
│ 入睡时刻   │ 醒来时刻 │ 归属日    │ 在床     │ 清醒     │
├───────────┼─────────┼──────────┼──────────┼─────────┤
│ 10-10 23:14│ 10-11 06:56│ 10-11 │ 7h42m    │ 12m     │ ← 夜间主睡眠（跨零点）
│ 10-11 13:30│ 10-11 14:10│ 10-11 │ 40m      │ 0       │ ← 午睡
│ 10-11 20:10│ 10-11 20:40│ 10-11 │ 30m      │ 0       │ ← 傍晚小睡
└───────────┴─────────┴──────────┴──────────┴─────────┘
                 GROUP BY dayKey = '2026-10-11'
                          ↓
        当日累计：3 段 · 在床 8h52m · 睡眠 8h40m
```

---

## 1. 口径定义

### 1.1 什么是「一段」

每完成一次「开始入睡 → 我醒了」的**正向计时**，产生一条 `sleep_session` 记录（一行 = 一段）。
同一天允许多段：每一段独立计时、独立计算自身时长，互不干扰。

### 1.2 归属日规则（本设计的核心决策）

> `dayKey = 结束时刻（endAt）所在本地日期`，即「起床日」。

| 分段 | 时间 | 归属日 | 说明 |
|---|---|---|---|
| 夜间主睡眠 | 10-10 23:14 → 10-11 06:56 | **10-11** | 跨零点，归到**醒来**那天 |
| 午睡 | 10-11 13:30 → 10-11 14:10 | **10-11** | 与当天主睡眠同归一天 |
| 傍晚小睡 | 10-11 20:10 → 10-11 20:40 | **10-11** | 同上 |
| 深夜短睡 | 10-11 23:50 → 10-12 00:20 | **10-12** | 结束在次日凌晨，归次日 |

选「起床日」而不是「入睡日」的三条理由：

1. **与既有约定一致**：历史列表、详情页、周视图从 v0.1 起就是按起床日归属，换规则会让旧数据语义漂移；
2. **符合直觉**：「今天睡了多久」 = 从上一次起床到这一次起床之间睡掉的时间；
3. **只有它能相加**：夜里的主睡眠与当天午睡落在同一天，才能汇总出有意义的「当日累计」——若按入睡日，主睡眠（10-10）与午睡（10-11）会被拆到两天，无法相加。

### 1.3 累计口径（分钟）

```
在床(段)  = ⌊(endAt − startAt) / 60000⌋
清醒(段)  = ⌊awakeMillis / 60000⌋
睡眠(段)  = max(0, 在床(段) − 清醒(段))

当日合计   = Σ 睡眠(每一段)          ← 累加的是「睡眠」口径，不是在床
当日达成率 = 当日合计 ÷ 睡眠目标
```

**取整规则：先各自取整、再相加**（不是先相加再取整）。
这样 6h20m30s 的一段固定显示为 6h20m，多段相加后「**当日合计 == 页面里各段之和**」严格成立，
误差不会跨段传递（这是本项目「数字自洽」原则的硬约束）。

---

## 2. 数据表设计

### 2.1 `sleep_session`（分段表 · 唯一事实源）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INTEGER | PK 自增 | |
| `startAt` | INTEGER | NOT NULL | 入睡时刻（UTC 毫秒）。正向计时起点 |
| `endAt` | INTEGER | 可空 | 结束时刻；NULL = 进行中（**全表至多一行**，由应用层 `startSleep` 前置检查保证） |
| **`dayKey`** | TEXT | 可空 · **索引** | **归属日 `"yyyy-MM-dd"`（= endAt 本地日期）。本次新增**：收口时写入；进行中为 NULL |
| `awakeMillis` | INTEGER | NOT NULL | 段内累计清醒（亮屏）毫秒，来自屏幕亮灭监听 |
| `wakeCount` | INTEGER | NOT NULL | 段内清醒片段数 |
| `awakeSegments` | TEXT | NOT NULL | `"start,end;start,end"` 清醒时间片，详情页画缺口、醒来检测回溯用 |
| `confirmedBy` | TEXT | NOT NULL | `manual`（手动结束）/ `auto`（醒来检测）/ `sample`（仅调试样例） |
| `integrity` | INTEGER | NOT NULL | 1 = 完整；0 = 记录期间进程被杀，亮屏不完整 |
| `mood` / `quality` / `note` | TEXT / INTEGER / TEXT | 可空 | 主观补充，不参与时长口径 |
| `createdAt` | INTEGER | NOT NULL | 记录创建时刻 |

索引：

```sql
CREATE INDEX index_sleep_session_dayKey ON sleep_session (dayKey);
```

> 为什么分段不存成「列」：段数无上限、无法索引、无法按段查询；
> **行式存储（一列多行）+ 分组键**才是可扩展的正解。所谓「各分段时长」
> 不是独立字段，而是每行由 `startAt/endAt` 派生的展示值 —— 存储只留事实，时长永远算出来。

### 2.2 迁移 SQL（schema 1 → 2）

```sql
-- 1) 加列（可空：进行中的记录暂不归属）
ALTER TABLE sleep_session ADD COLUMN dayKey TEXT;

-- 2) 旧数据回填：结束时刻的本地日期（与 dayKeyOf() 口径完全一致）
UPDATE sleep_session
SET dayKey = strftime('%Y-%m-%d', endAt / 1000, 'unixepoch', 'localtime')
WHERE endAt IS NOT NULL;

-- 3) 索引（命名必须与 Room 由 @Entity(indices = [Index("dayKey")]) 生成的预期 schema 一致）
CREATE INDEX IF NOT EXISTS index_sleep_session_dayKey ON sleep_session (dayKey);
```

### 2.3 为什么不建独立的「日汇总表」

曾评估过 `sleep_day(dayKey PK, totalMinutes, segmentCount, …)` 物化方案，结论：**不采用**。

- 本项目已明确原则：「所有统计均为明细表的聚合视图，不存冗余结果」；
- 冗余存储引入**漂移风险**：删除/修改任意一段后，日表必须同步，一旦漏同步，
  汇总与明细永久对不上，且很难排查；
- 数据量级微不足道：每天几条记录，十年不过一万行，`GROUP BY dayKey` 走索引是毫秒级。

> 若未来确实需要 O(1) 读取（如日历月视图一次拉 365 天）：
> 也**不要用 `total += 增量` 维护**，而要在收口事务里**重算式更新**：
> `totalMinutes = (SELECT SUM(...) FROM sleep_session WHERE dayKey = ?)`
> —— 增量的副本会漂移，重算的副本不会。

---

## 3. 分段的存储与累加逻辑

### 3.1 生命周期

| 阶段 | 触发 | 写什么 |
|---|---|---|
| 开始 | 点「开始入睡」 | `INSERT` 一行：`startAt = now`，`endAt = NULL`，`dayKey = NULL` |
| 进行中 | 每 30 秒 / 每次息屏 | `checkpoint`：更新 `awakeMillis / wakeCount / awakeSegments`（**不动 dayKey**） |
| 收口 | 「我醒了」/ 醒来检测确认 | 写入 `endAt`、**`dayKey = dayKeyOf(endAt)`**、最终 `awakeMillis / wakeCount / awakeSegments / confirmedBy / integrity` |
| 编辑 | 详情页改心情/备注 | 只动 `mood / quality / note`，**时间与 dayKey 永不修改** |

### 3.2 所有写库路径收敛到一处

无论手动结束、醒来检测自动结束、还是异常恢复，最终都走同一个函数：

```kotlin
// SleepRepository.finish()
cur.copy(
    endAt = endAt,
    dayKey = dayKeyOf(endAt),   // ← 归属日在此一次定死
    awakeMillis = awakeMillis,
    wakeCount = wakeCount,
    confirmedBy = confirmedBy,
    integrity = integrity,
    awakeSegments = SleepSession.encodeSegments(segments)
)
```

### 3.3 累加发生在读取时，不在写入时

收口只写「这一段的明细」。**当日累计没有任何落库动作** —— 它永远由聚合查询现算：

```
写入一段  →  （不更新任何汇总）
读取汇总  →  SELECT … GROUP BY dayKey   ← 永远与明细一致
```

这条规则同时消灭了「漏更新」「重复累加」「时段重叠被算两次」三类问题：
明细是唯一输入，汇总每次从零推导。

> 「时间段重叠」的兜底：应用层保证同一时刻至多一段在进行（`startSleep` 前置检查 +
> 收口前不允许再开新段），因此已完成的段之间天然不重叠，无需区间合并。

---

## 4. 按天汇总的数据结构

### 4.1 查询结果结构（`DayAggregate`）

| 字段 | 类型 | 含义 |
|---|---|---|
| `dayKey` | String | 日期（分组键） |
| `segmentCount` | Int | 当日分段数 |
| `inBedMinutes` | Long | 当日**在床**合计 = Σ⌊endAt−startAt⌋ |
| `sleptMinutes` | Long | **当日累计总时长** = Σmax(0, ⌊在床⌋−⌊清醒⌋) |
| `firstStartAt` / `lastEndAt` | Long | 首段入睡 / 末段醒来的时刻（画「跨度」用） |

### 4.2 聚合 SQL（与实现逐字一致）

```sql
SELECT dayKey AS dayKey,
       COUNT(*) AS segmentCount,
       COALESCE(SUM(MAX((endAt - startAt) / 60000, 0)), 0)                       AS inBedMinutes,
       COALESCE(SUM(MAX((endAt - startAt) / 60000 - awakeMillis / 60000, 0)), 0) AS sleptMinutes,
       COALESCE(MIN(startAt), 0) AS firstStartAt,
       COALESCE(MAX(endAt), 0)   AS lastEndAt
FROM sleep_session
WHERE endAt IS NOT NULL AND dayKey IS NOT NULL
GROUP BY dayKey
ORDER BY dayKey DESC
LIMIT :limit;
```

三个细节：

- 除法是 **SQLite 整数除法**（向零取整），与 Kotlin 的 `⌊/60000⌋` 在正数域等价 ——
  两端同一条公式、同一取整时机，数字必然一致（见 §6 验收）；
- `MAX(…, 0)` 兜底脏数据（清醒 > 在床、时钟回拨），保证单段不为负；
- `COALESCE` 兜底空集，保证字段恒为非空整数，UI 无需判空。

### 4.3 粒度换算（不需新增表）

| 视图 | 分组方式 | 说明 |
|---|---|---|
| 日 | `GROUP BY dayKey` | 本方案主体 |
| 周 | `GROUP BY` dayKey 后按 `substr(dayKey,1,10)` 归周（或应用层换算） | 周视图按“天合计”再取均值 |
| 月 | `substr(dayKey, 1, 7)` | |
| 年 | `substr(dayKey, 1, 4)` | |

`dayKey` 是 `yyyy-MM-dd` 定长字符串，**字典序 = 时间序**，可以直接做范围查询
（`WHERE dayKey >= '2026-10-01'`）和字符串前缀分组，不需要任何日期函数。

---

## 5. 边界与异常

| 场景 | 行为 |
|---|---|
| 同日多段 | 逐段入库，聚合自动相加，无任何特殊处理 |
| 跨零点 | 归属**结束日**；`endAt` 落在哪天就归哪天 |
| 进行中的段 | `dayKey = NULL`，**不计入任何日的累计**；收口写入后才计入 |
| 旧版本升级（1→2） | 迁移回填：`strftime(..., 'localtime')`，与 `dayKeyOf()` 等效；进行中的记录保持 NULL |
| 换时区 / 出国 | `dayKey` 收口时按**当时**本地时区定死，此后不再重算 —— 历史的归属不会随旅行漂移（时间戳始终是 UTC 毫秒） |
| 系统时间被改 | 时间戳是数据问题（`integrity` / 版本溯源管）；`dayKey` 只忠实记录「结束时刻换算出的日期」 |
| 不完整记录（`integrity = 0`） | 本设计照常计入汇总（时长仍有效，只是清醒分钟可能少记）；如需排除，在 `WHERE` 加 `integrity = 1` 即可，口径改动只在一处 |
| 零段日 | 不出现在结果中（`GROUP BY` 只产出有记录的天）——UI 的「按天列表」即「有记录的天的列表」 |
| 调试示例数据 | `confirmedBy = 'sample'` 标记，一键精确删除，不触碰真实记录 |
| 删除某段 | 聚合立即反映（无缓存无同步）；若未来加了物化表，必须重算式更新（见 §2.3） |

---

## 6. 算例（数字自洽校验）

目标 8h（480 分钟）：

| # | 时间 | 在床(分钟) | 清醒(分钟) | 睡眠(分钟) |
|---|---|---|---|---|
| ① | 00:30 → 07:00 | 390 | 10 | 380 |
| ② | 13:30 → 14:10 | 40 | 0 | 40 |
| ③ | 20:10 → 20:40 | 30 | 0 | 30 |
| **合计** | | **460（7h40m）** | **10** | **450（7h30m）** |

- 当日累计 = 450 分钟 = **7h30m**；逐段显示 6h20m + 40m + 30m = **7h30m** —— 严格相等；
- 达成率 = 450 ÷ 480 = 93.75% → 显示 **94%**；
- 此算例同时是单元测试 `DayRollupTest.同日三段相加等于当日累计` 的断言值。

---

## 7. 实现映射与验收

| 设计要素 | 落点 |
|---|---|
| 字段 + 索引 + 迁移 | `data/SleepSession.kt`（`@Entity(indices=[Index("dayKey")])`、`MIGRATION_1_2`、`DayAggregate`、`observeDayAggregates`） |
| 归属日写入 | `data/SleepRepository.kt` → `finish()`；`core/Format.kt` → `dayKeyOf()` |
| 分钟口径（唯一公式） | `domain/DayRollup.kt` → `inBedMinutesOf / sleptMinutesOf`（`metricsOf` 同源） |
| 日视图 UI | `ui/DataScreens.kt` → `StatsScreen`（日/周/月/年）→ `DayTotalsView / DayRow / DaySegments` |
| 单元测试 | `app/src/test/java/com/nox/sleep/DayRollupTest.kt`（归属日 3 例 + 口径 3 例 + 算例 1 例） |
| 真机验收 | 旧库升级后 `dayKey` 回填正确；同日多段相加 == 页面各段之和；跨零点归属正确 |

验收口诀：**合计对得上各段之和、跨零点归到醒来那天、升级不丢数据。**
