package jp.hay.bon2nmea

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * １地点の情報
 */
class LocationData {
    var m_timestamp: LocalDateTime
    var m_latitude: Long = 0
    var m_longtitude: Long = 0
    var m_elevation: Int = 0
    var m_speed: Int = 0

    /**
     * chunkを用いて初期化する
     */
    constructor(chunk: ByteArray) {
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

        val line = "GPRMC,$time,A,$latitude,N,$longtitude,E,$speed,,$date,,,A"
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