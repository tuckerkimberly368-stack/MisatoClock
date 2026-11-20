@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.nerv.misatoclock

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/* ------------------------------------------------------------------ tokens */

private val Ink = Color(0xFF3B2A50)
private val Red = Color(0xFFA83048)
private val Ink90 = Ink.copy(alpha = 0.90f)
private val Ink82 = Ink.copy(alpha = 0.82f)
private val Ink80 = Ink.copy(alpha = 0.78f)
private val Ink72 = Ink.copy(alpha = 0.72f)
private val Ink60 = Ink.copy(alpha = 0.60f)
private val Ink55 = Ink.copy(alpha = 0.55f)
private val Ink54 = Ink.copy(alpha = 0.54f)
private val Ink50 = Ink.copy(alpha = 0.50f)
private val Ink44 = Ink.copy(alpha = 0.44f)
private val Ink42 = Ink.copy(alpha = 0.42f)

private val Barlow = FontFamily(
    Font(R.font.barlow_sc_light, FontWeight.Light),
    Font(R.font.barlow_sc_medium, FontWeight.Medium)
)

private val WEEK_SHORT = listOf("日", "月", "火", "水", "木", "金", "土")
private val WEEKDAY_JA = listOf("月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日", "日曜日")

/* ------------------------------------------------------------------- scale */

/** 设计稿是 2560×1600，k = 1 dp 对应多少设计像素。 */
private class S(val k: Float) {
    fun d(v: Float): Dp = (v * k).dp
    fun t(v: Float): TextUnit = (v * k).sp
}

/* -------------------------------------------------------------------- root */

@Composable
fun ClockScreen() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var weather by remember { mutableStateOf<WeatherInfo?>(null) }
    var permission by remember { mutableStateOf(hasLocationPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permission = granted }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission(context)) {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LaunchedEffect(permission) {
        while (true) {
            val w = withContext(Dispatchers.IO) {
                runCatching { WeatherRepository.load(context) }.getOrNull()
            }
            if (w != null) {
                weather = w
                delay(15 * 60_000L)
            } else {
                delay(20_000L)
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1224))
    ) {
        val s = S(maxWidth.value / 2560f)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            ClockContent(now = now, weather = weather, s = s)
        }
    }
}

@Composable
private fun ClockContent(now: LocalDateTime, weather: WeatherInfo?, s: S) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_nerv),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        BrandBlock(s)
        DeptBlock(s)
        DateBlock(now, s)
        TimeBlock(now, s)
        CaptionBlock(now, s)
        MagiBlock(s)
        BottomBlock(now, weather, s)
        HazardTape(s)
    }
}

/* ------------------------------------------------------------------ blocks */

@Composable
private fun BrandBlock(s: S) {
    Row(
        modifier = Modifier.offset(x = s.d(110f), y = s.d(96f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nerv_leaf),
            contentDescription = null,
            modifier = Modifier.size(s.d(44f), s.d(52f)),
            tint = Red
        )
        Spacer(Modifier.width(s.d(16f)))
        Text(
            text = "NERV",
            fontFamily = Barlow,
            fontWeight = FontWeight.Bold,
            fontSize = s.t(33f),
            letterSpacing = s.t(11.9f),
            color = Ink
        )
        Spacer(Modifier.width(s.d(14f)))
        Box(
            Modifier
                .width(s.d(1f))
                .height(s.d(22f))
                .background(Ink.copy(alpha = 0.22f))
        )
        Spacer(Modifier.width(s.d(16f)))
        Text(
            text = "特務機関",
            fontSize = s.t(22f),
            letterSpacing = s.t(5.7f),
            color = Ink60
        )
    }
}

