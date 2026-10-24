package com.tennis.base.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bright.stars.utils.TimeGroupManager
import com.bright.stars.utils.TimeGroupManager.toMinuteKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间分组列表适配器基类
 * @param T 数据类型
 * @param ContentVB 内容项布局的ViewDataBinding类型
 * @param HeaderVB 时间标题布局的ViewDataBinding类型
 */
abstract class BaseTimeGroupAdapter<T, ContentVB : ViewDataBinding, HeaderVB : ViewDataBinding> :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TIME_HEADER = 0  // 时间标题类型
        private const val VIEW_TYPE_CONTENT = 1      // 内容类型
    }

    // 数据列表
    private val items = mutableListOf<TimeGroupItem<T>>()

    // 排序开关（默认为倒序：最新在前）
    private var isAscendingOrder = true

    // 事件监听器
    var onItemClickListener: ((T, Int) -> Unit)? = null

    // ==================== 抽象方法 ====================

    /**
     * 获取内容项布局资源ID
     */
    protected abstract fun getContentLayoutRes(): Int

    /**
     * 获取时间标题布局资源ID
     */
    protected abstract fun getHeaderLayoutRes(): Int

    /**
     * 绑定内容视图
     * @param binding 内容项布局的ViewDataBinding
     * @param item 数据项
     * @param position 位置
     * @param onItemClick 点击事件监听器
     */
    protected abstract fun bindContent(
        binding: ContentVB,
        item: TimeGroupItem.ContentItem<T>,
        position: Int,
        onItemClick: ((T, Int) -> Unit)?
    )

    /**
     * 绑定时间标题视图
     * @param binding 时间标题布局的ViewDataBinding
     * @param header 时间标题数据
     */
    protected abstract fun bindHeader(
        binding: HeaderVB,
        header: TimeGroupItem.TimeHeader
    )

    /**
     * 从数据对象中获取创建时间
     */
    protected abstract fun getCreateTime(data: T): String

    /**
     * 从数据对象中获取唯一ID
     */
    protected abstract fun getId(data: T): String

    // ==================== 公开API ====================

    /**
     * 1. 设置完整数据（完全替换）
     * @param dataList 数据列表
     * @param isAscending true=正序（最早在前），false=倒序（最新在前）
     */
    fun setData(dataList: List<T>, isAscending: Boolean = false) {
        this.isAscendingOrder = isAscending
        val groupedItems = convertToGroupedItems(dataList, isAscending)
        updateItems(groupedItems)
    }

    /**
     * 2. 添加更多数据（上拉加载，追加到列表末尾）
     * @param dataList 新数据列表
     */
    fun addMoreData(dataList: List<T>) {
        if (dataList.isEmpty()) return

        val newGroupedItems = convertToGroupedItems(dataList, isAscendingOrder)

        if (items.isEmpty()) {
            updateItems(newGroupedItems)
        } else {
            appendItems(newGroupedItems)
        }
    }

    /**
     * 3. 添加单个新数据（根据排序方向插入到正确位置）
     * @param data 新数据
     */
    fun addNewData(data: T) {
        val timestamp = TimeGroupManager.parseTimeString(getCreateTime(data))
        val minuteKey = timestamp.toMinuteKey()

        val newContent = createContentItem(data, timestamp, minuteKey)

        val insertIndex = findInsertPosition(timestamp)
        val headerIndex = findHeaderIndex(minuteKey)

        if (headerIndex == -1) {
            insertNewTimeGroup(insertIndex, timestamp, minuteKey, newContent)
        } else {
            insertIntoExistingGroup(headerIndex, timestamp, newContent)
        }
    }

    /**
     * 4. 更新条目数据
     * @param dataId 数据ID
     * @param updater 数据更新函数
     */
    fun updateItem(dataId: String, updater: (T) -> T) {
        val position = items.indexOfFirst {
            it is TimeGroupItem.ContentItem<T> && getId(it.data) == dataId
        }

        if (position >= 0) {
            updateContentItem(position, updater)
        }
    }

    fun updateItem(position: Int, updater: (T) -> T) {
        if (position >= 0) {
            updateContentItem(position, updater)
        }
    }

    /**
     * 5. 切换排序方式
     */
    fun toggleSortOrder(isAscending: Boolean) {
        if (this.isAscendingOrder == isAscending) return

        this.isAscendingOrder = isAscending
        val allData = getAllData()
        setData(allData, isAscending)
    }

    /**
     * 6. 获取当前排序方式
     */
    fun isAscendingOrder(): Boolean = isAscendingOrder

    /**
     * 7. 获取所有数据
     */
    fun getAllData(): List<T> {
        return items.filterIsInstance<TimeGroupItem.ContentItem<T>>()
            .map { it.data }
    }

    /**
     * 8. 清空数据
     */
    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    // ==================== RecyclerView 实现 ====================

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TimeGroupItem.TimeHeader -> VIEW_TYPE_TIME_HEADER
            is TimeGroupItem.ContentItem<*> -> VIEW_TYPE_CONTENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_TIME_HEADER -> {
                val binding = DataBindingUtil.inflate<HeaderVB>(
                    inflater,
                    getHeaderLayoutRes(),
                    parent,
                    false
                )
                TimeHeaderViewHolder(binding, parent.context, ::bindHeader)
            }

            VIEW_TYPE_CONTENT -> {
                val binding = DataBindingUtil.inflate<ContentVB>(
                    inflater,
                    getContentLayoutRes(),
                    parent,
                    false
                )
                ContentViewHolder(binding, parent.context, ::bindContent)
            }

            else -> throw IllegalArgumentException("未知的视图类型: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TimeHeaderViewHolder<*> -> {
                val item = items[position] as TimeGroupItem.TimeHeader
                @Suppress("UNCHECKED_CAST")
                (holder as TimeHeaderViewHolder<HeaderVB>).bind(item)
            }

            is ContentViewHolder<*, *> -> {
                val item = items[position] as TimeGroupItem.ContentItem<T>
                @Suppress("UNCHECKED_CAST")
                (holder as ContentViewHolder<T, ContentVB>).bind(
                    item,
                    position,
                    onItemClickListener
                )
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 将数据列表转换为分组项列表
     */
    private fun convertToGroupedItems(
        dataList: List<T>,
        isAscending: Boolean
    ): List<TimeGroupItem<T>> {
        if (dataList.isEmpty()) return emptyList()

        val result = mutableListOf<TimeGroupItem<T>>()
        val groupedByMinute = dataList.groupBy { data ->
            TimeGroupManager.parseTimeString(getCreateTime(data)).toMinuteKey()
        }

        val sortedKeys = groupedByMinute.keys.sortedBy { key ->
            TimeGroupManager.parseMinuteKeyToTimestamp(key)
        }

        val orderedKeys = if (isAscending) sortedKeys else sortedKeys.reversed()

        orderedKeys.forEach { minuteKey ->
            val minuteData = groupedByMinute[minuteKey] ?: return@forEach

            val firstData = minuteData.firstOrNull() ?: return@forEach
            val timestamp = TimeGroupManager.parseTimeString(getCreateTime(firstData))
            // 这里需要进行类型转换
            val timeHeader = TimeGroupItem.TimeHeader(
                minuteText = TimeGroupManager.getFriendlyTimeDisplay(timestamp),
                itemCount = minuteData.size,
                timestamp = timestamp,
                minuteKey = minuteKey
            )
            result.add(timeHeader)

            val sortedData = if (isAscending) {
                minuteData.sortedBy { TimeGroupManager.parseTimeString(getCreateTime(it)) }
            } else {
                minuteData.sortedByDescending { TimeGroupManager.parseTimeString(getCreateTime(it)) }
            }

            sortedData.forEach { data ->
                val contentTimestamp = TimeGroupManager.parseTimeString(getCreateTime(data))
                result.add(
                    TimeGroupItem.ContentItem(
                        data = data,
                        dataId = getId(data),  // 使用 getId 方法获取数据ID
                        formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(contentTimestamp)),
                        timestamp = contentTimestamp,
                        minuteKey = minuteKey
                    )
                )
            }
        }

        return result
    }

    /**
     * 更新整个数据列表
     */
    private fun updateItems(newItems: List<TimeGroupItem<T>>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldItem = items.getOrNull(oldPos)
                val newItem = newItems.getOrNull(newPos)
                return oldItem?.id == newItem?.id
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldItem = items.getOrNull(oldPos)
                val newItem = newItems.getOrNull(newPos)
                return oldItem == newItem
            }
        })

        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * 追加数据到末尾
     */
    private fun appendItems(newItems: List<TimeGroupItem<T>>) {
        if (newItems.isEmpty()) return

        val lastHeader =
            items.lastOrNull { it is TimeGroupItem.TimeHeader } as? TimeGroupItem.TimeHeader
        val firstNewHeader =
            newItems.firstOrNull { it is TimeGroupItem.TimeHeader } as? TimeGroupItem.TimeHeader

        if (lastHeader != null && firstNewHeader != null &&
            lastHeader.minuteKey == firstNewHeader.minuteKey
        ) {
            mergeForAppend(lastHeader, firstNewHeader, newItems)
        } else {
            val oldSize = items.size
            items.addAll(newItems)
            notifyItemRangeInserted(oldSize, newItems.size)
        }
    }

    /**
     * 合并追加的数据
     */
    private fun mergeForAppend(
        lastHeader: TimeGroupItem.TimeHeader,
        firstNewHeader: TimeGroupItem.TimeHeader,
        newItems: List<TimeGroupItem<T>>
    ) {
        val mergedList = mutableListOf<TimeGroupItem<T>>()

        var i = 0
        while (i < items.size) {
            val item = items[i]
            if (item is TimeGroupItem.TimeHeader && item.minuteKey == lastHeader.minuteKey) {
                i++
                while (i < items.size && items[i].minuteKey == lastHeader.minuteKey) {
                    i++
                }
            } else {
                mergedList.add(item)
                i++
            }
        }

        val existingContents = items.filter {
            it is TimeGroupItem.ContentItem<T> && it.minuteKey == lastHeader.minuteKey
        }
        val newContents = newItems.filter {
            it is TimeGroupItem.ContentItem<T> && it.minuteKey == firstNewHeader.minuteKey
        }
        val totalCount = existingContents.size + newContents.size

        mergedList.add(lastHeader.copy(itemCount = totalCount))

        val allContents = if (isAscendingOrder) {
            (existingContents + newContents).sortedBy { it.timestamp }
        } else {
            (existingContents + newContents).sortedByDescending { it.timestamp }
        }

        mergedList.addAll(allContents)

        for (item in newItems) {
            if (item.minuteKey != lastHeader.minuteKey) {
                mergedList.add(item)
            }
        }

        items.clear()
        items.addAll(mergedList)
        notifyDataSetChanged()
    }

    /**
     * 创建内容项
     */
    private fun createContentItem(
        data: T,
        timestamp: Long,
        minuteKey: String
    ): TimeGroupItem.ContentItem<T> {
        return TimeGroupItem.ContentItem(
            data = data,
            dataId = getId(data),  // 使用抽象方法获取数据ID
            formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)),
            timestamp = timestamp,
            minuteKey = minuteKey
        )
    }

    /**
     * 查找插入位置
     */
    private fun findInsertPosition(timestamp: Long): Int {
        return if (isAscendingOrder) {
            var insertIndex = items.size
            for (i in items.indices.reversed()) {
                if (items[i].timestamp <= timestamp) {
                    break
                }
                insertIndex = i
            }
            insertIndex
        } else {
            var insertIndex = 0
            for (i in items.indices) {
                if (items[i].timestamp > timestamp) {
                    insertIndex = i + 1
                } else {
                    break
                }
            }
            insertIndex
        }
    }

    /**
     * 查找时间标题的索引
     */
    private fun findHeaderIndex(minuteKey: String): Int {
        return items.indexOfFirst {
            it is TimeGroupItem.TimeHeader && it.minuteKey == minuteKey
        }
    }

    /**
     * 插入新的时间组
     */
    private fun insertNewTimeGroup(
        insertIndex: Int,
        timestamp: Long,
        minuteKey: String,
        newContent: TimeGroupItem.ContentItem<T>
    ) {
        val header = TimeGroupItem.TimeHeader(
            minuteText = TimeGroupManager.getFriendlyTimeDisplay(timestamp),
            itemCount = 1,
            timestamp = timestamp,
            minuteKey = minuteKey
        )

        items.add(insertIndex, header)
        items.add(insertIndex + 1, newContent)
        notifyItemRangeInserted(insertIndex, 2)
    }

    /**
     * 插入到现有时间组
     */
    private fun insertIntoExistingGroup(
        headerIndex: Int,
        timestamp: Long,
        newContent: TimeGroupItem.ContentItem<T>
    ) {
        val header = items[headerIndex] as TimeGroupItem.TimeHeader
        items[headerIndex] = header.copy(itemCount = header.itemCount + 1)

        var contentIndex = headerIndex + 1
        if (isAscendingOrder) {
            while (contentIndex < items.size &&
                items[contentIndex] is TimeGroupItem.ContentItem<T> &&
                items[contentIndex].timestamp <= timestamp
            ) {
                contentIndex++
            }
        } else {
            while (contentIndex < items.size &&
                items[contentIndex] is TimeGroupItem.ContentItem<T> &&
                items[contentIndex].timestamp > timestamp
            ) {
                contentIndex++
            }
        }

        items.add(contentIndex, newContent)
        notifyItemInserted(contentIndex)
        notifyItemChanged(headerIndex)
    }

    /**
     * 更新内容项数据
     */
    private fun updateContentItem(position: Int, updater: (T) -> T) {
        val content = items[position] as TimeGroupItem.ContentItem<T>
        val updatedData = updater(content.data)

        items[position] = content.copy(data = updatedData)
        notifyItemChanged(position)
    }

}

