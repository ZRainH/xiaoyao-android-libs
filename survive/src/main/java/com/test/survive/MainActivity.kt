package com.test.survive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.xiaoyao.keepalive.KeepAlive
import com.xiaoyao.keepalive.battery.BatteryOptimizationHelper
import com.xiaoyao.keepalive.vendor.VendorGuide
import com.xiaoyao.keepalive.vendor.VendorRom

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvVendor: TextView
    private lateinit var tvBattery: TextView

    private val notifyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Toast.makeText(
                this,
                if (granted) R.string.toast_notify_granted else R.string.toast_notify_denied,
                Toast.LENGTH_SHORT
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        tvVendor = findViewById(R.id.tvVendor)
        tvBattery = findViewById(R.id.tvBattery)

        tvVendor.text = getString(R.string.vendor_fmt, VendorRom.displayName())

        findViewById<MaterialButton>(R.id.btnStart).setOnClickListener {
            ensureNotifyPermissionThen {
                KeepAlive.start(this)
                refreshStatus()
            }
        }
        findViewById<MaterialButton>(R.id.btnStop).setOnClickListener {
            KeepAlive.stop(this)
            refreshStatus()
        }
        findViewById<MaterialButton>(R.id.btnNotifyPermission).setOnClickListener {
            requestNotifyPermission()
        }
        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            BatteryOptimizationHelper.requestIgnoreIfNeeded(this)
            refreshStatus()
        }
        findViewById<MaterialButton>(R.id.btnAutoStart).setOnClickListener {
            VendorGuide.openAutoStartSettings(this)
        }
        findViewById<MaterialButton>(R.id.btnBatteryUnrestricted).setOnClickListener {
            VendorGuide.openBatteryUnrestricted(this)
        }
        findViewById<MaterialButton>(R.id.btnAppDetails).setOnClickListener {
            VendorGuide.openAppDetails(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        tvStatus.setText(
            if (KeepAlive.isRunning()) R.string.status_running else R.string.status_stopped
        )
        tvBattery.setText(
            if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
                R.string.battery_yes
            } else {
                R.string.battery_no
            }
        )
    }

    private fun ensureNotifyPermissionThen(block: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            block()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            block()
        } else {
            notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            // 用户授权后可再次点启动；此处不阻塞
            block()
        }
    }

    private fun requestNotifyPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.toast_notify_granted, Toast.LENGTH_SHORT).show()
            return
        }
        notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
