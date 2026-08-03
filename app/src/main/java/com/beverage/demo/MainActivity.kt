package com.beverage.demo

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cn.xiaoyao.bluetooth.autoconnect.BleAutoConnectManager
import cn.xiaoyao.bluetooth.manager.BleConnectionManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 验证 Maven Central 依赖 io.github.zrainh:bluetooth 可正常解析与初始化
        val connectionManager = BleConnectionManager.getInstance(this)
        val autoConnectManager = BleAutoConnectManager.create(this)
        findViewById<TextView>(R.id.statusText).text = buildString {
            appendLine("io.github.zrainh:bluetooth:1.0.0")
            appendLine("BleConnectionManager: ${connectionManager.javaClass.name}")
            appendLine("BleAutoConnectManager: ${autoConnectManager.javaClass.name}")
            append("集成成功")
        }
    }
}
