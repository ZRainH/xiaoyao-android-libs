package com.tennis.base.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tennis.base.mvi.IUiIntent
import com.tennis.base.mvi.IUiState
import com.tennis.base.mvi.PageStatus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach
import kotlin.jvm.javaClass

/**
 * 纯净版 BaseViewModel (不包含任何网络层逻辑)
 */
abstract class RootViewModel : ViewModel() {

    protected val TAG: String = this::class.java.simpleName

    private val _uiIntentFlow = Channel<IUiIntent>(Channel.UNLIMITED)
    val uiIntentFlow: Flow<IUiIntent> = _uiIntentFlow.consumeAsFlow()

    private val stateCache = ConcurrentHashMap<Class<out IUiState>, IUiState>()

    val _state = MutableSharedFlow<IUiState>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _pageStatusFlow = MutableSharedFlow<PageStatus>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        initialStates().forEach { state ->
            stateCache[state.javaClass] = state
            _state.tryEmit(state)
        }

        viewModelScope.launch {
            uiIntentFlow.collect { handleIntent(it) }
        }
    }

    protected abstract fun handleIntent(intent: IUiIntent)
    protected open fun initialStates(): List<IUiState> = emptyList()

    // 注意：这里的权限依然是 protected，封装良好
    protected fun sendUiState(state: IUiState) {
        stateCache[state.javaClass] = state
        _state.tryEmit(state)
    }

    protected fun sendPageStatus(status: PageStatus) {
        _pageStatusFlow.tryEmit(status)
    }

    private inline fun <reified T : IUiState> peekState(): T? {
        return stateCache[T::class.java] as? T
    }

    fun sendUiIntent(uiIntent: IUiIntent) {
        viewModelScope.launch { _uiIntentFlow.send(uiIntent) }
    }

    private inline fun <reified T : IUiState> getStateFlow(): Flow<T> {
        return _state
            .filterIsInstance<T>()
            .onStart {
                val cachedState = stateCache[T::class.java] as? T
                if (cachedState != null) emit(cachedState)
            }
    }

    fun getPageStatusFlow(): Flow<PageStatus> = _pageStatusFlow.asSharedFlow()
}

/*
*
* package com.tennis.common

import androidx.lifecycle.viewModelScope
import com.tennis.base.viewmodel.BaseViewModel
import com.tennis.base.mvi.PageStatus
import com.tennis.network.BaseInfo // 这里可以安全地引入网络模块的 BaseInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 业务级别的 ViewModel 基类
 * 你的项目中所有具体的 ViewModel (如 LoginViewModel) 都应该继承这个类
 */
abstract class AppBaseViewModel : BaseViewModel() {

    /**
     * 统一处理后台返回的 BaseInfo
     */
    protected fun <B> requestApi(
        request: suspend () -> BaseInfo<B>,
        isShowLoading: Boolean = true,
        onSuccess: (B?) -> Unit,
        onError: ((code: Int, msg: String) -> Boolean)? = null
    ) {
        viewModelScope.launch {
            if (isShowLoading) sendPageStatus(PageStatus.Loading)

            try {
                val response = request()
                if (isShowLoading) sendPageStatus(PageStatus.Success)

                if (response.ok) {
                    onSuccess(response.data)
                } else {
                    val errorMsg = response.msg ?: "业务请求失败(Code:${response.code})"
                    handleError(response.code, errorMsg, onError)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                if (isShowLoading) sendPageStatus(PageStatus.Success)
                handleError(-1, "网络请求失败，请检查网络设置", onError)
            }
        }
    }

    private fun handleError(code: Int, msg: String, onError: ((Int, String) -> Boolean)?) {
        val isHandled = onError?.invoke(code, msg) ?: false
        if (!isHandled) {
            // 由于继承关系，你可以直接调用父类的 protected 方法
            sendPageStatus(PageStatus.Error(msg))
        }
    }
}
* */