package com.tennis.base.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Process
import java.lang.ref.WeakReference
import java.util.Stack

class ActivityManagerUtils private constructor() {

    private var mActivityStack: Stack<WeakReference<Activity>>? = null

    private object SingletonHolder {
        val INSTANCE: ActivityManagerUtils = ActivityManagerUtils()
    }

    companion object {
        fun getInstance(): ActivityManagerUtils {
            return SingletonHolder.INSTANCE
        }
    }

    /**
     * 添加Activity到栈
     *
     * @param activity
     */
    fun addActivity(activity: Activity) {
        if (mActivityStack == null) {
            mActivityStack = Stack()
        }
        mActivityStack!!.add(WeakReference(activity))
    }

    /**
     * 添加Activity到栈
     *
     * @param activity
     */
    @SuppressLint("ImplicitSamInstance")
    fun removeActivity(activity: Activity) {
        if (mActivityStack == null) {
            mActivityStack = Stack()
        }
        mActivityStack!!.remove(WeakReference(activity))
    }

    /**
     * 检查弱引用是否释放，若释放，则从栈中清理掉该元素
     */
    fun checkWeakReference() {
        if (mActivityStack != null) {
            val iterator = mActivityStack!!.iterator()
            while (iterator.hasNext()) {
                val activityWeakReference = iterator.next()
                val temp = activityWeakReference.get()
                if (temp == null) {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * 获取当前Activity（栈中最后一个压入的）
     *
     * @return
     */
    fun currentActivity(): Activity? {
        checkWeakReference()
        return if (mActivityStack != null && !mActivityStack!!.isEmpty()) {
            mActivityStack!!.lastElement().get()
        } else null
    }

    /**
     * 关闭当前Activity（栈中最后一个压入的）
     */
    fun finishActivity() {
        val activity = currentActivity()
        activity?.let { finishActivity(it) }
    }

    /**
     * 关闭指定的Activity
     *
     * @param activity
     */
    fun finishActivity(activity: Activity?) {
        if (activity != null && mActivityStack != null) {
            val iterator = mActivityStack!!.iterator()
            while (iterator.hasNext()) {
                val activityWeakReference = iterator.next()
                val temp = activityWeakReference.get()
                if (temp == null) {
                    iterator.remove()
                    continue
                }
                if (temp === activity) {
                    iterator.remove()
                }
            }
            activity.finish()
        }
    }

    /**
     * 关闭指定类名的所有Activity
     *
     * @param cls
     */
    fun finishActivity(cls: Class<*>) {
        if (mActivityStack != null) {
            // 使用迭代器进行安全删除
            val it = mActivityStack!!.iterator()
            while (it.hasNext()) {
                val activityReference = it.next()
                val activity = activityReference.get()
                // 清理掉已经释放的activity
                if (activity == null) {
                    it.remove()
                    continue
                }
                if (activity.javaClass == cls) {
                    it.remove()
                    activity.finish()
                }
            }
        }
    }

    /**
     * 结束所有Activity
     */
    fun finishAllActivity() {
        if (null != mActivityStack) {
            for (activityWeakReference in mActivityStack!!) {
                val activity = activityWeakReference.get()
                activity?.finish()
            }
            mActivityStack!!.clear()
        }
    }

    /**
     * 结束所有Activity  保留某页面（一般为登录）
     */
    fun finishAllExceptActivity(cls: Class<*>) {
        if (null != mActivityStack) {
            for (activityWeakReference in mActivityStack!!) {
                val activity = activityWeakReference.get()
                if (null != activity) {
                    if (activity.javaClass != cls) {
                        activity.finish()
                    }
                }
            }
            mActivityStack!!.clear()
        }
    }

    fun recreateExceptActivity(cls: Class<*>) {
        if (null != mActivityStack) {
            for (activityWeakReference in mActivityStack!!) {
                val activity = activityWeakReference.get()
                if (null != activity) {
                    if (activity.javaClass != cls) {
                        activity.recreate()
                    }
                }
            }
            mActivityStack!!.clear()
        }
    }

    /**
     * 跳转到指定的Activity并清空其他所有页面
     * @param targetActivityClass 目标Activity类
     * @param intentAction Intent动作（可选）
     * @param finishSelf 是否关闭当前Activity
     */
    fun startActivityAndClearOthers(
        targetActivityClass: Class<*>,
        intentAction: String? = null,
        finishSelf: Boolean = true
    ) {
        val currentActivity = currentActivity() ?: // 如果没有当前Activity，无法启动新页面
        return

        // 创建Intent
        val intent = Intent(currentActivity, targetActivityClass)

        // 设置Intent标志，清除所有栈并创建新任务
      //  intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        // 设置Action（如果提供）
        intentAction?.let {
            intent.action = it
        }

        // 启动目标Activity
        currentActivity.startActivity(intent)

        // 如果指定关闭当前Activity
        if (finishSelf) {
            currentActivity.finish()
        }

        // 清理Activity栈（除了目标Activity）
        clearActivityStackExcept(targetActivityClass)
    }

    /**
     * 跳转到指定的Activity并清空其他所有页面（带Bundle参数）
     * @param targetActivityClass 目标Activity类
     * @param bundle 要传递的数据
     * @param finishSelf 是否关闭当前Activity
     */
    fun startActivityAndClearOthers(
        targetActivityClass: Class<*>,
        bundle: android.os.Bundle?,
        finishSelf: Boolean = true
    ) {
        val currentActivity = currentActivity() ?: return

        val intent = Intent(currentActivity, targetActivityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        bundle?.let {
            intent.putExtras(it)
        }

        currentActivity.startActivity(intent)

        if (finishSelf) {
            currentActivity.finish()
        }

        clearActivityStackExcept(targetActivityClass)
    }

    /**
     * 跳转到指定的Activity并清空其他所有页面（带请求码，适用于startActivityForResult）
     * @param targetActivityClass 目标Activity类
     * @param requestCode 请求码
     * @param bundle 要传递的数据
     */
    fun startActivityForResultAndClearOthers(
        targetActivityClass: Class<*>,
        requestCode: Int,
        bundle: android.os.Bundle? = null
    ) {
        val currentActivity = currentActivity()
        if (currentActivity == null) {
            return
        }

        val intent = Intent(currentActivity, targetActivityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        bundle?.let {
            intent.putExtras(it)
        }

        currentActivity.startActivityForResult(intent, requestCode)

        // 不关闭当前Activity，因为需要接收结果
        // 但清理其他Activity
        clearActivityStackExcept(targetActivityClass, currentActivity)
    }
    private fun clearActivityStackExcept(keepActivityClass: Class<*>, keepActivity: Activity? = null) {
        if (mActivityStack == null) return

        // 收集需要关闭的Activity
        val activitiesToFinish = mutableListOf<Activity>()
        val iterator = mActivityStack!!.iterator()

        while (iterator.hasNext()) {
            val weakRef = iterator.next()
            val activity = weakRef.get()

            if (activity == null) {
                iterator.remove()
                continue
            }

            if (activity.javaClass != keepActivityClass || (keepActivity != null && activity !== keepActivity)) {
                activitiesToFinish.add(activity)
            }
        }

        // 移除所有需要关闭的Activity
        removeActivitiesFromStack(activitiesToFinish)

        // 关闭Activity（在遍历之后）
        activitiesToFinish.forEach { activity ->
            if (!activity.isFinishing) {
                activity.finish()
            }
        }
    }

    /**
     * 从栈中移除指定的Activities
     * @param activities 要移除的Activity列表
     */
    private fun removeActivitiesFromStack(activities: List<Activity>) {
        if (mActivityStack == null || activities.isEmpty()) return

        val iterator = mActivityStack!!.iterator()
        while (iterator.hasNext()) {
            val weakRef = iterator.next()
            val activity = weakRef.get()
            // 如果Activity为null或者是要移除的Activity，则移除
            if (activity == null || activity in activities) {
                iterator.remove()
            }
        }
    }
    /**
     * 退出应用程序
     */
    fun exitApp() {
        try {
            finishAllActivity()
            // 退出JVM,释放所占内存资源,0表示正常退出
            System.exit(0)
            // 从系统中kill掉应用程序
            Process.killProcess(Process.myPid())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 优化的跳转方法，避免白屏
     */
    fun startActivityAndClearOthersOptimized(
        targetActivityClass: Class<*>,
        bundle: android.os.Bundle? = null,
        finishSelf: Boolean = true,
        useAnimation: Boolean = true
    ) {
        val currentActivity = currentActivity()
        if (currentActivity == null) return

        // 使用 Intent.FLAG_ACTIVITY_CLEAR_TOP 替代 CLEAR_TASK
        val intent = Intent(currentActivity, targetActivityClass).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            bundle?.let { putExtras(it) }
        }

        currentActivity.startActivity(intent)

        // 使用渐变动画
        if (useAnimation) {
            currentActivity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 延迟清理其他Activity
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finishAllExceptActivity(targetActivityClass)
        }, 200)

        if (finishSelf) {
            currentActivity.finish()
        }
    }

}