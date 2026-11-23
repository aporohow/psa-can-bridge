package ru.psa.canbridge

/** Расшифровка кадров PSA CAN. Синхронизирован, используется обоими серверами. */
object Decoder {
    val FILTER_IDS = intArrayOf(
        0x21F, 0x0A2, 0x1D0, 0x165, 0x0B6, 0x0F6, 0x221, 0x12D, 0x161,
        0x0E1, 0x1E8, 0x2D7, 0x0E8, 0x10C, 0x128, 0x261
    )
    val v = HashMap<String, Double>()
    fun get(key: String, def: Double = 0.0): Double = synchronized(v) { v[key] ?: def }
    private fun s8(x: Int) = if (x >= 128) x - 256 else x

    fun feed(fid: Int, d: ByteArray) {
        if (!FILTER_IDS.contains(fid)) return
        val b = ByteArray(8); System.arraycopy(d, 0, b, 0, minOf(d.size, 8))
        val D = { i: Int -> b[i].toInt() and 0xFF }
        synchronized(v) {
            when (fid) {
                0x0B6 -> v["rpm"] = (((D(0) shl 8) or D(1)) and 0x1FFF) / 8.0
                0x0F6 -> {
                    v["volts"] = D(0) / 10.0
                    v["odo"] = ((D(2) shl 16) or (D(3) shl 8) or D(4)) / 10.0
                }
                0x221 -> v["range"] = ((D(0) shl 8) or D(1)).toDouble()
                0x0E1 -> v["outtemp"] = s8(D(0)) / 10.0
                0x165 -> {
                    v["ign"] = (D(0) and 1).toDouble()
                    v["starter"] = ((D(0) shr 1) and 1).toDouble()
                    v["ignstatus"] = D(1).toDouble()
                }
                0x21F -> {
                    v["sw_list"] = (D(0) and 1).toDouble()
                    v["sw_vol"] = ((D(0) shr 1) and 3).toDouble()
                    v["sw_seeud"] = ((D(0) shr 6) and 1).toDouble()
                    v["sw_seeuu"] = ((D(0) shr 7) and 1).toDouble()
                    v["sw_raw0"] = D(0).toDouble(); v["sw_raw1"] = D(1).toDouble()
                }
                0x0A2 -> {
                    v["radio_menu"] = D(0).toDouble()
                    v["radio_f1"] = D(1).toDouble()
                    v["radio_f3"] = D(3).toDouble()
                    v["radio_f4"] = D(4).toDouble()
                }
                0x1D0 -> {
                    v["cl_t_dr"] = D(0).toDouble(); v["cl_t_ps"] = D(1).toDouble()
                    v["cl_fan"] = D(2).toDouble()
                    v["cl_btn0"] = D(3).toDouble(); v["cl_btn1"] = D(4).toDouble()
                    v["cl_btn2"] = D(5).toDouble(); v["cl_btn3"] = D(6).toDouble()
                }
                0x12D -> v["cand_coolant"] = D(0).toDouble()
                0x161 -> v["cand_oil"] = D(0).toDouble()
                0x2D7 -> { v["light0"] = D(0).toDouble(); v["light1"] = D(1).toDouble() }
                0x0E8 -> { v["turn0"] = D(0).toDouble(); v["turn1"] = D(1).toDouble() }
                0x1E8 -> { v["status_e8_0"] = D(0).toDouble(); v["status_e8_1"] = D(1).toDouble() }
                0x10C -> v["status_10c"] = D(0).toDouble()
                0x128 -> v["status_128"] = D(0).toDouble()
                0x261 -> v["status_261"] = D(0).toDouble()
            }
        }
    }
}
