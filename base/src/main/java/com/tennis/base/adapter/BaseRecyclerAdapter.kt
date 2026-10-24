package com.tennis.base.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import androidx.core.util.size
import com.chad.library.adapter4.BaseQuickAdapter
import com.chad.library.adapter4.BaseSingleItemAdapter

/**
 * @author zhang-yuheng
 * Create date 2021/9/10
 * Description: TODO: 描述该类的作用
 */
abstract class BaseRecyclerAdapter<B, VDB : ViewDataBinding>
    : RecyclerView.Adapter<BaseRecyclerAdapter<B, VDB>.RootViewHolder>() {

    protected val TAG = this::class.java.simpleName

    protected lateinit var mContext: Context

    protected var listener: OnItemClickListener<B>? = null
    protected var childListener: OnItemClickListener<B>? = null
    protected var longListener: OnLongItemClickListener<B>? = null
    private var mOnItemChildClickArray: SparseArray<OnItemChildClickListener<B>>? = null

    var data = mutableListOf<B>()

    fun addOnItemChildClickListener(@IdRes id: Int, listener: OnItemChildClickListener<B>) = apply {
        mOnItemChildClickArray =
            (mOnItemChildClickArray ?: SparseArray<OnItemChildClickListener<B>>(2)).apply {
                put(id, listener)
            }
    }

    fun setOnItemClickListener(listener: OnItemClickListener<B>) {
        this.listener = listener
    }

    fun setOnLongItemClickListener(listener: OnLongItemClickListener<B>) {
        this.longListener = listener
    }

    fun interface OnItemClickListener<B> {
        fun onItemClick(view: View, position: Int, bean: B)
    }

    fun interface OnItemChildClickListener<B>{
        fun onItemClick(view: View, position: Int, bean: B)
    }

   fun interface OnLongItemClickListener<B> {
        fun onLongItemClick(view: View,position: Int, bean: B)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RootViewHolder {
        mContext = parent.context
        val dataBinding = DataBindingUtil.inflate<VDB>(
            LayoutInflater.from(mContext),
            getAdapterItemRes(),
            parent,
            false
        )
        dataBinding.executePendingBindings()
        val holder = RootViewHolder(dataBinding)
        return holder.apply {
            bindViewClick(this)
        }
    }

    private fun bindViewClick(holder: RootViewHolder){
        mOnItemChildClickArray?.let {
            for (i in 0 until it.size) {
                val id = it.keyAt(i)
                holder.itemView.findViewById<View>(id)?.let { childView ->
                    childView.setOnClickListener { v ->
                        val position = holder.adapterPosition
                        if (position == RecyclerView.NO_POSITION) {
                            return@setOnClickListener
                        }
                        onItemChildClick(v, position)
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RootViewHolder, position: Int) {
        onBindHolder(holder.binding, position)
        onBindingViewHolder(holder, position)
        holder.binding.root.setOnClickListener {
            listener?.onItemClick(holder.itemView,position, data[position])
        }
        holder.binding.root.setOnLongClickListener {
            longListener?.onLongItemClick(holder.itemView,position,data[position])
            return@setOnLongClickListener true
        }
    }

   open fun onBindingViewHolder(holder: RootViewHolder, position: Int){

    }

    inner class RootViewHolder(var binding: VDB) : RecyclerView.ViewHolder(binding.root)

    abstract fun getAdapterItemRes(): Int

    abstract fun onBindHolder(binding: VDB, position: Int)

    @SuppressLint("NotifyDataSetChanged")
    open fun setDataSource(mutableList: MutableList<B>) {
        this.data.clear()
        this.data.addAll(mutableList)
        if (data.isNotEmpty()) {
            notifyItemRangeChanged(0,data.size)
        }
            notifyDataSetChanged()

    }

    open fun addDataSource(mutableList: MutableList<B>) {
        this.data.addAll(mutableList)
        notifyItemRangeChanged(0, mutableList.size)
    }

    open fun addDataSource(position: Int,i : B) {
        this.data.add(position,i)
        notifyItemRangeChanged(0, data.size)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    protected open fun onItemChildClick(v: View, position: Int) {
        mOnItemChildClickArray?.get(v.id)?.onItemClick(v,  position,data[position])
    }
}


//封装BaseQuickAdapter
abstract class BaseRecyclerViewAdapter<B : Any, VDB : ViewDataBinding>
    : BaseQuickAdapter<B, BaseRecyclerViewAdapter<B, VDB>.VH>() {

    inner class VH(var binding: VDB) : RecyclerView.ViewHolder(binding.root)

    protected lateinit var mContext: Context
    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): VH {
        mContext = parent.context
        val dataBinding = DataBindingUtil.inflate<VDB>(
            LayoutInflater.from(mContext),
            getAdapterItemRes(),
            parent,
            false
        )
        dataBinding.executePendingBindings()
        val holder = VH(dataBinding)
        return holder
    }

    override fun onBindViewHolder(
        holder: BaseRecyclerViewAdapter<B, VDB>.VH,
        position: Int,
        item: B?
    ) {
        onBindHolder(binding = holder.binding, position, item)
    }

    abstract fun getAdapterItemRes(): Int

    abstract fun onBindHolder(binding: VDB, position: Int, item: B?)

}

abstract class BaseSingleItemAdapter<B : Any, VDB : ViewDataBinding>: BaseSingleItemAdapter<B, BaseSingleItemAdapter<B, VDB>.VH>() {
    inner class VH(var binding: VDB) : RecyclerView.ViewHolder(binding.root)
    protected lateinit var mContext: Context
    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): VH {
        mContext = parent.context
        val dataBinding = DataBindingUtil.inflate<VDB>(
            LayoutInflater.from(mContext),
            getAdapterItemRes(),
            parent,
            false
        )
        dataBinding.executePendingBindings()
        val holder = VH(dataBinding)
        return holder
    }

    override fun onBindViewHolder(holder: BaseSingleItemAdapter<B, VDB>.VH, item: B?) {
        onBindHolder(holder.binding,item)
    }

    abstract fun getAdapterItemRes(): Int

    abstract fun onBindHolder(binding: VDB, item: B?)
}