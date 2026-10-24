package com.tennis.base.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.lang.reflect.Constructor


/**
 * @author Zhang-yuheng
 * @description:
 * @date :2024/8/6 10:02
 */
class BaseViewModelFactory(private val param: Array<Any> = arrayOf()) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val count = param.size
        if (count <= 0) {
            return modelClass.getDeclaredConstructor().newInstance()
        }

        return find(modelClass, param)?.newInstance(*param)
            ?: modelClass.getDeclaredConstructor().newInstance()
    }

    private fun <T> find(modelClass: Class<T>, signature: Array<Any>): Constructor<T>? {
        var hitConstructor: Constructor<T>? = null
        //获取构造函数
        val constructors = modelClass.constructors
        for (constructor in constructors) {
            //获取构造参数类型
            val parameterTypes = constructor.parameterTypes
            val size = parameterTypes.size
            //判断参数的个数是否和传入的参数一样
            if (size != signature.size) {
                continue
            }
            val match = match(signature, parameterTypes, size)
            if (match) {
                hitConstructor = constructor as Constructor<T>
                break
            }
        }
        return hitConstructor

    }

    private fun match(
        signature: Array<Any>,
        parameterTypes: Array<Class<*>>,
        parameterCount: Int
    ): Boolean {
        var match = true
        for (i in 0 until parameterCount) {
            val clz = parameterTypes[i]
            val targetClz: Class<*> = signature[i].javaClass
            val equals = if (clz.name == "int" && targetClz.name == "java.lang.Integer") {
                true
            } else {
                clz.isAssignableFrom(targetClz)
            }
            if (!equals) {
                match = false
                break
            }
        }
        return match
    }


}
