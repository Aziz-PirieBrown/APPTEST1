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
        
        // 1. 初始化所有输入框引用
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
        
        // 2. 设置下拉预设选项与点击弹出逻辑
        setupPresetOptions()
        
        // 3. 设置所有输入框的历史记忆与自动保存
        setupAllAutoComplete()
        
        // 4. 加载浏览器列表
        loadBrowserList()
        
        // 5. 设置按钮点击事件
        view.findViewById<View>(R.id.btnStart).setOnClickListener {
            mainActivity.startScanning()
        }
        
        view.findViewById<View>(R.id.btnStop).setOnClickListener {
            mainActivity.stopScanning()
        }
    }

    /**
     * 设置路径、并发、延迟等预设下拉内容
     */
    private fun setupPresetOptions() {
        // 路径设置
        val pathOptions = listOf("/board", "/wewewe")
        etPath.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, pathOptions))
        etPath.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etPath.showDropDown() }
        etPath.setOnClickListener { etPath.showDropDown() }

        // 并发数设置
        val concurrencyOptions = listOf("80", "100", "120", "150")
        etConcurrency.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, concurrencyOptions))
        etConcurrency.setOnClickListener { etConcurrency.showDropDown() }

        // 延迟设置
        val delayOptions = listOf("0", "1", "2", "3", "5", "10")
        etDelay.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, delayOptions))
        etDelay.setOnClickListener { etDelay.showDropDown() }
    }

    /**
     * 批量绑定历史记录
     */
    private fun setupAllAutoComplete() {
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
    }
    
    private fun setupAutoComplete(editText: MaterialAutoCompleteTextView, key: String) {
        val history = configManager.getHistory(key)
        editText.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, history))
        
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    configManager.saveValue(key, text)
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
        
        val edgeKeywords = listOf("com.microsoft.emmx", "edge")
        val honorKeywords = listOf("com.hihonor.browser", "com.huawei.browser", "荣耀浏览器")

        val edgeBrowser = browsers.find { b -> 
            edgeKeywords.any { key -> b.second.lowercase().contains(key) || b.first.lowercase().contains(key) }
        }
        val honorBrowser = browsers.find { b -> 
            honorKeywords.any { key -> b.second.lowercase().contains(key) || b.first.lowercase().contains(key) }
        }

        // 自动选中逻辑：优先荣耀，次选Edge
        if (honorBrowser != null) {
            etBrowser.setText(honorBrowser.first, false)
        } else if (edgeBrowser != null) {
            etBrowser.setText(edgeBrowser.first, false)
        } 

        etBrowser.setOnClickListener { etBrowser.showDropDown() }
    }

    /**
     * 新增：供 MainActivity 发现目标后调用，更新基准点并保存
     */
    fun updateBaseAndSave(newX: Int, newY: Int) {
        if (::etBaseX.isInitialized && ::etBaseY.isInitialized) {
            val xStr = newX.toString()
            val yStr = newY.toString()
            
            // 1. 更新 UI 显示
            etBaseX.setText(xStr, false)
            etBaseY.setText(yStr, false)
            
            // 2. 强制保存到 ConfigManager 的历史记录中，确保下次启动加载
            configManager.saveValue("base_x", xStr)
            configManager.saveValue("base_y", yStr)
            
            // 3. 刷新适配器以显示最新值
            setupAutoComplete(etBaseX, "base_x")
            setupAutoComplete(etBaseY, "base_y")
        }
    }
    
    // --- 以下为获取配置值的方法 ---
    fun getBaseX(): Int = etBaseX.text.toString().toIntOrNull() ?: 130
    fun getBaseY(): Int = etBaseY.text.toString().toIntOrNull() ?: 188
    fun getYUp(): Int = etYUp.text.toString().toIntOrNull() ?: 20
    fun getYDown(): Int = etYDown.text.toString().toIntOrNull() ?: 10
    fun getYStep(): Int = etYStep.text.toString().toIntOrNull() ?: 1
    fun getXUp(): Int = etXUp.text.toString().toIntOrNull() ?: 20
    fun getXDown(): Int = etXDown.text.toString().toIntOrNull() ?: 10
    fun getXStep(): Int = etXStep.text.toString().toIntOrNull() ?: 1
    fun getPort(): Int = etPort.text.toString().toIntOrNull() ?: 34832
    fun getPath(): String = etPath.text.toString().ifEmpty { "/board" }
    fun getConcurrency(): Int = etConcurrency.text.toString().toIntOrNull() ?: 120
    fun getDelay(): Int = etDelay.text.toString().toIntOrNull() ?: 0
    fun getBrowserName(): String = etBrowser.text.toString().ifEmpty { "荣耀浏览器" }
}