package com.nerv.misatoclock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** ------------------------------------------------------------------ model */

data class LatLon(val lat: Double, val lon: Double)

data class WeatherInfo(
    val city: String?,
    val temp: Int,
    val text: String,
    val tempMax: Int,
    val tempMin: Int,
    val humidity: Int,
    val windDir: String,
    val windSpeed: String,
    val feelsLike: Int
)

object Config {

    /** 和风天气 key（注意：必须全小写，控制台里显示的大写字母形式会 401）。 */
    const val QWEATHER_KEY = "fc097fc13ac7447998b92831ee296553"

    /**
     * 和风天气专属 API Host（控制台「项目管理」里看到的那个）。
     * 天气接口：{QWEATHER_BASE}/v7/weather/now
     * 老账号用 https://devapi.qweather.com 时，把 GEO 换成 https://geoapi.qweather.com 即可。
     */
    const val QWEATHER_BASE = "https://mb2k5ffcgk.re.qweatherapi.com"

    /** 城市反查接口基址：专属 Host 下带 /geo 前缀。 */
    const val QWEATHER_GEO_BASE = "https://mb2k5ffcgk.re.qweatherapi.com/geo"

    // 没有定位权限时的兜底位置（第三新东京市 ≈ 东京）
    const val FALLBACK_LAT = 35.6812
    const val FALLBACK_LON = 139.7671
}

/** --------------------------------------------------------------- location */

fun hasLocationPermission(context: Context): Boolean {
    val coarse = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val fine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return coarse || fine
}

