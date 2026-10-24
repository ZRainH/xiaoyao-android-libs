package com.tennis.base.activity

import android.view.ViewStub
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tennis.base.viewmodel.BaseViewModel
import com.tennis.base.viewmodel.BaseViewModelFactory
import com.tennis.base.mvi.IUiIntent
import com.tennis.base.mvi.IUiState
import kotlinx.coroutines.launch
import java.lang.reflect.ParameterizedType

/**
 * @author Zhang-yuheng
 * @description:
 * @date :2024/8/6 11:21
 */
abstract class BaseDataBindingVmActivity<V : ViewDataBinding, VM : BaseViewModel<out IUiIntent, out IUiState>> :
    BaseDataBindingActivity<V>() {
    protected lateinit var mViewModel: VM

    // 改为非抽象方法，通过反射获取VM的实际类型
    open fun onBindViewModel(): Class<VM> {
        // 获取当前类的泛型超类（即 BaseDataBindingVmFragment）
        val type = javaClass.genericSuperclass as ParameterizedType
        // 获取第二个泛型参数（VM）的实际类型
        @Suppress("UNCHECKED_CAST")
        return type.actualTypeArguments[1] as Class<VM>
    }

    abstract fun setViewModelParameter(): Array<Any>

    override fun initContentView(vsContent: ViewStub) {
        super.initContentView(vsContent)
        mViewModel = ViewModelProvider(this,
            BaseViewModelFactory(setViewModelParameter())
        )[
            onBindViewModel()
        ]
        lifecycleScope.launch {
            repeatOnLifecycle(getMinLifecycleState()) { // 避免后台更新

                //必须用内部 launch 并发收集，否则第一个 collect 会阻塞整个协程！
                launch {
                    mViewModel.getUiState().collect { state ->
                        observeViewModel(state)
                    }
                }

                launch {
                    mViewModel.getPageStatusFlow().collect { pageStatus ->
                        // 因为 PageStatus 现在也是 IUiState，所以可以直接传进去
                        observeViewModel(pageStatus)
                    }
                }
            }
        }

    }

    /**
     * 🟢 允许子类配置生命周期监听状态
     * 默认是 STARTED（界面可见时监听）
     * 如果需要在后台也继续监听（如 ViewPager2 隐藏页），子类可改为 CREATED
     */
    open fun getMinLifecycleState(): Lifecycle.State = Lifecycle.State.STARTED

    open fun observeViewModel(state: IUiState) {
    }

}