@Composable
private fun DeptBlock(s: S) {
    Row(
        modifier = Modifier.offset(x = s.d(110f), y = s.d(168f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(s.d(5f))
                .background(Red)
        )
        Spacer(Modifier.width(s.d(12f)))
        Text(
            text = "戦術作戦部 ・ 第一発令所",
            fontSize = s.t(20f),
            letterSpacing = s.t(6f),
            color = Ink44
        )
    }
}

@Composable
private fun DateBlock(now: LocalDateTime, s: S) {
    Row(Modifier.offset(x = s.d(110f), y = s.d(212f))) {
        Text(
            text = "${now.monthValue}月${now.dayOfMonth}日",
            modifier = Modifier.alignByBaseline(),
            fontSize = s.t(56f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = s.t(2.2f),
            color = Ink
        )
        Spacer(Modifier.width(s.d(20f)))
        Text(
            text = WEEKDAY_JA[now.dayOfWeek.value - 1],
            modifier = Modifier.alignByBaseline(),
            fontSize = s.t(52f),
            fontWeight = FontWeight.Light,
            letterSpacing = s.t(4f),
            color = Ink90
        )
        Spacer(Modifier.width(s.d(20f)))
        Text(
            text = now.year.toString(),
            modifier = Modifier.alignByBaseline(),
            fontFamily = Barlow,
            fontSize = s.t(27f),
            letterSpacing = s.t(8f),
            color = Ink.copy(alpha = 0.36f)
        )
    }
}

@Composable
private fun TimeBlock(now: LocalDateTime, s: S) {
    val hour12 = if (now.hour % 12 == 0) 12 else now.hour % 12
    val time = buildAnnotatedString {
        append(hour12.toString())
        withStyle(SpanStyle(color = Red)) { append(":") }
        append(now.minute.toString().padStart(2, '0'))
    }

    Row(Modifier.offset(x = s.d(110f), y = s.d(282f))) {
        Text(
            text = time,
            modifier = Modifier.alignByBaseline(),
            fontFamily = Barlow,
            fontWeight = FontWeight.Light,
            fontSize = s.t(336f),
            letterSpacing = s.t(-6f),
            color = Ink,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Text(
            text = if (now.hour < 12) "AM" else "PM",
            modifier = Modifier
                .alignByBaseline()
                .padding(start = s.d(30f)),
            fontFamily = Barlow,
            fontWeight = FontWeight.Medium,
            fontSize = s.t(62f),
            letterSpacing = s.t(6f),
            color = Ink44
        )
    }
}

@Composable
private fun CaptionBlock(now: LocalDateTime, s: S) {
    val weekOfYear: Int = now.toLocalDate().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
    Row(
        modifier = Modifier.offset(x = s.d(110f), y = s.d(608f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = now.second.toString().padStart(2, '0'),
            fontFamily = Barlow,
            fontSize = s.t(21f),
            color = Ink72
        )
        Spacer(Modifier.width(s.d(8f)))
        Text("秒", fontSize = s.t(19f), letterSpacing = s.t(4f), color = Ink50)
        Spacer(Modifier.width(s.d(20f)))
        Box(
            Modifier
                .width(s.d(42f))
                .height(s.d(1f))
                .background(Ink.copy(alpha = 0.22f))
        )
        Spacer(Modifier.width(s.d(20f)))
        Text("第 ${now.dayOfYear} 日", fontSize = s.t(19f), letterSpacing = s.t(4.4f), color = Ink50)
        Spacer(Modifier.width(s.d(20f)))
        Box(
            Modifier
                .width(s.d(42f))
                .height(s.d(1f))
                .background(Ink.copy(alpha = 0.22f))
        )
        Spacer(Modifier.width(s.d(20f)))
        Text("第 $weekOfYear 週", fontSize = s.t(19f), letterSpacing = s.t(4.4f), color = Ink50)
    }
}

@Composable
private fun MagiBlock(s: S) {
    Column(Modifier.offset(x = s.d(110f), y = s.d(712f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(s.d(6f))
                    .clip(CircleShape)
                    .background(Red.copy(alpha = 0.86f))
            )
            Spacer(Modifier.width(s.d(14f)))
            Text(
                text = "MAGI SYSTEM",
                fontFamily = Barlow,
                fontWeight = FontWeight.Medium,
                fontSize = s.t(21f),
                letterSpacing = s.t(8.8f),
                color = Ink60
            )
        }
        Spacer(Modifier.height(s.d(12f)))
        Text(
            text = "CASPAR · BALTHASAR · MELCHIOR",
            fontFamily = Barlow,
            fontSize = s.t(16f),
            letterSpacing = s.t(4.8f),
            color = Ink42
        )
        Spacer(Modifier.height(s.d(14f)))
        Text(
            text = "稼働状態 正常 ／ パターン 青 検知なし",
            fontSize = s.t(18f),
            letterSpacing = s.t(4f),
            color = Ink54
        )
    }
}

@Composable
private fun BoxScope.BottomBlock(now: LocalDateTime, weather: WeatherInfo?, s: S) {
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = s.d(110f), bottom = s.d(104f))
    ) {
        // 一周日历
        Row(horizontalArrangement = Arrangement.spacedBy(s.d(44f))) {
            weekCells(now.toLocalDate()).forEach { cell ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = cell.label,
                        fontSize = s.t(19f),
                        letterSpacing = s.t(2.7f),
                        color = if (cell.isToday) Red else Ink55
                    )
                    Spacer(Modifier.height(s.d(12f)))
                    Text(
                        text = cell.day.toString(),
                        fontFamily = Barlow,
                        fontWeight = if (cell.isToday) FontWeight.Medium else FontWeight.Light,
                        fontSize = s.t(46f),
                        color = if (cell.isToday) Red else Ink80
                    )
                }
            }
        }

        Spacer(Modifier.height(s.d(54f)))

        // 地点
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "現在地",
                fontSize = s.t(18f),
                letterSpacing = s.t(7.2f),
                color = Ink55
            )
            Spacer(Modifier.width(s.d(12f)))
            Text(
                text = weather?.city ?: "TOKYO-3",
                fontSize = s.t(20f),
                letterSpacing = s.t(4.8f),
                color = Ink55
            )
        }

        Spacer(Modifier.height(s.d(10f)))

        // 温度 + 天气
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = weather?.temp?.toString() ?: "--",
                fontFamily = Barlow,
                fontWeight = FontWeight.Light,
                fontSize = s.t(150f),
                color = Ink,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                text = "°",
                fontFamily = Barlow,
                fontSize = s.t(58f),
                color = Red,
                modifier = Modifier.padding(bottom = s.d(96f))
            )
            Spacer(Modifier.width(s.d(30f)))
            Column(Modifier.padding(bottom = s.d(24f))) {
                Text(
                    text = weather?.text ?: "取得中",
                    fontSize = s.t(40f),
                    letterSpacing = s.t(2.4f),
                    color = Ink
                )
                Spacer(Modifier.height(s.d(12f)))
                Text(
                    text = if (weather != null)
                        "最高 ${weather.tempMax}° / 最低 ${weather.tempMin}°"
                    else "最高 --° / 最低 --°",
                    fontFamily = Barlow,
                    fontSize = s.t(22f),
                    letterSpacing = s.t(4f),
                    color = Ink60
                )
            }
        }

        Spacer(Modifier.height(s.d(20f)))

        // 湿度 / 风 / 体感
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatItem("湿度", weather?.let { "${it.humidity}%" } ?: "--", s)
            Spacer(Modifier.width(s.d(30f)))
            StatItem("風", weather?.let { "${it.windDir} ${it.windSpeed}" } ?: "--", s)
            Spacer(Modifier.width(s.d(30f)))
            StatItem("体感", weather?.let { "${it.feelsLike}°" } ?: "--", s)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, s: S) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = s.t(19f),
            letterSpacing = s.t(4.8f),
            color = Ink55
        )
        Spacer(Modifier.width(s.d(10f)))
        Text(
            text = value,
            fontSize = s.t(24f),
            letterSpacing = s.t(2f),
            color = Ink82
        )
    }
}

@Composable
private fun BoxScope.HazardTape(s: S) {
    Canvas(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = s.d(110f), bottom = s.d(56f))
            .size(s.d(250f), s.d(9f))
    ) {
        drawRect(color = Color(0xFF2F1F42))
        val h = size.height
        val stripe = h * 1.22f
        var x = -h
        while (x < size.width + h) {
            val p = Path().apply {
                moveTo(x, 0f)
                lineTo(x + stripe, 0f)
                lineTo(x + stripe - h, h)
                lineTo(x - h, h)
                close()
            }
            drawPath(p, Color(0xFFD9A24E))
            x += stripe * 2f
        }
    }
}

/* ------------------------------------------------------------------- utils */

private data class DayCell(val label: String, val day: Int, val isToday: Boolean)

private fun weekCells(today: LocalDate): List<DayCell> {
    val offset = today.dayOfWeek.value % 7 // Sunday -> 0
    val sunday = today.minusDays(offset.toLong())
    return (0..6).map { i ->
        val d = sunday.plusDays(i.toLong())
        DayCell(
            label = WEEK_SHORT[i],
            day = d.dayOfMonth,
            isToday = d == today
        )
    }
}