// 将 ViewHolder 移到外部，作为普通类
class TimeHeaderViewHolder<HeaderVB : ViewDataBinding>(
    val binding: HeaderVB,
    val context: Context,
    val bindHeader: (HeaderVB, TimeGroupItem.TimeHeader) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(header: TimeGroupItem.TimeHeader) {
        bindHeader(binding, header)
    }
}

class ContentViewHolder<T, ContentVB : ViewDataBinding>(
    val binding: ContentVB,
    val context: Context,
    val bindContent: (ContentVB, TimeGroupItem.ContentItem<T>, Int, ((T, Int) -> Unit)?) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: TimeGroupItem.ContentItem<T>, position: Int, onItemClick: ((T, Int) -> Unit)?) {
        bindContent(binding, item, position, onItemClick)
    }
}

/**
 * 时间分组项基类
 * @param T 数据类型
 */
sealed class TimeGroupItem<out T> {

    /**
     * 唯一标识符，用于DiffUtil
     */
    abstract val id: String

    /**
     * 时间戳，用于排序
     */
    abstract val timestamp: Long

    /**
     * 分钟级别的key，用于分组
     */
    abstract val minuteKey: String

    /**
     * 时间标题项
     * @param minuteText 显示的时间文本（如"今天 14:30"）
     * @param itemCount 该时间组内的条目数量
     * @param timestamp 时间戳
     * @param minuteKey 分钟key
     */
    data class TimeHeader(
        val minuteText: String,
        val itemCount: Int,
        override val timestamp: Long,
        override val minuteKey: String
    ) : TimeGroupItem<Nothing>() {
        override val id: String = "header_$minuteKey"
    }

    /**
     * 内容项
     * @param data 具体的数据对象
     * @param dataId 数据唯一ID
     * @param formattedTime 格式化的时间（如"14:30"）
     * @param timestamp 时间戳
     * @param minuteKey 分钟key
     */
    data class ContentItem<T>(
        val data: T,
        val dataId: String,
        val formattedTime: String,
        override val timestamp: Long,
        override val minuteKey: String
    ) : TimeGroupItem<T>() {
        override val id: String = "content_${dataId}_$timestamp"
    }
}