package com.tennis.base.fragment

import android.R
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import com.blankj.utilcode.util.BarUtils
import com.zackratos.ultimatebarx.ultimatebarx.navigationBar
import com.zackratos.ultimatebarx.ultimatebarx.statusBar
import org.greenrobot.eventbus.EventBus

abstract class BaseFragment : Fragment() {
    val TAG = this::class.java.simpleName

    protected abstract fun getLayoutResId(): Int

    protected abstract fun initView(view: View)

    protected abstract fun initData(savedInstanceState: Bundle?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (useEventBus()) {
            EventBus.getDefault().register(this)
        }
        return if (getLayoutResId() != -1) {
            val view = LayoutInflater.from(context)
                .inflate(getLayoutResId(), container, false)

            view
        } else {
            super.onCreateView(inflater, container, savedInstanceState)
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setStatusBar()
        //setNavigationBar()
        initView(view)
        initData(savedInstanceState)
    }

    /**
     * 是否开启EventBus
     */
    open fun useEventBus() = false


    override fun onDestroyView() {
        super.onDestroyView()
        if (useEventBus()) {
            EventBus.getDefault().unregister(this)
        }
    }

    protected open fun setStatusBar() {
        // 设置状态栏
        statusBar {
            // 布局是否侵入状态栏（true 不侵入，false 侵入）
            fitWindow = false
            // 状态栏背景颜色（色值）
            color = Color.TRANSPARENT
            // 状态栏背景颜色（资源 id）
            colorRes = R.color.transparent
            // 以上三个设置背景的方法用一个即可，如多次设置，后面的会把前面的覆盖掉
            // light模式：状态栏字体 true: 灰色，false: 白色 Android 6.0+
            light = true
        }

    }

    protected open fun setNavigationBar() {
        // 设置导航栏
        navigationBar {
            // 布局是否侵入状态栏（true 不侵入，false 侵入）
            fitWindow = false
            // 以上三个设置背景的方法用一个即可，如多次设置，后面的会把前面的覆盖掉
            // light模式：状态栏字体 true: 灰色，false: 白色 Android 6.0+
            light = true

        }
    }


    companion object {
        private const val TAG_OFFSET: String = "TAG_OFFSET"
        private const val KEY_OFFSET: Int = -123

        @JvmStatic
        @BindingAdapter("addMarginTopEqualStatusBarHeight")
        fun addMarginTopEqualStatusBarHeight(view: View,type : Int) {
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
    }

}