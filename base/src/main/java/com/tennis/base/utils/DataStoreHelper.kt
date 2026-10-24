package com.tennis.base.utils

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.text.get

val Context.dataStore by preferencesDataStore(name = "fingertip")

object DataStoreHelper {

    private const val DATASTORE_NAME = "app_preferences"

    // 保存字符串
    suspend fun putString(context: Context, key: String, value: String) {
        val dataKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[dataKey] = value
        }
    }

    // 读取字符串
    fun getString(context: Context, key: String, default: String = ""): Flow<String> {
        val dataKey = stringPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[dataKey] ?: default
        }
    }

    // 同步读取字符串
    fun getStringSync(context: Context, key: String, default: String = ""): String = runBlocking {
        val dataKey = stringPreferencesKey(key)
        val preferences = context.dataStore.data.first()
        preferences[dataKey] ?: default
    }

    // 保存布尔值
    suspend fun putBoolean(context: Context, key: String, value: Boolean) {
        val dataKey = booleanPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[dataKey] = value
        }
    }

    // 读取布尔值
    fun getBoolean(context: Context, key: String, default: Boolean = false): Flow<Boolean> {
        val dataKey = booleanPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[dataKey] ?: default
        }
    }

    // 同步读取布尔值
    fun getBooleanSync(context: Context, key: String, default: Boolean = false): Boolean = runBlocking {
        val dataKey = booleanPreferencesKey(key)
        val preferences = context.dataStore.data.first()
        preferences[dataKey] ?: default
    }


    // 保存整数
    suspend fun putInt(context: Context, key: String, value: Int) {
        val dataKey = intPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[dataKey] = value
        }
    }

    // 读取整数
    fun getInt(context: Context, key: String, default: Int = 0): Flow<Int> {
        val dataKey = intPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[dataKey] ?: default
        }
    }

    // 同步读取整数
    fun getIntSync(context: Context, key: String, default: Int = 0): Int = runBlocking {
        val dataKey = intPreferencesKey(key)
        val preferences = context.dataStore.data.first()
        preferences[dataKey] ?: default
    }

    // 清除所有数据
    suspend fun clearAll(context: Context) {
        context.dataStore.edit { it.clear() }
    }

    suspend fun clearString(context: Context,key : Preferences.Key<String>){
        context.dataStore.edit {
            it.remove(key)
        }
    }
}