package com.lanscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class LogFragment : Fragment() {
    
    private lateinit var tvLog: TextView
    private lateinit var scrollView: NestedScrollView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_log, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        tvLog = view.findViewById(R.id.tvLog)
        scrollView = view.findViewById(R.id.scrollView)
        
        val fabCopy = view.findViewById<FloatingActionButton>(R.id.fabCopy)
        val fabClear = view.findViewById<FloatingActionButton>(R.id.fabClear)
        
        // 一键复制
        fabCopy.setOnClickListener {
            val logText = tvLog.text.toString()
            if (logText.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("日志", logText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "日志为空", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 清空日志
        fabClear.setOnClickListener {
            clearLog()
            Toast.makeText(requireContext(), "日志已清空", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun addLog(message: String) {
        if (!isAdded) return
        
        activity?.runOnUiThread {
            val currentText = tvLog.text.toString()
            tvLog.text = if (currentText.isEmpty()) {
                message
            } else {
                "$currentText\n$message"
            }
            
            // 自动滚动到底部
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
    
    fun clearLog() {
        if (!isAdded) return
        
        activity?.runOnUiThread {
            tvLog.text = "[日志] 日志已清空，等待开始探测..."
        }
    }
}
