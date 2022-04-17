package jp.hay.bon2nmea

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * １地点の情報
 */
class LocationData {
    var m_timestamp: LocalDateTime
    var m_latitude: Long
    var m_longtitude: Long
    var m_elevation: Int
    var m_speed: Int
    var m_course: Int = 0

    /**
     * chunkを用いて初期化する
     */
    constructor(chunk: ByteArray, prevLocation: LocationData?) {
        // 時刻
        val year = byteArrayToUIntLE(chunk.sliceArray(18..19)).toInt()
        val month = chunk[20].toInt()
        val dayOfMonth = chunk[21].toInt()
        val hour = chunk[22].toInt()
        val minute = chunk[23].toInt()
        val second = chunk[24].toInt()
        val nanosecond = chunk[25].toInt() * 100 * 1000 * 1000
        m_timestamp = LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanosecond)

        // 座標
        m_latitude = byteArrayToUIntLE(chunk.sliceArray(4..7)).toLong() * 6
        m_longtitude = byteArrayToUIntLE(chunk.sliceArray(8..11)).toLong() * 6
        m_elevation = byteArrayToUIntLE(chunk.sliceArray(12..13)).toInt()
        //Log.i("Main", "lat=$m_latitude,long=$m_longtitude,ev=$m_elevation")

        // 速度
        m_speed = byteArrayToUIntLE(chunk.sliceArray(2..3)).toInt()

        // 方位角
        if (prevLocation != null) {
            m_course = getCourseFromLocation(prevLocation)
        }
    }

    /**
     * 任意のサイズのByteArrayをリトルエンディアンの数値とみなして変換する
     */
    fun byteArrayToUIntLE(bytes: ByteArray): UInt {
        val result: UInt = bytes.reversed().fold(0u) { result, b ->
            (result shl 8) or b.toUByte().toUInt()
        }
        return result
    }

    /**
     * ２点間の方位を求める（0.01度単位）
     */
    private fun getCourseFromLocation(prevLocation: LocationData): Int {
        val x1 = prevLocation.m_latitude
        val y1 = prevLocation.m_longtitude
        val x2 = m_latitude
        val y2 = m_longtitude

        if (x1 == x2 && y1 == y2) {
            return prevLocation.m_course
        }

        val xr1 = getRadianFromMegaminutes(x1)
        val xr2 = getRadianFromMegaminutes(x2)
        val yr1 = getRadianFromMegaminutes(y1)
        val yr2 = getRadianFromMegaminutes(y2)

        val deltaX = xr2 - xr1
        val earthR: Double = 6378137.0

        val d = earthR * asin(sin(yr1) * sin(yr2) + cos(yr1) * cos(yr2) * deltaX)
        var course = -atan2(cos(yr1) * tan(yr2) - sin(yr1) * cos(deltaX), sin(deltaX))
        while (true) {
            if (course < 0.0) {
                course += Math.PI * 2
                continue
            }
            if (course > Math.PI * 2) {
                course -= Math.PI * 2
                continue
            }
            break
        }
        val deg = floor(course * 18000.0 / Math.PI).toInt()
        return deg
    }

    fun getRadianFromMegaminutes(m: Long): Double {
        val d = m.toDouble() / (60.0 * 1000000.0)
        return d * Math.PI / 180.0
    }

    /**
     * NMEA文字列として取り出す
     */
    fun getNmea(): String {
        val timeFormatter = DateTimeFormatter.ofPattern("HHmmss.nnnnnnnnn")
        val time = m_timestamp.format(timeFormatter).take(10)
        val dateFormatter = DateTimeFormatter.ofPattern("ddMMyy")
        val date = m_timestamp.format(dateFormatter)

        val latitude = getLocationString(m_latitude)
        val longtitude = getLocationString(m_longtitude)

        val knots = m_speed.toFloat() * 0.0194384f
        val speed = String.format("%.1f", knots)
        val course = String.format("%.1f", m_course.toFloat() * 0.01f)

        val line = "GPRMC,$time,A,$latitude,N,$longtitude,E,$speed,$course,$date,,,A"
        val checksum = line.fold(0) { sum, c -> sum xor c.code }

        return "\$%s*%02X".format(line, checksum)
    }

    fun getLocationString(megaMinutes: Long): String {
        val degree = megaMinutes / (60 * 1000000)
        val minute = (megaMinutes - degree * 60 * 1000000)
        val minFloor = minute / 1000000
        val minFraction = minute - minFloor * 1000000
        return "%d%02d.%06d".format(degree, minFloor, minFraction)
    }

}