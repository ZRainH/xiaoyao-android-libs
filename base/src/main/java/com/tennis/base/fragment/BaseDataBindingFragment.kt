package com.tennis.base.fragment

import android.os.Bundle
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

/**
 * @author zhang-yuheng
 * Create date 2023/12/27
 * Description:
 */
abstract class BaseDataBindingFragment<V : ViewDataBinding> : BaseFragment() {

    protected lateinit var mBinding: V

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initContentView(view)
        super.onViewCreated(view, savedInstanceState)
    }

    open fun initContentView(view: View) {
        mBinding = DataBindingUtil.bind<V>(view)!!
        mBinding.lifecycleOwner = this
    }


    override fun onDestroyView() {
        super.onDestroyView()
        mBinding.unbind()
    }
}