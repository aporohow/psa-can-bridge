package ru.psa.canbridge

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Живой адаптер Robotell (STM32 + CH340) через USB Host API.
 * Упрощённая реализация: инициализация CH340 и разбор бинарных пакетов
 * по маркерам AA AA ... 55 55. Протокол задокументирован в python-can
 * (интерфейс robotell) и basicpi.org; при расхождениях сверяемся с ним.
 */
class RobotellDevice(private val context: Context) {
    private var connection: android.hardware.usb.UsbDeviceConnection? = null
    private var endpointIn: android.hardware.usb.UsbEndpoint? = null

    fun find(): UsbDevice? {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return mgr.deviceList.values.firstOrNull {
            it.vendorId == 0x1A86 && it.productId == 0x7523   // CH340
        }
    }

    fun hasPermission(dev: UsbDevice): Boolean {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return mgr.hasPermission(dev)
    }

    fun open(dev: UsbDevice): Boolean {
        val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val conn = mgr.openDevice(dev) ?: return false
        val iface = dev.getInterface(0)
        if (!conn.claimInterface(iface, true)) return false
        connection = conn
        endpointIn = iface.getEndpoint(0)
        // Инициализация CH340: 115200 8N1 (базовая последовательность)
        conn.controlTransfer(0x40, 0x01, 0, 0, null, 0, 100)         // RESET? no-op
        conn.controlTransfer(0x40, 0x09, 0x00C2, 0, null, 0, 100)    // SET_LINE: 8N1
        conn.controlTransfer(0x40, 0x1B, 0x1A86, 0, null, 0, 100)    // BAUD: 115200
        log("Robotell: открыт, CH340 инициализирован")
        return true
    }

    /** Читает шину и отдаёт кадры в decoder. Блокирующий вызов цикла. */
    fun readLoop(onFrame: (Int, ByteArray) -> Unit, stop: () -> Boolean) {
        val conn = connection ?: return
        val ep = endpointIn ?: return
        val buf = ByteArray(512)
        val acc = java.io.ByteArrayOutputStream()
        while (!stop()) {
            val n = conn.bulkTransfer(ep, buf, buf.size, 100)
            if (n <= 0) continue
            acc.write(buf, 0, n)
            extractFrames(acc, onFrame)
        }
    }

    private fun extractFrames(acc: java.io.ByteArrayOutputStream, onFrame: (Int, ByteArray) -> Unit) {
        val data = acc.toByteArray()
        var i = 0
        while (i + 16 <= data.size) {
            if (data[i] == 0xAA.toByte() && data[i+1] == 0xAA.toByte()) {
                // маркер найден: пакет длиной до 55 55; упрощённо берём фиксированный блок
                val payload = data.copyOfRange(i, minOf(i + 16, data.size))
                // NOTE: точный разбор байт-стаффинга и CRC - см. python-can robotell;
                // здесь базовый вариант, при расхождениях сверяемся с трассой анализатора
                if (payload.size == 16) {
                    val fid = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
                    val bytes = payload.copyOfRange(8, 16)
                    onFrame(fid, bytes)
                }
                i += 16
            } else i++
        }
        acc.reset(); acc.write(data.copyOfRange(i, data.size))
    }

    fun close() {
        try { connection?.close() } catch (_: Exception) {}
        connection = null
    }
    private fun log(s: String) = android.util.Log.i("PSABridge", s)
}
