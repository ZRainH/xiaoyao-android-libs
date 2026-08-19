package com.beverage.demo

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.beverage.demo.protocol.DispenseSegment
import com.beverage.demo.protocol.WxPayload
import com.beverage.demo.serial.BeverageSerialClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val serial = BeverageSerialClient()

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)

        findViewById<MaterialButton>(R.id.btnOpen).setOnClickListener { openSerial() }
        findViewById<MaterialButton>(R.id.btnClose).setOnClickListener {
            serial.close()
            appendLog("已关闭串口")
        }
        findViewById<MaterialButton>(R.id.btnStop).setOnClickListener {
            lifecycleScope.launch {
                serial.stopDispense()
                    .onSuccess { msg ->
                        appendLog("停止应答 msgId=${msg.frame.messageId}")
                    }
                    .onFailure { appendLog("停止失败：${it.message}") }
            }
        }
        findViewById<MaterialButton>(R.id.btnDispense).setOnClickListener {
            // 文档示例：1号引脚，速度 0x3232，时间 0x00093F ms
            lifecycleScope.launch {
                serial.segmentDispense(
                    listOf(
                        DispenseSegment(
                            pin = 1,
                            forward = true,
                            speed = 0x3232,
                            durationMs = 0x00093F
                        )
                    )
                ).onSuccess { msg ->
                        appendLog("出料应答 msgId=${msg.frame.messageId}")
                    }
                    .onFailure { appendLog("出料失败：${it.message}") }
            }
        }
        findViewById<MaterialButton>(R.id.btnClean).setOnClickListener {
            // 文档示例参数
            lifecycleScope.launch {
                serial.clean(
                    totalPin = 0x21,
                    totalForward = true,
                    speed = 0x3232,
                    loopPin = 0x06,
                    loopDirection = 0x01,
                    loopDurationMs = 0x002710
                ).onSuccess { msg ->
                    appendLog("清洗应答 msgId=${msg.frame.messageId}")
                }
                    .onFailure { appendLog("清洗失败：${it.message}") }
            }
        }

        lifecycleScope.launch {
            serial.state.collect { state ->
                statusText.text = "串口 ${BeverageSerialClient.DEFAULT_PATH} @ " +
                    "${BeverageSerialClient.DEFAULT_BAUD}  |  $state"
            }
        }
        lifecycleScope.launch {
            serial.rawLog.collect { line -> appendLog(line) }
        }
        lifecycleScope.launch {
            serial.messages.collect { msg ->
                val summary = when (val p = msg.payload) {
                    is WxPayload.StopResponse ->
                        "停止应答: ${BeverageSerialClient.statusText(p.status)}"
                    is WxPayload.SegmentDispenseResponse ->
                        "出料应答: " + p.items.joinToString { "${it.pin}=${BeverageSerialClient.statusText(it.status)}" }
                    is WxPayload.DispenseFinished ->
                        "出料结束: ${BeverageSerialClient.statusText(p.status)}"
                    is WxPayload.CleanResponse ->
                        "清洗应答: pin=${p.pin} ${BeverageSerialClient.statusText(p.status)}"
                    is WxPayload.CleanFinished ->
                        "清洗结束: ${BeverageSerialClient.statusText(p.status)}"
                    is WxPayload.PinDispenseResponse ->
                        "单引脚应答: pin=${p.pin} ${BeverageSerialClient.statusText(p.status)}"
                    else ->
                        BeverageSerialClient.functionName(msg.frame.functionCode)
                }
                appendLog("MSG msgId=${msg.frame.messageId} $summary")
            }
        }

        appendLog("准备就绪。请打开 ${BeverageSerialClient.DEFAULT_PATH}")
    }

    private fun openSerial() {
        val result = serial.open()
        result.onSuccess {
            appendLog("串口已打开")
        }.onFailure { e ->
            appendLog("打开失败: ${e.message}")
            statusText.text = "打开失败: ${e.message}"
        }
    }

    private fun appendLog(line: String) {
        runOnUiThread {
            logText.append(line)
            logText.append("\n")
            logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        serial.release()
        super.onDestroy()
    }
}