class LocationHelper(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun current(): LatLon? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        var best: Location? = null
        for (p in providers) {
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || l.time > best.time) best = l
        }
        val fresh = best != null && System.currentTimeMillis() - best.time < 15 * 60_000L
        if (fresh && best != null) return LatLon(best.latitude, best.longitude)

        val fix = withTimeoutOrNull(12_000L) { awaitFix(lm, providers) } ?: best
        return fix?.let { LatLon(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitFix(lm: LocationManager, providers: List<String>): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { lm.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            val ok = runCatching {
                for (p in providers) {
                    lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                }
            }.isSuccess
            if (!ok && cont.isActive) cont.resume(null)
            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
        }
}

/** ------------------------------------------------------------------ http */

private fun httpGet(urlStr: String): String {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.connectTimeout = 8_000
    conn.readTimeout = 8_000
    conn.requestMethod = "GET"
    conn.setRequestProperty("User-Agent", "MisatoClock/1.0 (Android)")
    conn.setRequestProperty("Accept", "application/json")
    return try {
        val code = conn.responseCode
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

/** ------------------------------------------------------------- 和风天气 */

object QWeather {

    fun fetch(lat: Double, lon: Double): WeatherInfo {
        val loc = String.format(Locale.US, "%.2f,%.2f", lon, lat)
        val base = Config.QWEATHER_BASE
        val key = Config.QWEATHER_KEY

        val nowJson = JSONObject(
            httpGet("$base/v7/weather/now?location=$loc&key=$key&lang=ja&unit=m")
        )
        if (nowJson.optString("code") != "200") {
            throw IllegalStateException("qweather now: ${nowJson.optString("code")}")
        }
        val now = nowJson.getJSONObject("now")

        val maxMin = runCatching {
            val d3 = JSONObject(httpGet("$base/v7/weather/3d?location=$loc&key=$key&lang=ja&unit=m"))
            val daily = d3.optJSONArray("daily")?.optJSONObject(0)
            val max = daily?.optString("tempMax")?.toIntOrNull()
            val min = daily?.optString("tempMin")?.toIntOrNull()
            if (max != null && min != null) max to min else null
        }.getOrNull()

        val city = runCatching { cityName(lat, lon) }.getOrNull()

        val temp = now.optString("temp").toIntOrNull() ?: 0
        val windKmh = now.optString("windSpeed").toDoubleOrNull() ?: 0.0

        return WeatherInfo(
            city = city,
            temp = temp,
            text = now.optString("text").ifBlank { "—" },
            tempMax = maxMin?.first ?: temp,
            tempMin = maxMin?.second ?: temp,
            humidity = now.optString("humidity").toIntOrNull() ?: 0,
            windDir = windDirJa(now.optString("windDir")),
            windSpeed = String.format(Locale.US, "%.1f m/s", windKmh / 3.6),
            feelsLike = now.optString("feelsLike").toIntOrNull() ?: temp
        )
    }


    /** 和风的 windDir 返回的是 NNE/WSW 这类英文缩写，转成日文方位。 */
    private fun windDirJa(dir: String): String {
        val map = mapOf(
            "N" to "北", "NNE" to "北北東", "NE" to "北東", "ENE" to "東北東",
            "E" to "東", "ESE" to "東南東", "SE" to "南東", "SSE" to "南南東",
            "S" to "南", "SSW" to "南南西", "SW" to "南西", "WSW" to "西南西",
            "W" to "西", "WNW" to "西北西", "NW" to "北西", "NNW" to "北北西"
        )
        return map[dir.uppercase()] ?: dir
    }
    private fun cityName(lat: Double, lon: Double): String? {
        val loc = String.format(Locale.US, "%.2f,%.2f", lon, lat)
        val json = JSONObject(
            httpGet(
                "${Config.QWEATHER_GEO_BASE}/v2/city/lookup" +
                    "?location=$loc&key=${Config.QWEATHER_KEY}&lang=ja&number=1"
            )
        )
        if (json.optString("code") != "200") return null
        val first = json.optJSONArray("location")?.optJSONObject(0) ?: return null
        return first.optString("name").ifBlank { null }
    }
}

/** ------------------------------------------------------------- Open-Meteo */

object OpenMeteo {

    fun fetch(context: Context, lat: Double, lon: Double): WeatherInfo {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1&wind_speed_unit=ms"

        val json = JSONObject(httpGet(url))
        val cur = json.getJSONObject("current")
        val daily = json.optJSONObject("daily")

        val code = cur.optInt("weather_code", 0)
        val dir = cur.optDouble("wind_direction_10m", 0.0)
        val temp = cur.optDouble("temperature_2m", 0.0).roundToInt()

        return WeatherInfo(
            city = cityFromGeocoder(context, lat, lon),
            temp = temp,
            text = wmoToJa(code),
            tempMax = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, temp.toDouble())
                ?.roundToInt() ?: temp,
            tempMin = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, temp.toDouble())
                ?.roundToInt() ?: temp,
            humidity = cur.optDouble("relative_humidity_2m", 0.0).roundToInt(),
            windDir = degToJa(dir),
            windSpeed = String.format(Locale.US, "%.1f m/s", cur.optDouble("wind_speed_10m", 0.0)),
            feelsLike = cur.optDouble("apparent_temperature", temp.toDouble()).roundToInt()
        )
    }

    private fun wmoToJa(code: Int): String = when (code) {
        0 -> "快晴"
        1 -> "晴れ"
        2 -> "晴れ時々くもり"
        3 -> "くもり"
        45, 48 -> "霧"
        51, 53, 55 -> "霧雨"
        56, 57 -> "着氷性の霧雨"
        61, 63 -> "雨"
        65 -> "強い雨"
        66, 67 -> "着氷性の雨"
        71, 73 -> "雪"
        75 -> "強い雪"
        77 -> "霧雪"
        80, 81, 82 -> "にわか雨"
        85, 86 -> "にわか雪"
        95 -> "雷雨"
        96, 99 -> "雷雨（雹）"
        else -> "—"
    }

    private fun degToJa(deg: Double): String {
        val names = arrayOf("北", "北東", "東", "南東", "南", "南西", "西", "北西")
        val idx = (((deg + 22.5) % 360) / 45.0).toInt().coerceIn(0, 7)
        return names[idx]
    }
}

/** 系统反查城市名（Geocoder）。和风的城市接口不可用时用它兜底。 */
@Suppress("DEPRECATION")
fun cityFromGeocoder(context: Context, lat: Double, lon: Double): String? = runCatching {
    if (!Geocoder.isPresent()) return null
    val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
    list?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
}.getOrNull()

/** ------------------------------------------------------------- repository */

object WeatherRepository {

    suspend fun load(context: Context): WeatherInfo? = withContext(Dispatchers.IO) {
        val ll = runCatching { LocationHelper(context).current() }.getOrNull()
        val lat = ll?.lat ?: Config.FALLBACK_LAT
        val lon = ll?.lon ?: Config.FALLBACK_LON

        // 1) 和风天气（日文、米制）→ 2) Open-Meteo 兜底
        val info = runCatching { QWeather.fetch(lat, lon) }.getOrNull()
            ?: runCatching { OpenMeteo.fetch(context, lat, lon) }.getOrNull()
            ?: return@withContext null

        if (info.city.isNullOrBlank()) {
            val c = cityFromGeocoder(context, lat, lon)
            if (c != null) info.copy(city = c) else info
        } else {
            info
        }
    }
}
