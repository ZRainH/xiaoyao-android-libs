package com.tennis.base.fragment

import android.os.Bundle
import android.view.View
import androidx.databinding.ViewDataBinding
import com.tennis.base.viewmodel.BaseViewModel
import com.tennis.base.mvi.IUiIntent
import com.tennis.base.mvi.IUiState

/**
 * 支持懒加载的 BaseDataBindingVmFragment
 */
abstract class LazyDataBindingVmFragment<V : ViewDataBinding, VM : BaseViewModel<out IUiIntent, out IUiState>> :
    BaseDataBindingVmFragment<V, VM>() {

    // 懒加载相关状态
    private var isViewCreated = false
    private var isFirstLoad = true
    private var isDataLoaded = false

    // 添加可见性状态
    private var fragmentVisible = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewCreated = true
        tryLazyLoad()
    }

    override fun onResume() {
        super.onResume()
        // 在 onResume 中检查可见性
        checkVisibility()
    }

    override fun onPause() {
        super.onPause()
        if (fragmentVisible) {
            fragmentVisible = false
            onFragmentInvisible()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        fragmentVisible = !hidden
        if (fragmentVisible) {
            tryLazyLoad()
        } else {
            onFragmentInvisible()
        }
    }

    /**
     * 设置 Fragment 可见性（由外部调用，如在 ViewPager2 的页面监听中）
     */
    fun setFragmentVisible(visible: Boolean) {
        if (fragmentVisible != visible) {
            fragmentVisible = visible
            if (visible) {
                tryLazyLoad()
            } else {
                onFragmentInvisible()
            }
        }
    }

    /**
     * 检查当前 Fragment 是否可见
     */
    private fun checkVisibility() {
        val shouldBeVisible = isVisible && !isHidden && userVisibleHint
        if (fragmentVisible != shouldBeVisible) {
            fragmentVisible = shouldBeVisible
            if (fragmentVisible) {
                tryLazyLoad()
            } else {
                onFragmentInvisible()
            }
        }
    }

    /**
     * 尝试执行懒加载
     */
    private fun tryLazyLoad() {
        if (isViewCreated && fragmentVisible) {
            if (isFirstLoad) {
                // 第一次可见时加载数据
                onLazyLoad()
                isFirstLoad = false
                isDataLoaded = true
            }
            // 每次可见时调用
            onFragmentVisible()
        }
    }

    /**
     * 懒加载数据（只在第一次可见时调用）
     */
    abstract fun onLazyLoad()

    /**
     * Fragment 可见时调用（每次可见都会调用）
     */
    open fun onFragmentVisible() {
        // 子类可重写此方法处理每次可见时的逻辑
    }

    /**
     * Fragment 不可见时调用
     */
    open fun onFragmentInvisible() {
        // 子类可重写此方法处理不可见时的逻辑
    }

    /**
     * 重置懒加载状态（用于重新加载数据）
     */
    fun resetLazyLoad() {
        isFirstLoad = true
        isDataLoaded = false
    }

    /**
     * 检查是否对用户可见
     */
    protected fun isFragmentVisible(): Boolean {
        return fragmentVisible
    }
}