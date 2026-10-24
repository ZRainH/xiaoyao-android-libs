package com.tennis.base.activity

import android.view.ViewStub
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

/**
 * @author zhang-yuheng
 * Create date 2024/3/10
 * Description:
 */
abstract class BaseDataBindingActivity<V : ViewDataBinding> : BaseActivity() {
    protected lateinit var mBinding: V

    override fun initContentView(vsContent: ViewStub) {
        vsContent.layoutResource = onBindLayout()
        initViewDataBinding(vsContent)
        vsContent.inflate()
    }

    private fun initViewDataBinding(vsContent: ViewStub) {
        vsContent.setOnInflateListener { _, inflated ->
            mBinding = DataBindingUtil.bind<V>(inflated)!!
            mBinding.lifecycleOwner = this
        }
    }

    open fun V.bindV(){

    }

    override fun onDestroy() {
        super.onDestroy()
        // 增加初始化检查，避免 Activity 未完全启动即销毁时崩溃
        if (::mBinding.isInitialized) {
            mBinding.unbind()
        }
    }
}