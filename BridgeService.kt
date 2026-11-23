package ru.psa.canbridge

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat

/** Сервис: демо/трасса/адаптер + два TCP-сервера. Работает в фоне. */
class BridgeService : Service() {
    private var thread: Thread? = null
    @Volatile private var running = false
    var traceUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        RealDashServer.start(35000)
        ElmServer.start(35001)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("mode") ?: return START_NOT_STICKY
        intent.getStringExtra("traceUri")?.let { traceUri = Uri.parse(it) }
        startForeground(1, notification("Режим: $mode"))
        running = false
        thread?.interrupt()
        running = true
        thread = Thread {
            when (mode) {
                "demo" -> demoLoop()
                "trace" -> traceLoop()
                "port" -> portLoop()
            }
        }.apply { start() }
        return START_STICKY
    }

    private fun demoLoop() {
        val t0 = SystemClock.elapsedRealtime()
        while (running) {
            val t = (SystemClock.elapsedRealtime() - t0) / 1000.0
            val (rpm, ign, st) = when {
                t < 2 -> Triple(0.0, 0, 0)
                t < 4 -> Triple(300.0, 1, 1)
                t < 20 -> Triple(762.0 + 400.0 * kotlin.math.abs(((t / 1.7) % 2) - 1), 1, 0)
                else -> Triple(762.0, 1, 0)
            }
            val volts = if (t < 2) 12.6 else 14.2
            val r = (rpm * 8).toInt()
            RealDashServer.sendFrame(0x0B6, byteArrayOf(((r shr 8) and 0x1F).toByte(), (r and 0xFF).toByte()))
            Decoder.feed(0x0B6, byteArrayOf(((r shr 8) and 0x1F).toByte(), (r and 0xFF).toByte()))
            val f6 = byteArrayOf((volts * 10).toInt().toByte(), 0, 0x32, 0x72, 0x95, 0, 0, 0)
            RealDashServer.sendFrame(0x0F6, f6); Decoder.feed(0x0F6, f6)
            val f165 = byteArrayOf((ign or (st shl 1)).toByte(), 0, 0, 0, 0, 0, 0, 0)
            RealDashServer.sendFrame(0x165, f165); Decoder.feed(0x165, f165)
            val f221 = byteArrayOf(0x03, 0x5C, 0, 0, 0, 0, 0, 0)
            RealDashServer.sendFrame(0x221, f221); Decoder.feed(0x221, f221)
            val fe1 = byteArrayOf(0x38, 0, 0, 0, 0, 0, 0, 0)
            RealDashServer.sendFrame(0x0E1, fe1); Decoder.feed(0x0E1, fe1)
            val f1d0 = byteArrayOf(14, 14, 0, 0, 0, 0, 0, 0)
            RealDashServer.sendFrame(0x1D0, f1d0); Decoder.feed(0x1D0, f1d0)
            val btn = (if ((t.toInt() / 6) % 2 == 1) 0x04 else 0x00).toByte()
            val f21f = byteArrayOf(btn, 0, 0, 0, 0, 0, 0, 0)
            RealDashServer.sendFrame(0x21F, f21f); Decoder.feed(0x21F, f21f)
            RealDashServer.sendFrame(0x0A2, byteArrayOf(0x01, 0, 0, 0, 0, 0, 0, 0))
            val fc = byteArrayOf(0x5A, 0, 0, 0, 0, 0, 0, 0)
            RealDashServer.sendFrame(0x12D, fc); Decoder.feed(0x12D, fc)
            val fo = byteArrayOf(0x40, 0, 0, 0, 0, 0, 0, 0)
            RealDashServer.sendFrame(0x161, fo); Decoder.feed(0x161, fo)
            SystemClock.sleep(50)
        }
    }

    private fun traceLoop() {
        val uri = traceUri ?: return
        try {
            val reader = contentResolver.openInputStream(uri)!!.bufferedReader()
            val speed = 20
            val re = Regex("([0-9A-Fa-f]{3,4})\\s+((?:[0-9A-Fa-f]{2}\\s+){8})")
            reader.forEachLine { line ->
                if (!running) return@forEachLine
                val m = re.find(line) ?: return@forEachLine
                val fid = m.groupValues[1].toInt(16)
                if (!Decoder.FILTER_IDS.contains(fid)) return@forEachLine
                val bytes = m.groupValues[2].trim().replace(" ", "")
                    .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                Decoder.feed(fid, bytes)
                RealDashServer.sendFrame(fid, bytes)
                SystemClock.sleep((50 / speed).toLong())
            }
        } catch (e: Exception) { android.util.Log.e("PSABridge", "trace: ${e.message}") }
    }

    private fun portLoop() {
        val dev = RobotellDevice(this).find() ?: return
        if (!RobotellDevice(this).hasPermission(dev)) return
        val device = RobotellDevice(this)
        if (!device.open(dev)) return
        device.readLoop(
            onFrame = { fid, bytes ->
                Decoder.feed(fid, bytes)
                RealDashServer.sendFrame(fid, bytes)
            },
            stop = { !running }
        )
        device.close()
    }

    private fun notification(text: String): Notification {
        val ch = NotificationChannel("bridge", "PSA CAN Bridge", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "bridge")
            .setContentTitle("PSA CAN Bridge активен")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { running = false; super.onDestroy() }
}
