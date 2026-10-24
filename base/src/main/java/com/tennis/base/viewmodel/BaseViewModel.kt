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
import kotlinx.coroutines.launch

/**
 * Author: zyh
 * Date: 2024/1/5 10:15
 * Description: BaseViewModel
 */
abstract class BaseViewModel<UI : IUiIntent, US : IUiState> : ViewModel() {

    protected var TAG = this::class.java.simpleName
    private val _uiIntentFlow = Channel<UI>(Channel.UNLIMITED)
    val uiIntentFlow: Flow<UI> = _uiIntentFlow.consumeAsFlow()

    // 🟢 管道1：具体的业务数据流 (保存页面数据)
    private val _uiStateFlow = MutableSharedFlow<US>(
        replay = 0, // 不回放旧数据，保证一次性效果
        extraBufferCapacity = 1, // 缓冲区，防止发送太快丢失
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiStateFlow: Flow<US> = _uiStateFlow.asSharedFlow()

//    private val _effectChannel = Channel<US>(Channel.BUFFERED)
//    val effectFlow: Flow<US> = _effectChannel.receiveAsFlow()

    // 🟢 管道2：公共事件流 (专门处理 Loading, Error, 弹窗等一次性事件)
    private val _pageStatusFlow = MutableSharedFlow<PageStatus>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun sendUiIntent(uiIntent: UI) {
        viewModelScope.launch {
            _uiIntentFlow.send(uiIntent)
        }
    }

    // 发送业务状态 (给子类使用)
    fun sendUiState(state: US) {
        viewModelScope.launch {
            _uiStateFlow.emit(state)
        }

    }

    /**
     * 在 Flow 操作符（onStart / onCompletion / catch）内调用，会挂起直到 UI 收集到事件。
     */
    suspend fun emitPageStatus(status: PageStatus) {
        _pageStatusFlow.emit(status)
    }

    /** 非 suspend 场景（普通回调）使用；内部 launch 后 emit，不阻塞调用方。 */
    fun sendPageStatus(status: PageStatus) {
        viewModelScope.launch {
            _pageStatusFlow.emit(status)
        }
    }

    // 【规范化】只暴露只读的 Flow 给 UI 层，防止 UI 层去乱改数据
    fun getUiState(): Flow<US> = uiStateFlow

    fun getPageStatusFlow(): Flow<PageStatus> = _pageStatusFlow.asSharedFlow()

    init {
        // 【必须加】SharedFlow 没有默认值，必须手动发射初始状态，否则页面一开始是白板！
        viewModelScope.launch {
            _uiStateFlow.emit(initialState())
        }

        viewModelScope.launch {
            uiIntentFlow.collect {
                handleIntent(it)
            }
        }
    }

    protected abstract fun handleIntent(intent: UI)

    // 初始化状态
    abstract fun initialState(): US
}