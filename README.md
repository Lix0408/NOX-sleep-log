<div align="center">

# NOX · 睡眠时长记录

**睡前点一下，醒来点一下。剩下的交给夜。**

一个只做一件事的 Android 应用：记录你的睡眠时长。

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-8B7CF6)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)
![License](https://img.shields.io/badge/License-MIT-5EEAD4)

</div>

---

## 它是什么

NOX 不做睡眠分期推断，不做鼾声录音，不做社交排行，不需要联网账号。

它只回答一个问题：**你昨晚睡了多久。**

整个生命周期只有两次点击：

1. 睡前点「开始入睡」
2. 醒来点「我醒了」

## 功能

- **睡眠记录** —— 一键开始 / 结束；切后台、划掉最近任务都不会中断计时
- **醒来检测** —— 基于屏幕亮灭判断「你已经醒了」：睡眠中累计亮屏超过阈值时弹出确认卡片。只做半自动结束、只给出建议，绝不替你凭空造一条记录
- **四数口径** —— 在床时长 / 清醒时长 / 实际睡眠 / 睡眠效率，全部由同一份数据推导，各屏数字互相自洽
- **统计与历史** —— 周 / 月 / 年视图、目标达成率、连续记录天数、单夜详情时间轴（含夜间清醒缺口）
- **就寝提醒** —— 每天到点提醒该睡了；使用非精确闹钟，不申请任何特殊权限
- **桌面小组件** —— 2×2「昨夜睡眠」与 4×1「连续记录」，点一下直接进入睡眠记录

## 安装

从 [Releases](https://github.com/Lix0408/NOX-sleep-log/releases) 下载最新的 `NOX-x.x.x-release.apk`。

- 系统要求：**Android 8.0（API 26）及以上**
- 安装包签名证书指纹（SHA-256），可用于校验官方包：

```
E6:47:B5:36:AD:83:A8:94:F9:51:81:C9:56:DD:43:EA:
60:75:F2:0D:D1:57:3A:FD:8E:B6:AA:1F:9C:99:0A:A5
```

## 从源码构建

```bash
git clone https://github.com/Lix0408/NOX-sleep-log.git
cd NOX-sleep-log
./gradlew assembleDebug
```

- 需要 **JDK 17** 与 Android SDK（compileSdk 35）
- 仓库不包含任何签名密钥。未配置密钥时，release 构建会自动回退到 debug 签名，**clone 下来无需任何配置即可编译出可安装的包**

## 权限说明

NOX 不申请任何敏感权限，**没有网络权限**——所有记录只保存在这台设备上。

| 权限 | 用途 |
|---|---|
| `POST_NOTIFICATIONS` | 睡眠计时通知、就寝提醒、醒来确认卡片 |
| `FOREGROUND_SERVICE` | 睡眠计时在后台持续运行 |
| `RECEIVE_BOOT_COMPLETED` | 设备重启后恢复就寝提醒 |

清醒时长由「屏幕亮灭 + 交互事件」推导，不采集麦克风、位置、活动识别或任何传感器数据。

## 技术栈

Kotlin · Jetpack Compose（Material 3）· Room · DataStore · AlarmManager

无第三方 UI 库、无网络库、无统计 SDK，离线优先。

## 设计

- **深色优先**：界面为夜间场景设计；睡眠中页为纯深色，不套用任何背景图
- **月牙是唯一母题**：应用图标、主按钮、启动动画、呼吸动画共用同一条路径
- **一个真相源**：所有屏幕展示的数字都由同一份数据推导，不允许出现对不上的两个数

## License

[MIT](LICENSE)
