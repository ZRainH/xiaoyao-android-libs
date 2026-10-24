package com.tennis.base.view

import android.content.Context
import android.text.SpannableString
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tennis.base.R


/**
 * @author zhang-yuheng
 * Create date 2021/9/19
 * Description: TODO: 描述该类的作用
 */
class StateLayout : FrameLayout {

    private lateinit var mContext: Context

    constructor(context: Context) : super(context) {
        mContext = context
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        mContext = context
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        mContext = context
        init()
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    private lateinit var inflater: LayoutInflater

    private lateinit var stateError: View

    private lateinit var stateLoading: View

    private lateinit var mContentView: View

    private lateinit var mEmptyView: View

    private lateinit var mProgressDialog: View

    private lateinit var mCustomView: View

    private lateinit var mTvErrorContent: AppCompatTextView

    private lateinit var mProgressbar: ProgressBar

    private var onErrorClickListener: OnClickListener? = null

    private var onEmptyClickListener: OnClickListener? = null

    private lateinit var mCustomTextView: AppCompatTextView
    private lateinit var mCustomImageView: AppCompatImageView

    private fun init() {
        inflater = LayoutInflater.from(context)

    }


    override fun onFinishInflate() {
        super.onFinishInflate()
        if (childCount > 1) {
            throw IllegalStateException("StateLayout can host only one direct child")
        }
        mContentView = getChildAt(0)

        setErrorView()
        setLoadingView()
        setEmptyView()
      //  setProgressDialog()
        setCustomView()
      //  setProgressbar()
    }

    private fun setCustomView() {
        mCustomView = inflater.inflate(R.layout.layout_state_custom, this, false)
        mCustomView.visibility = GONE
        mCustomTextView = mCustomView.findViewById(R.id.tv_custom_content)
        mCustomImageView = mCustomView.findViewById(R.id.iv_custom_pic)
        addView(mCustomView, 0)
    }

    private fun setLoadingView() {
        //加载中把布局
        stateLoading = inflater.inflate(R.layout.layout_state_loading, this, false)
        stateLoading.visibility = GONE


        addView(stateLoading, -1)
    }

    private fun setErrorView() {
        //加载错误布局
        stateError = inflater.inflate(R.layout.layout_state_error, this, false)
        stateError.visibility = GONE
        mTvErrorContent = stateError.findViewById(R.id.tv_error_content)
        mTvErrorContent.text =
            "Html.fromHtml(context.resources.getString(R.string.text_net_errors))"
        mTvErrorContent.setOnClickListener(onErrorClickListener)
        addView(stateError, 0)
    }

    private fun setEmptyView() {
        mEmptyView = inflater.inflate(R.layout.layout_state_empty, this, false)
        mEmptyView.visibility = GONE
        mEmptyView.setOnClickListener(onEmptyClickListener)
        addView(mEmptyView, 0)
    }
/*
    private fun setProgressDialog() {
        mProgressDialog = inflater.inflate(R.layout.layout_progress_dialog, this, false)
        mProgressDialog.visibility = GONE
        addView(mProgressDialog, 0)
    }*/

    /*private fun setProgressbar() {
        mProgressbar = ProgressBar(context)
        mProgressbar.isIndeterminate = false
        mProgressbar.indeterminateDrawable =
            ResourcesCompat.getDrawable(resources, R.drawable.anim_loading, null)
        val dp20 = dip2px(context, context.resources.getDimension(R.dimen.dp_20))
        val params = FrameLayout.LayoutParams(dp20, dp20)
        params.gravity = Gravity.CENTER
        mProgressbar.layoutParams =  params
        addView(mProgressbar)
        mProgressbar.visibility = GONE
    }
*/
    fun showProgressBar() {
        mProgressbar.visibility = VISIBLE
    }

    fun dismissProgressbar() {
        mProgressbar.visibility = GONE
    }

    fun showContentView() {
        hideAllChild(mContentView)
        mContentView.visibility = VISIBLE
    }

    fun showLoadingView() {
        stateLoading.visibility = VISIBLE
    }

    fun hideLoadingView() {
        stateLoading.visibility = GONE
    }

    fun singleShowLoadingView() {
        hideAllChild()
        stateLoading.visibility = VISIBLE
    }

    fun showLoadErrorView() {
        hideAllChild()
        mTvErrorContent.setOnClickListener(onErrorClickListener)
        stateError.visibility = VISIBLE
    }

    fun showEmptyView() {
        hideAllChild()
        mEmptyView.visibility = VISIBLE
    }
/*
    fun showLoadingDialog() {
        mLoadingDialog.setNoBack(false)
        if (!mLoadingDialog.isShowing) {
            mLoadingDialog.show()
        }
    }

    fun hideLoadingDialog() {
        mLoadingDialog.dismiss()
    }*/

    fun showCustomView(content: Any = "", resId: Int = -1) {
        when (content) {
            is String -> {
                mCustomTextView.text = content
            }

            is SpannableString -> {
                mCustomTextView.text = content
            }
        }
        if (resId == -1) {
            mCustomImageView.visibility = GONE
        } else {
            mCustomImageView.setImageResource(resId)
        }
        hideAllChild()
        mCustomView.visibility = VISIBLE
    }

    /**
     * 隐藏所有的子控件
     */
    private fun hideAllChild(vararg exceptViews: View) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            var needHide = true
            for (exceptView in exceptViews) {
                if (child === exceptView) {
                    needHide = false
                    break
                }
            }
            child.visibility = if (needHide) INVISIBLE else VISIBLE
        }
    }

    fun setOnEmptyClickListener(listener: OnClickListener) {
        this.onEmptyClickListener = listener
    }

    fun setOnErrorClickListener(listener: OnClickListener) {
        this.onErrorClickListener = listener
    }
}