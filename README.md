# NERV CLOCK · 葛城美里 主题数字时钟（Galaxy Tab S7 横屏）

按 2560×1600 设计稿 1:1 还原的横屏数字时钟：时间 / 日期 / 一周日历 / 天气，
纯文字排版，NERV 图形元素，日文界面。

- 目标设备：Samsung Galaxy Tab S7（SM-T870），2560×1600，16:10
- 技术栈：Kotlin + Jetpack Compose，minSdk 26，横屏锁定，全屏沉浸，屏幕常亮
- 时间：12 小时制（`7:42 PM`）
- 天气：和风天气（主）→ Open-Meteo（自动兜底），按定位权限取当前城市

---

## 一、云端打包（不用装 Android Studio）

### 1. 新建仓库
登录 GitHub → **New repository** → 名字随意（例如 `misato-clock`）→ 选 Private 也行 → Create。

### 2. 上传工程
把本文件夹里的**所有内容**（不要带外层文件夹）放进仓库根目录。
最省事是用 **GitHub Desktop**：Add local repository → 选本文件夹 → Publish repository。

用网页版 `Add file → Upload files` 拖整个文件夹也可以。
如果 `.github` 目录没传上去（网页版偶尔会漏隐藏目录），用
`Add file → Create new file`，文件名输入 `.github/workflows/build.yml`
（输入斜杠会自动建目录），把下面内容粘进去提交：

```yaml
name: Build APK
on:
  push:
    branches: [ main, master ]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x ./gradlew
      - run: ./gradlew :app:assembleDebug --no-daemon --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: MisatoClock-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

### 3. 下载 APK
提交后打开仓库的 **Actions** 页 → 最新的 `Build APK` → 等它变绿（约 3~5 分钟）→
页面底部 **Artifacts** 下载 `MisatoClock-apk.zip`，解压得到 `app-debug.apk`。

### 4. 装到平板
APK 传到平板（数据线 / 网盘 / 微信文件传输都行）→ 点击安装 →
首次会提示「允许安装未知应用」，允许即可。打开后授权定位，天气自动出来。

---

## 二、用 Android Studio 本地打包（可选）

工程里已带 Gradle Wrapper（8.7），装好 Android Studio 后
`File → Open` 选本文件夹，等 Sync 完成，点 ▶ Run 或者
`Build → Generate Signed Bundle / APK`。首次需要联网下载依赖。

---

## 三、配置

都在 `app/src/main/java/com/nerv/misatoclock/Weather.kt` 顶部的 `Config`：

| 常量 | 说明 |
|---|---|
| `QWEATHER_KEY` | 和风天气 key（已填入你的 key） |
| `QWEATHER_BASE` | 专属 API Host：`https://mb2k5ffcgk.re.qweatherapi.com`（天气 `/v7/...`） |
| `QWEATHER_GEO_BASE` | 城市反查：`https://mb2k5ffcgk.re.qweatherapi.com/geo`（专属 Host 下的 GeoAPI 是 `/geo/v2/...`） |
| `FALLBACK_LAT/LON` | 没有定位权限时的兜底坐标（默认东京） |

**和风天气接口已验证通过**（2026-11-21 实测）：

```
GET https://mb2k5ffcgk.re.qweatherapi.com/v7/weather/now?location=139.69,35.68&key=fc097f...&lang=ja&unit=m
→ 200 {"code":"200","now":{"temp":"7","text":"晴れ","humidity":"30","windDir":"WSW","feelsLike":"4"}}
```

两个坑记一下：

1. **key 必须全小写**。控制台界面上显示的是 `FC097f…`（大写 FC 开头），但服务端只认
   `fc097fc13ac7447998b92831ee296553`，大写会返回 `401 Unauthorized`。
2. **专属 Host 的 GeoAPI 前面要加 `/geo`**：
   `https://mb2k5ffcgk.re.qweatherapi.com/geo/v2/city/lookup`（不带 `/geo` 是 404）。

`windDir` 返回的是 `WSW` 这种英文缩写，代码里已映射成日文方位（西南西）。

和风失败时会自动回落到 Open-Meteo，不会白屏。

时间制在 `ClockScreen.kt` 的 `TimeBlock()` 里，改成 24 小时制只需把
`hour12` 换成 `now.hour` 并去掉 `AM/PM`。

---

## 四、工程结构

```
app/src/main/java/com/nerv/misatoclock/
  MainActivity.kt    全屏沉浸、常亮、横屏入口
  ClockScreen.kt     全部 UI（按 2560×1600 设计稿等比缩放）
  Weather.kt         定位 + 和风天气 + Open-Meteo 兜底
app/src/main/res/
  drawable-nodpi/bg_nerv.png      2560×1600 背景（由 8K 原图裁切）
  drawable/ic_nerv_leaf.xml       NERV 叶片矢量
  font/barlow_sc_light.ttf        Barlow Semi Condensed Light（OFL）
  font/barlow_sc_medium.ttf       Barlow Semi Condensed Medium（OFL）
  mipmap-*/ic_launcher.png        桌面图标
.github/workflows/build.yml       云端打包
```

字体为 SIL Open Font License 1.1，可随 APK 分发。

---

## 五、已知说明

- 天气每 15 分钟刷新；定位失败或无权限时用兜底坐标。
- 城市名：和风走 geoapi，兜底走系统 Geocoder，取不到显示 `TOKYO-3`。
- debug APK 自带签名可直接安装；要正式版需自建 keystore。
- 首次启动会弹定位授权；拒绝也不影响时钟，只是天气用东京坐标。
