package com.kidslock.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {

    companion object {
        private const val REQ_NOTIFICATIONS = 2001
    }

    private lateinit var tvOverlay: TextView
    private lateinit var tvNotifications: TextView
    private lateinit var tvAlarm: TextView
    private lateinit var tvUsage: TextView
    private lateinit var tvAccessibility: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvSecure: TextView

    private lateinit var btnOverlay: Button
    private lateinit var btnNotifications: Button
    private lateinit var btnAlarm: Button
    private lateinit var btnUsage: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnBattery: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        tvOverlay = findViewById(R.id.tvOverlay)
        tvNotifications = findViewById(R.id.tvNotifications)
        tvAlarm = findViewById(R.id.tvAlarm)
        tvUsage = findViewById(R.id.tvUsage)
        tvAccessibility = findViewById(R.id.tvAccessibility)
        tvBattery = findViewById(R.id.tvBattery)
        tvSecure = findViewById(R.id.tvSecure)

        btnOverlay = findViewById(R.id.btnOverlay)
        btnNotifications = findViewById(R.id.btnNotifications)
        btnAlarm = findViewById(R.id.btnAlarm)
        btnUsage = findViewById(R.id.btnUsage)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnBattery = findViewById(R.id.btnBattery)

        btnOverlay.setOnClickListener { openOverlay() }
        btnNotifications.setOnClickListener { openNotifications() }
        btnAlarm.setOnClickListener { openAlarm() }
        btnUsage.setOnClickListener { openUsage() }
        btnAccessibility.setOnClickListener { openAccessibility() }
        btnBattery.setOnClickListener { openBattery() }

        findViewById<Button>(R.id.btnCopySecure).setOnClickListener { copySecureCommand() }
        findViewById<Button>(R.id.btnPermissionBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) refresh()
    }

    private fun refresh() {
        bind(tvOverlay, btnOverlay, R.string.perm_overlay, PermissionStatus.overlay(this))
        bind(tvNotifications, btnNotifications, R.string.perm_notifications, PermissionStatus.notifications(this))
        bind(tvAlarm, btnAlarm, R.string.perm_exact_alarm, PermissionStatus.exactAlarm(this))
        bind(tvUsage, btnUsage, R.string.perm_usage, PermissionStatus.usageAccess(this))
        bind(tvAccessibility, btnAccessibility, R.string.perm_accessibility, PermissionStatus.accessibility(this))
        bind(tvBattery, btnBattery, R.string.perm_battery, PermissionStatus.battery(this))

        val secure = PermissionStatus.secureSettings(this)
        tvSecure.text = getString(R.string.perm_secure) + "：" + getString(
            if (secure) R.string.perm_ok else R.string.perm_optional_missing
        )
        tvSecure.setTextColor(ContextCompat.getColor(this, if (secure) R.color.correct_green else R.color.text_secondary))
    }

    private fun bind(tv: TextView, btn: Button, titleRes: Int, ok: Boolean) {
        tv.text = getString(titleRes) + "：" + getString(if (ok) R.string.perm_ok else R.string.perm_missing_one)
        tv.setTextColor(ContextCompat.getColor(this, if (ok) R.color.correct_green else R.color.wrong_red))
        btn.text = getString(if (ok) R.string.perm_view else R.string.perm_open)
    }

    private fun openOverlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        if (!startSafely(intent)) {
            if (!startSafely(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) {
                toast(getString(R.string.perm_tip_overlay))
            }
        }
    }

    private fun openNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS
            )
            return
        }
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (!startSafely(intent)) toast(getString(R.string.perm_tip_notify))
    }

    private fun openAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName")
        )
        if (!startSafely(intent)) {
            if (!startSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))) {
                toast(getString(R.string.perm_tip_alarm))
            }
        }
    }

    private fun openUsage() {
        if (!startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) {
            toast(getString(R.string.perm_tip_usage))
        }
    }

    private fun openAccessibility() {
        if (RecoveryAccessibilityService.trySelfEnable(this)) {
            refresh()
            return
        }
        if (!startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
            toast(getString(R.string.perm_tip_accessibility))
        }
    }

    private fun openBattery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) {
            if (!startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
                toast(getString(R.string.perm_tip_battery))
            }
            return
        }
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        if (startSafely(direct)) return
        if (!startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            toast(getString(R.string.perm_tip_battery))
        }
    }

    private fun copySecureCommand() {
        val command = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("adb", command))
        toast(getString(R.string.perm_copied))
    }

    private fun startSafely(intent: Intent): Boolean {
        return try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
