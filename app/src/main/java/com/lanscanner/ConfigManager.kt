package com.lanscanner

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("lan_scanner_config", Context.MODE_PRIVATE)
    
    companion object {
        // 配置键
        const val KEY_IP_X = "ip_x"
        const val KEY_IP_Y = "ip_y"
        const val KEY_Y_UP = "y_up"
        const val KEY_Y_DOWN = "y_down"
        const val KEY_Y_STEP = "y_step"
        const val KEY_X_UP = "x_up"
        const val KEY_X_DOWN = "x_down"
        const val KEY_X_STEP = "x_step"
        const val KEY_PORT = "port"
        const val KEY_PATH = "path"
        const val KEY_CONCURRENCY = "concurrency"
        const val KEY_DELAY = "delay"
        const val KEY_BROWSER = "browser"
        
        // 历史记录键（每个字段最多5条）
        const val HISTORY_SUFFIX = "_history"
        const val MAX_HISTORY = 5
    }
    
    // 保存配置值
    fun saveValue(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        
        // 同时保存到历史记录
        val historyKey = key + HISTORY_SUFFIX
        val history = getHistory(key).toMutableList()
        
        // 如果值已存在，先移除
        history.remove(value)
        // 添加到开头
        history.add(0, value)
        // 限制最多5条
        if (history.size > MAX_HISTORY) {
            history.removeAt(history.size - 1)
        }
        
        // 保存历史记录（用逗号分隔）
        prefs.edit().putString(historyKey, history.joinToString(",")).apply()
    }
    
    // 获取配置值
    fun getValue(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }
    
    // 获取历史记录
    fun getHistory(key: String): List<String> {
        val historyKey = key + HISTORY_SUFFIX
        val historyStr = prefs.getString(historyKey, "") ?: ""
        return if (historyStr.isEmpty()) {
            emptyList()
        } else {
            historyStr.split(",").filter { it.isNotEmpty() }
        }
    }
    
    // 清空某个字段的值和历史记录
    fun clearValue(key: String) {
        prefs.edit().remove(key).apply()
        prefs.edit().remove(key + HISTORY_SUFFIX).apply()
    }
    
    // 获取所有配置（用于启动时加载）
    fun getAllConfig(): Map<String, String> {
        return mapOf(
            KEY_IP_X to getValue(KEY_IP_X, "130"),
            KEY_IP_Y to getValue(KEY_IP_Y, "188"),
            KEY_Y_UP to getValue(KEY_Y_UP, "20"),
            KEY_Y_DOWN to getValue(KEY_Y_DOWN, "10"),
            KEY_Y_STEP to getValue(KEY_Y_STEP, "1"),
            KEY_X_UP to getValue(KEY_X_UP, "20"),
            KEY_X_DOWN to getValue(KEY_X_DOWN, "10"),
            KEY_X_STEP to getValue(KEY_X_STEP, "1"),
            KEY_PORT to getValue(KEY_PORT, "34832"),
            KEY_PATH to getValue(KEY_PATH, "/board"),
            KEY_CONCURRENCY to getValue(KEY_CONCURRENCY, "120"),
            KEY_DELAY to getValue(KEY_DELAY, "0s"),
            KEY_BROWSER to getValue(KEY_BROWSER, "荣耀浏览器")
        )
    }
}
