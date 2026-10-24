package com.tennis.base.activity

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat

/**
 * @author zhang-yuheng
 * Create date 2024/5/8
 * Description: 权限请求
 */

interface PermissionCallback {
    fun granted()

    fun denied()
}

class PermissionActivity : ComponentActivity() {

    private val mPermissions by lazy {
        intent.getStringArrayExtra("permissions")
    }

    private val mMessage by lazy {
        intent.getStringExtra("message")
    }

    private var visible = mutableStateOf(true)

    companion object {
        private var mCallback: PermissionCallback? = null

        fun requestPermission(
            context: Context,
            vararg permissions: String,
            message: String,
            callback: PermissionCallback?
        ) {

            //检测是否有权限
            val result = permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            if (result) {
                callback?.granted()
                return
            }
            mCallback = callback
            val intent = Intent(context, PermissionActivity::class.java)
            intent.putExtra("permissions", permissions)
            intent.putExtra("message", message)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnimatedVisibility(
                visible.value,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                        .systemBarsPadding()

                ) {

                    Card(
                        elevation = Dp(3f),
                        shape = RoundedCornerShape(Dp(8f)),
                        backgroundColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dp(10f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dp(10f))

                        ) {
//                            Image(
//                                painter = painterResource(id = R.mipmap.),
//                                contentDescription = "",
//                                modifier = Modifier
//                                    .width(Dp(25f))
//                                    .height(Dp(25f))
//                            )
//                            Text(
//                                text = mMessage ?: "", modifier = Modifier.padding(start = Dp(10f))
//                            )
                        }
                    }
                }
            }
        }

        val la = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {

            visible.value = false
            val result = !it.any { (_, value) -> !value }
            if (result) {
                mCallback?.granted()
            } else {
                mCallback?.denied()
            }

            finish()
        }


        mPermissions?.let {
            val filteredArray = it
                .filterNotNull()        // 过滤掉 null
                .filterNot { it.isBlank() }  // 过滤掉空字符串
                .toTypedArray()
            la.launch(filteredArray) }

    }
}