package com.tennis.base.activity

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.BindingAdapter
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.ToastUtils
import com.tennis.base.R
import com.tennis.base.utils.ActivityManagerUtils
import com.tennis.base.utils.AppGuardManager
import com.tennis.base.utils.LanguageHelper
import com.zackratos.ultimatebarx.ultimatebarx.navigationBar
import com.zackratos.ultimatebarx.ultimatebarx.statusBar
import org.greenrobot.eventbus.EventBus

/**
 * @author zhang-yuheng
 * Create date 2024/3/10
 * Description: 基础 Activity，完美处理沉浸式状态栏与底部导航栏重叠
 */
abstract class BaseActivity : AppCompatActivity() {

    private var mVs: ViewStub? = null
    // 将第 40 行左右的代码改为：
    protected val TAG: String by lazy { this::class.java.simpleName }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        if (AppGuardManager.checkZombieRestart(this, savedInstanceState)) {
            return
        }

        setContentView(R.layout.layout_base)

        // 【核心优化】完美解决底部导航栏重叠：
        // 使用 ViewCompat 统一处理 WindowInsets，全版本兼容（API 21+）
        val main = findViewById<LinearLayout>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(main) { v, insets ->
            // 获取系统栏（状态栏 + 导航栏）的实时高度
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 策略：通过设置根布局的内边距来避开底部导航栏
            // 这样 root 的背景色依然会延伸到导航栏下方（实现沉浸式），但内容 View 会被限制在导航栏上方
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)

            insets
        }

        setStatusBar()
        setNavigationBar()

        mVs = findViewById(R.id.vs)
        mVs?.let { initContentView(it) }

        initView()
        initData(savedInstanceState)

        if (useEventBus()) {
            EventBus.getDefault().register(this)
        }

        ActivityManagerUtils.getInstance().addActivity(this)
    }

    open fun initContentView(vsContent: ViewStub) {
        vsContent.layoutResource = onBindLayout()
        vsContent.inflate()
    }

    abstract fun onBindLayout(): Int
    abstract fun initView()
    abstract fun initData(savedInstanceState: Bundle?)

    override fun onDestroy() {
        super.onDestroy()
        ToastUtils.cancel()
        if (useEventBus()) {
            EventBus.getDefault().unregister(this)
        }
    }

    open fun useEventBus() = false

    protected open fun setStatusBar() {
        statusBar {
            fitWindow = false // 布局侵入状态栏
            color = Color.TRANSPARENT
            light = true
        }
    }

    protected open fun setNavigationBar() {
        // 1. 使用 UltimateBarX 设置颜色和图标风格
        navigationBar {
            fitWindow = false // 允许布局延伸到导航栏区域（因为你已经手动设置了 Padding 避让）
            color = Color.TRANSPARENT // 在这里设置你想要的十六进制颜色
            light = true // true: 导航栏为浅色，系统按钮变为黑色；false: 导航栏为深色，系统按钮变为白色
        }

        // 2. 【核心修复】解决 Android 10+ 导航栏颜色变灰、不生效或有遮罩的问题
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 禁用系统强制的对比度增强，这样设置的颜色才能纯净显示
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun getResources(): Resources {
        val resources = super.getResources()
        val configuration = resources.configuration

        // 核心修复：处理字体缩放和 Android 12+ 的系统字体加粗 (fontWeightAdjustment)
        val isFontScaleChanged = configuration.fontScale != 1.0f
        val isFontWeightChanged = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && configuration.fontWeightAdjustment != 0
        val isScaledDensityChanged = resources.displayMetrics.scaledDensity != resources.displayMetrics.density

        if (isFontScaleChanged || isFontWeightChanged || isScaledDensityChanged) {
            val newConfig = Configuration(configuration)
            newConfig.fontScale = 1.0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                newConfig.fontWeightAdjustment = 0
            }
            val newRes = createConfigurationContext(newConfig).resources
            // 强制将缩放密度设为与基础密度一致
            newRes.displayMetrics.scaledDensity = newRes.displayMetrics.density
            return newRes
        }
        return resources
    }

    /**
     * 设置根布局 main 的背景颜色
     * @param color 颜色值（例如 Color.WHITE 或 "#FFFFFF".toColorInt()）
     */
    fun setMainBackgroundColor(color: Int) {
        findViewById<LinearLayout>(R.id.main)?.setBackgroundColor(color)
    }

    /**
     * 通过颜色资源 ID 设置根布局背景颜色
     * @param resId 颜色资源 ID，如 R.color.white
     */
    fun setMainBackgroundResource(@ColorRes resId: Int) {
        setMainBackgroundColor(ContextCompat.getColor(this, resId))
    }

    override fun attachBaseContext(newBase: Context) {
        // 1. 获取 LanguageHelper 处理后的 Context
        val base = LanguageHelper.attachBaseContext(newBase)

        // 2. 二次检查：确保 config 没被篡改
        val config = Configuration(base.resources.configuration)
        config.fontScale = 1.0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            config.fontWeightAdjustment = 0
        }

        // 3. 产生最终 Context
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun finish() {
        super.finish()
        ActivityManagerUtils.getInstance().removeActivity(this)
    }

    companion object {
        private const val TAG_OFFSET: String = "TAG_OFFSET"
        private const val KEY_OFFSET: Int = -123
        private const val KEY_BOTTOM_OFFSET: Int = -124

        /**
         * 状态栏避让 BindingAdapter
         */
        @JvmStatic
        @BindingAdapter("addMarginTopEqualStatusBarHeight")
        fun addMarginTopEqualStatusBarHeight(view: View, type: Int) {
            view.tag = TAG_OFFSET
            val haveSetOffset = view.getTag(KEY_OFFSET)
            if (haveSetOffset != null && haveSetOffset as Boolean) return
            val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(
                layoutParams.leftMargin,
                layoutParams.topMargin + BarUtils.getStatusBarHeight(),
                layoutParams.rightMargin,
                layoutParams.bottomMargin
            )
            view.setTag(KEY_OFFSET, true)
        }

        /**
         * 【完美方案】动态避开底部导航栏的 BindingAdapter
         */
        @JvmStatic
        @BindingAdapter("addPaddingBottomEqualNarBarHeight")
        fun addPaddingBottomEqualNarBarHeight(view: View, extraPaddingDp: Int) {
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val extraPx = ConvertUtils.dp2px(extraPaddingDp.toFloat())
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBarHeight + extraPx)
                insets
            }
        }
    }
}
