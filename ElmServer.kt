package ru.psa.canbridge

import java.net.ServerSocket
import kotlin.experimental.and

/** ELM327-эмулятор для CronyAuto: AT-команды + стандартные и кастомные PID 220Bxxx. */
object ElmServer {
    fun start(port: Int) = Thread {
        try {
            val srv = ServerSocket(port)
            log("ELM327: TCP $port, жду CronyAuto...")
            while (true) {
                val c = srv.accept()
                log("ELM327: клиент подключён")
                Thread { serve(c) }.start()
            }
        } catch (e: Exception) { log("ELM: ${e.message}") }
    }.apply { isDaemon = true }.start()

    private fun serve(c: java.net.Socket) {
        try {
            val inp = c.getInputStream()
            val out = c.getOutputStream()
            val buf = StringBuilder()
            while (true) {
                val ch = inp.read()
                if (ch == -1) break
                if (ch == 13.toChar().code) {
                    val resp = respond(buf.toString())
                    buf.setLength(0)
                    out.write(resp.toByteArray(Charsets.US_ASCII)); out.flush()
                } else buf.append(ch.toChar())
            }
        } catch (_: Exception) {}
    }

    private fun h(v: Int, n: Int) = "%0${n * 2}X".format(v and ((1 shl (n * 8)) - 1))
    private fun s8(x: Int) = if (x >= 128) x - 256 else x

    fun respond(lineRaw: String): String {
        val line = lineRaw.trim().uppercase()
        if (line.isEmpty()) return "\r\r"
        if (line == "ATZ") return "ELM327 v1.5\r\r>"
        if (line.startsWith("AT")) return "OK\r\r>"
        if (line == "0100") return "41 00 18 00 11 00\r\r>"
        if (line == "0900") return "41 00 FF 00 00 00\r\r>"
        if (line.startsWith("220B")) {
            val pid = line.substring(4, minOf(7, line.length))
            return when (pid) {
                "001" -> "62 0B 01 ${h(Decoder.get("odo").toInt() * 10, 3)}\r\r>"
                "002" -> "62 0B 02 ${h(Decoder.get("range").toInt(), 2)}\r\r>"
                "003" -> "62 0B 03 ${h(s8((Decoder.get("outtemp") * 10).toInt()) and 0xFF, 1)}\r\r>"
                "004" -> "62 0B 04 ${h(Decoder.get("ign").toInt() or (Decoder.get("starter").toInt() shl 1), 1)}\r\r>"
                "005" -> "62 0B 05 ${h(Decoder.get("cand_coolant").toInt(), 1)}\r\r>"
                "006" -> "62 0B 06 ${h(Decoder.get("cand_oil").toInt(), 1)}\r\r>"
                "007" -> "62 0B 07 ${h(Decoder.get("light0").toInt(), 1)}\r\r>"
                "008" -> "62 0B 08 ${h(Decoder.get("turn0").toInt(), 1)}\r\r>"
                else -> "NO DATA\r\r>"
            }
        }
        if (line.length == 4 && line.startsWith("01")) {
            val pid = line.substring(2).toInt(16)
            return when (pid) {
                0x0C -> { val r = (Decoder.get("rpm") * 4).toInt() and 0xFFFF
                          "41 0C ${"%02X".format(r shr 8)} ${"%02X".format(r and 0xFF)}\r\r>" }
                0x42 -> { val r = (Decoder.get("volts") * 1000).toInt() and 0xFFFF
                          "41 42 ${"%02X".format(r shr 8)} ${"%02X".format(r and 0xFF)}\r\r>" }
                0x05 -> "41 05 ${"%02X".format((s8(Decoder.get("cand_coolant").toInt()) + 40) and 0xFF)}\r\r>"
                0x46 -> "41 46 ${"%02X".format((s8((Decoder.get("outtemp") * 10).toInt()) + 40) and 0xFF)}\r\r>"
                0x5C -> "41 5C ${"%02X".format((s8(Decoder.get("cand_oil").toInt()) + 40) and 0xFF)}\r\r>"
                0x0F -> "41 0F 28\r\r>"
                else -> "NO DATA\r\r>"
            }
        }
        return "NO DATA\r\r>"
    }
    private fun log(s: String) = android.util.Log.i("PSABridge", s)
}
