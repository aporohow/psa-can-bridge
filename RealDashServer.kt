package ru.psa.canbridge

import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/** TCP-сервер протокола RealDash CAN: 44 33 22 11 | ID (LE, 4 байта) | count | data(8) */
object RealDashServer {
    private val HEADER = byteArrayOf(0x44, 0x33, 0x22, 0x11)
    private val clients = CopyOnWriteArrayList<Socket>()

    fun start(port: Int) = Thread {
        try {
            val srv = ServerSocket(port)
            log("RealDash: TCP $port, жду клиента...")
            while (true) {
                val c = srv.accept()
                clients.add(c)
                log("RealDash: клиент подключён")
            }
        } catch (e: Exception) { log("RealDash: ${e.message}") }
    }.apply { isDaemon = true }.start()

    fun sendFrame(fid: Int, data: ByteArray) {
        val d = ByteArray(8); System.arraycopy(data, 0, d, 0, minOf(data.size, 8))
        val pkt = ByteArray(17)
        System.arraycopy(HEADER, 0, pkt, 0, 4)
        pkt[4] = (fid and 0xFF).toByte(); pkt[5] = ((fid shr 8) and 0xFF).toByte()
        pkt[6] = ((fid shr 16) and 0xFF).toByte(); pkt[7] = ((fid shr 24) and 0xFF).toByte()
        pkt[8] = 8
        System.arraycopy(d, 0, pkt, 9, 8)
        for (c in clients) {
            try {
                val os: OutputStream = c.getOutputStream()
                os.write(pkt); os.flush()
            } catch (e: Exception) { clients.remove(c) }
        }
    }
    private fun log(s: String) = android.util.Log.i("PSABridge", s)
}
