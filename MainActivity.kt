package ru.psa.canbridge

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Один экран: режимы Демо / Трасса / Адаптер + порты. Всё по-русски. */
class MainActivity : Activity() {
    private val pickTrace = 42
    private val usbAction = "ru.psa.canbridge.USB_PERMISSION"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        layout.addView(TextView(this).apply { text =
            "PSA CAN Bridge\n\n" +
            "RealDash: TCP 35000 (приборка)\n" +
            "CronyAuto: TCP 35001 (ELM327, WiFi)\n\n" +
            "1. Демо - проверка без машины\n" +
            "2. Трасса - репетиция записи поездки\n" +
            "3. Адаптер - живой Robotell по OTG" })
        val rdPort = EditText(this).apply { hint = "Порт RealDash"; setText("35000") }
        val elmPort = EditText(this).apply { hint = "Порт ELM/CronyAuto"; setText("35001") }
        layout.addView(rdPort); layout.addView(elmPort)

        fun start(mode: String, uri: Uri? = null) {
            val i = Intent(this, BridgeService::class.java)
                .putExtra("mode", mode)
                .putExtra("traceUri", uri?.toString())
                .putExtra("rdPort", rdPort.text.toString().toIntOrNull() ?: 35000)
                .putExtra("elmPort", elmPort.text.toString().toIntOrNull() ?: 35001)
            ContextCompat.startForegroundService(this, i)
            Toast.makeText(this, "Мост запущен: $mode", Toast.LENGTH_SHORT).show()
        }
        fun button(text: String, action: () -> Unit) =
            Button(this).apply { this.text = text; setOnClickListener { action() } }

        layout.addView(button("Демо - синтетическая поездка") { start("demo") })
        layout.addView(button("Трасса - выбрать файл записи") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, pickTrace)
        })
        layout.addView(button("Адаптер - живой Robotell (OTG)") {
            val dev = RobotellDevice(this).find()
            when {
                dev == null -> Toast.makeText(this,
                    "Robotell не найден (CH340 1A86:7523). Подключите по OTG.",
                    Toast.LENGTH_LONG).show()
                !RobotellDevice(this).hasPermission(dev) -> {
                    registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(c: Context, i: Intent) {
                            if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                start("port")
                            } else Toast.makeText(this@MainActivity,
                                "Разрешение USB не выдано", Toast.LENGTH_LONG).show()
                        }
                    }, IntentFilter(usbAction))
                    val pi = android.app.PendingIntent.getBroadcast(this, 0,
                        Intent(usbAction), android.app.PendingIntent.FLAG_MUTABLE)
                    (getSystemService(Context.USB_SERVICE) as UsbManager)
                        .requestPermission(dev, pi)
                    Toast.makeText(this, "Выдайте разрешение USB", Toast.LENGTH_SHORT).show()
                }
                else -> start("port")
            }
        })
        layout.addView(button("Стоп") {
            stopService(Intent(this, BridgeService::class.java))
            Toast.makeText(this, "Мост остановлен", Toast.LENGTH_SHORT).show()
        })
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickTrace && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val i = Intent(this, BridgeService::class.java)
                    .putExtra("mode", "trace").putExtra("traceUri", uri.toString())
                ContextCompat.startForegroundService(this, i)
                Toast.makeText(this, "Трасса запущена (x20)", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
