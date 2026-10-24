package com.tennis.base.dialog

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager


/**
 * @Author : 逍遥
 * @Date : on 2024-04-24.
 * @Description :
 */
abstract class BaseDialogFragment<V : ViewDataBinding> : DialogFragment() {
    protected var TAG: String = javaClass.simpleName
    protected lateinit var mBinding: V
    // 回调监听
    private var onConfirmListener: ((Any?) -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

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
    }

    protected abstract fun initView()

    protected abstract fun initData(savedInstanceState: Bundle?)

    protected abstract val layoutId: Int


    protected open fun getWidth() = 0

    protected open fun getHeight() = 0

    protected open fun isCanceledOnTouchOutside() = false

    // ================== 回调设置方法 ==================

    /**
     * 设置确定按钮点击监听（支持传参）
     * @param listener 回调函数，参数为任意类型的数据
     */
    fun setOnConfirmListener(listener: (Any?) -> Unit): BaseDialogFragment<V> {
        this.onConfirmListener = listener
        return this
    }

    /**
     * 设置取消按钮点击监听
     */
    fun setOnCancelListener(listener: () -> Unit): BaseDialogFragment<V> {
        this.onCancelListener = listener
        return this
    }

    /**
     * 同时设置确定和取消监听
     */
    fun setListeners(
        onConfirm: ((Any?) -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ): BaseDialogFragment<V> {
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


    override fun show(manager: FragmentManager, tag: String?) {
        try {
            // 1. 检查 Activity 是否已经销毁或保存状态
            if (manager.isStateSaved || manager.isDestroyed) {
                return
            }

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
            e.printStackTrace()
        }
    }

    fun show(manager: FragmentManager) {
        show(manager, javaClass.simpleName)
    }

    override fun onStart() {
        super.onStart()
        val window = dialog!!.window
        if (window != null) {
            dialog!!.setCanceledOnTouchOutside(isCanceledOnTouchOutside())
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            val attributes = window.attributes
            attributes.dimAmount = 0.6f
            attributes.width = getWidth()
            attributes.height = if (getHeight() == 0) WindowManager.LayoutParams.WRAP_CONTENT else getHeight()
            window.attributes = attributes
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mBinding!!.unbind()
    }
}
