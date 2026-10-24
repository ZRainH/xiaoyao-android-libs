package com.tennis.base.dialog

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.FragmentManager
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


/**
 * @Author : 逍遥
 * @Date : on 2024-04-24.
 * @Description :
 */
abstract class BaseBottomSheetDialogFragment<V : ViewDataBinding> : BottomSheetDialogFragment() {


    // 回调监听
    private var onConfirmListener: ((Any?) -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

    protected var TAG: String = javaClass.simpleName
    protected lateinit var mBinding: V

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        mBinding = DataBindingUtil.inflate(inflater, layoutId, container, false)!!
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initData(savedInstanceState)
        val dialog = dialog as? BottomSheetDialog
        dialog?.apply {
            disableSwipeToDismiss(dialog)
        }
        //initTransparentBackground(mBinding.root)
    }

     fun initTransparentBackground(view: View){
        // 设置根布局背景
        view.setBackgroundColor(Color.TRANSPARENT)

        // 或者延迟设置，确保 BottomSheet 已初始化
        view.post {
            val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    protected abstract fun initView()

    protected abstract fun initData(savedInstanceState: Bundle?)

    protected abstract val layoutId: Int


    protected open fun getWidth() = 0

    protected open fun getHeight() = 0

    protected open fun isCanceledOnTouchOutside() = false


    override fun show(manager: FragmentManager, tag: String?) {
        try {
            // 1. 检查 Activity 是否已经销毁或保存状态
            if (manager.isStateSaved || manager.isDestroyed) {
                return
            }
            // 每次show前先取消已存在的实例
            manager.executePendingTransactions()
            val prev = manager.findFragmentByTag(tag)

            val ft = manager.beginTransaction()
            if (prev != null) {
                ft.remove(prev)
            }
            // 2. 将自己添加到事务
            ft.add(this, tag)
            // 3. 关键：允许状态丢失提交，避免崩溃
            ft.commitAllowingStateLoss()
        } catch (e: Exception) {
            // 处理异常
            e.printStackTrace()
        }
    }

    fun show(manager: FragmentManager) {
        show(manager, javaClass.simpleName)
    }

    override fun onStart() {
        super.onStart()
        setDialogAttr()
    }

    open fun setDialogAttr() {
        val window = dialog!!.window
        if (window != null) {
            dialog!!.setCanceledOnTouchOutside(isCanceledOnTouchOutside())
            // 拦截返回键
//            dialog!!.setOnKeyListener { _, keyCode, event ->
//                if (keyCode == KeyEvent.KEYCODE_BACK) {
//                    true // 消费事件
//                } else {
//                    false
//                }
//            }
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            val attributes = window.attributes
            attributes.dimAmount = 0.5f
            window.attributes = attributes
        }
        // 关键点：去除 design_bottom_sheet 的默认背景颜色
        dialog?.findViewById<View>(R.id.design_bottom_sheet)?.apply {
            background = Color.TRANSPARENT.toDrawable()
        }
    }

   open fun isHideable() = false

    open fun isDraggable() = false

    /**
     * 完全禁止下滑取消
     */
    open fun disableSwipeToDismiss(dialog: BottomSheetDialog) {
        val bottomSheet =
            dialog.findViewById<View>(R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)

            // 禁止拖拽和隐藏
            behavior.isHideable = isHideable()
            behavior.isDraggable = isDraggable()

            // 设置高度
            if (getHeight() > 0) {
                val layoutParams = sheet.layoutParams
                layoutParams.height = getHeight()
                sheet.layoutParams = layoutParams
            }else{
                sheet.layoutParams = sheet.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }


            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true

            // 设置取消点击外部
            dialog.setCanceledOnTouchOutside(isCanceledOnTouchOutside())

            // 监听状态变化，阻止隐藏
            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN && !isHideable()) {
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // 空实现
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mBinding.unbind()
    }

    // ================== 回调设置方法 ==================

    /**
     * 设置确定按钮点击监听（支持传参）
     * @param listener 回调函数，参数为任意类型的数据
     */
    fun setOnConfirmListener(listener: (Any?) -> Unit): BaseBottomSheetDialogFragment<V> {
        this.onConfirmListener = listener
        return this
    }

    /**
     * 设置取消按钮点击监听
     */
    fun setOnCancelListener(listener: () -> Unit): BaseBottomSheetDialogFragment<V> {
        this.onCancelListener = listener
        return this
    }

    /**
     * 同时设置确定和取消监听
     */
    fun setListeners(
        onConfirm: ((Any?) -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ): BaseBottomSheetDialogFragment<V> {
        this.onConfirmListener = onConfirm
        this.onCancelListener = onCancel
        return this
    }

    // ================== 回调执行方法 ==================

    /**
     * 执行确定按钮点击回调（带参数）
     * @param data 要传递的数据
     */
    protected fun performConfirm(data: Any? = null) {
        onConfirmListener?.invoke(data)
    }

    /**
     * 执行确定按钮点击回调并关闭对话框（带参数）
     * @param data 要传递的数据
     */
    protected fun performConfirmAndDismiss(data: Any? = null) {
        performConfirm(data)
        dismiss()
    }

    /**
     * 执行取消按钮点击回调
     */
    protected fun performCancel() {
        onCancelListener?.invoke()
    }

    /**
     * 执行取消按钮点击回调并关闭对话框
     */
    protected fun performCancelAndDismiss() {
        performCancel()
        dismiss()
    }
}
