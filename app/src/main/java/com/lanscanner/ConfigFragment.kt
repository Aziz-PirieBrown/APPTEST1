package com.lanscanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class ConfigFragment : Fragment() {
    
    private lateinit var configManager: ConfigManager
    private lateinit var mainActivity: MainActivity
    
    // 输入框引用
    private lateinit var etBaseX: MaterialAutoCompleteTextView
    private lateinit var etBaseY: MaterialAutoCompleteTextView
    private lateinit var etYUp: MaterialAutoCompleteTextView
    private lateinit var etYDown: MaterialAutoCompleteTextView
    private lateinit var etYStep: MaterialAutoCompleteTextView
    private lateinit var etXUp: MaterialAutoCompleteTextView
    private lateinit var etXDown: MaterialAutoCompleteTextView
    private lateinit var etXStep: MaterialAutoCompleteTextView
    private lateinit var etPort: MaterialAutoCompleteTextView
    private lateinit var etPath: MaterialAutoCompleteTextView
    private lateinit var etConcurrency: MaterialAutoCompleteTextView
    private lateinit var etDelay: MaterialAutoCompleteTextView
    private lateinit var etBrowser: MaterialAutoCompleteTextView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_config, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        mainActivity = activity as MainActivity
        configManager = ConfigManager(requireContext())
        
        // 初始化所有输入框
        etBaseX = view.findViewById(R.id.etBaseX)
        etBaseY = view.findViewById(R.id.etBaseY)
        etYUp = view.findViewById(R.id.etYUp)
        etYDown = view.findViewById(R.id.etYDown)
        etYStep = view.findViewById(R.id.etYStep)
        etXUp = view.findViewById(R.id.etXUp)
        etXDown = view.findViewById(R.id.etXDown)
        etXStep = view.findViewById(R.id.etXStep)
        etPort = view.findViewById(R.id.etPort)
        etPath = view.findViewById(R.id.etPath)
        etConcurrency = view.findViewById(R.id.etConcurrency)
        etDelay = view.findViewById(R.id.etDelay)
        etBrowser = view.findViewById(R.id.etBrowser)
        
        // 设置路径预设值
        val pathOptions = listOf("/board", "/wewewe")
        etPath.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, pathOptions))
        
        // 设置并发数预设值
        val concurrencyOptions = listOf("80", "100", "120")
        etConcurrency.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, concurrencyOptions))
        
        // 设置延迟预设值
        val delayOptions = listOf("0", "1", "2", "3", "5")
        etDelay.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, delayOptions))
        
        // 设置所有输入框的历史记忆
        setupAutoComplete(etBaseX, "base_x")
        setupAutoComplete(etBaseY, "base_y")
        setupAutoComplete(etYUp, "y_up")
        setupAutoComplete(etYDown, "y_down")
        setupAutoComplete(etYStep, "y_step")
        setupAutoComplete(etXUp, "x_up")
        setupAutoComplete(etXDown, "x_down")
        setupAutoComplete(etXStep, "x_step")
        setupAutoComplete(etPort, "port")
        setupAutoComplete(etPath, "path")
        setupAutoComplete(etConcurrency, "concurrency")
        setupAutoComplete(etDelay, "delay")
        setupAutoComplete(etBrowser, "browser")
        
        // 加载浏览器列表
        loadBrowserList()
        
        // 设置按钮点击事件
        view.findViewById<View>(R.id.btnStart).setOnClickListener {
            mainActivity.startScanning()
        }
        
        view.findViewById<View>(R.id.btnStop).setOnClickListener {
            mainActivity.stopScanning()
        }
    }
    
    private fun setupAutoComplete(editText: MaterialAutoCompleteTextView, key: String) {
        // 加载历史记录
        val history = configManager.getHistory(key)
        editText.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, history))
        
        // 失去焦点时保存
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    configManager.saveValue(key, text)
                    // 更新历史记录
                    val updatedHistory = configManager.getHistory(key)
                    editText.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, updatedHistory))
                } else {
                    configManager.clearValue(key)
                }
            }
        }
    }
    
    private fun loadBrowserList() {
        val browsers = mainActivity.getInstalledBrowsers()
        etBrowser.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, browsers.map { it.first }))
        
        // 默认选中荣耀浏览器
        val honorBrowser = browsers.find { it.second == "com.hihonor.browser" }
        if (honorBrowser != null) {
            etBrowser.setText(honorBrowser.first, false)
        }
    }
    
    // 获取配置值的方法
    fun getBaseX(): Int = if (::etBaseX.isInitialized) etBaseX.text.toString().toIntOrNull() ?: 130 else 130
    fun getBaseY(): Int = if (::etBaseY.isInitialized) etBaseY.text.toString().toIntOrNull() ?: 188 else 188
    fun getYUp(): Int = if (::etYUp.isInitialized) etYUp.text.toString().toIntOrNull() ?: 20 else 20
    fun getYDown(): Int = if (::etYDown.isInitialized) etYDown.text.toString().toIntOrNull() ?: 10 else 10
    fun getYStep(): Int = if (::etYStep.isInitialized) etYStep.text.toString().toIntOrNull() ?: 1 else 1
    fun getXUp(): Int = if (::etXUp.isInitialized) etXUp.text.toString().toIntOrNull() ?: 20 else 20
    fun getXDown(): Int = if (::etXDown.isInitialized) etXDown.text.toString().toIntOrNull() ?: 10 else 10
    fun getXStep(): Int = if (::etXStep.isInitialized) etXStep.text.toString().toIntOrNull() ?: 1 else 1
    fun getPort(): Int = if (::etPort.isInitialized) etPort.text.toString().toIntOrNull() ?: 34832 else 34832
    fun getPath(): String = if (::etPath.isInitialized) etPath.text.toString().ifEmpty { "/board" } else "/board"
    fun getConcurrency(): Int = if (::etConcurrency.isInitialized) etConcurrency.text.toString().toIntOrNull() ?: 120 else 120
    fun getDelay(): Int = if (::etDelay.isInitialized) etDelay.text.toString().toIntOrNull() ?: 0 else 0
    fun getBrowserName(): String = if (::etBrowser.isInitialized) etBrowser.text.toString().ifEmpty { "荣耀浏览器" } else "荣耀浏览器"
}
