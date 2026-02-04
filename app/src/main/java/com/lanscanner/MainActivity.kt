package com.lanscanner

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: ViewPagerAdapter
    
    private var scanJob: Job? = null
    private var isScanning = false
    private var foundTarget = false
    private var discoverySource = "未知" 
    
    private val browserList = mutableListOf<Pair<String, String>>() // <名称, 包名>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)
            
            viewPager = findViewById(R.id.viewPager)
            tabLayout = findViewById(R.id.tabLayout)
            
            adapter = ViewPagerAdapter(this)
            viewPager.adapter = adapter
            
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "配置"
                    1 -> "日志"
                    else -> "未知"
                }
            }.attach()
            
            try {
                scanInstalledBrowsers()
            } catch (e: Exception) {}
            
            // 自动启动检测逻辑：循环检查直到 Fragment 加载完成
            val autoStartRunnable = object : Runnable {
                override fun run() {
                    if (adapter.configFragment.isAdded && adapter.configFragment.view != null) {
                        addLog("[自动] 环境就绪，开始自动探测...")
                        startScanning()
                    } else {
                        viewPager.postDelayed(this, 500)
                    }
                }
            }
            viewPager.postDelayed(autoStartRunnable, 1000)

        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }
    
    private fun scanInstalledBrowsers() {
        browserList.clear()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            browserList.add(Pair(activityInfo.loadLabel(packageManager).toString(), activityInfo.packageName))
        }
    }
    
    fun getInstalledBrowsers(): List<Pair<String, String>> = browserList
    
    fun startScanning() {
        if (isScanning) return
        if (!adapter.configFragment.isAdded || !adapter.logFragment.isAdded) return
        
        isScanning = true
        foundTarget = false
        discoverySource = "未知"
        adapter.logFragment.clearLog()
        
        val baseX = adapter.configFragment.getBaseX()
        val baseY = adapter.configFragment.getBaseY()
        val port = adapter.configFragment.getPort()
        val path = adapter.configFragment.getPath()
        val delay = adapter.configFragment.getDelay()
        val browserName = adapter.configFragment.getBrowserName()
        
        addLog("[扫描] 任务启动...")
        
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            if (checkPort("192.168.$baseX.$baseY", port)) {
                discoverySource = "快车道直连"
                withContext(Dispatchers.Main) {
                    onTargetFound("192.168.$baseX.$baseY", port, path, delay, browserName)
                }
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                viewPager.currentItem = 1 
                addLog("[提示] 基准点不可达，正在扩散扫描...")
            }
            
            val yUp = adapter.configFragment.getYUp()
            val yDown = adapter.configFragment.getYDown()
            val yStep = adapter.configFragment.getYStep()
            val xUp = adapter.configFragment.getXUp()
            val xDown = adapter.configFragment.getXDown()
            val xStep = adapter.configFragment.getXStep()
            val concurrency = adapter.configFragment.getConcurrency()

            val activeJob = launch { startActiveScan(baseX, baseY, xUp, xDown, xStep, yUp, yDown, yStep, port, path, concurrency, delay, browserName) }
            val passiveJob = launch { startPassiveScan(port, path, delay, browserName) }
            
            activeJob.join()
            passiveJob.cancelAndJoin()
        }
    }
    
    private suspend fun startActiveScan(baseX: Int, baseY: Int, xUp: Int, xDown: Int, xStep: Int, yUp: Int, yDown: Int, yStep: Int, port: Int, path: String, concurrency: Int, delay: Int, browserName: String) {
        val targets = ArrayList<String>()
        for (y in (baseY - yDown)..(baseY + yUp) step yStep) {
            for (x in (baseX - xDown)..(baseX + xUp) step xStep) {
                if (x in 0..255 && y in 0..255) targets.add("192.168.$x.$y")
            }
        }
        val semaphore = Semaphore(concurrency)
        val jobs = targets.map { ip ->
            CoroutineScope(Dispatchers.IO).async {
                semaphore.acquire()
                try {
                    if (!isScanning || foundTarget) return@async
                    if (checkPort(ip, port)) {
                        if (!foundTarget) {
                            foundTarget = true
                            discoverySource = "主动扫描"
                            withContext(Dispatchers.Main) { onTargetFound(ip, port, path, delay, browserName) }
                        }
                    }
                } finally { semaphore.release() }
            }
        }
        jobs.awaitAll()
    }
    
    private suspend fun startPassiveScan(port: Int, path: String, delay: Int, browserName: String) {
        val checkedIps = mutableSetOf<String>()
        withContext(Dispatchers.IO) {
            try {
                val socket = DatagramSocket(null).apply { reuseAddress = true; broadcast = true; soTimeout = 1000 }
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                while (isScanning && !foundTarget) {
                    try {
                        socket.receive(packet)
                        val sourceIp = packet.address.hostAddress ?: continue
                        if (sourceIp.startsWith("192.168.") && !checkedIps.contains(sourceIp)) {
                            checkedIps.add(sourceIp)
                            if (checkPort(sourceIp, port)) {
                                if (!foundTarget) {
                                    foundTarget = true
                                    discoverySource = "被动抓包"
                                    withContext(Dispatchers.Main) { onTargetFound(sourceIp, port, path, delay, browserName) }
                                }
                                break
                            }
                        }
                    } catch (e: Exception) {}
                }
                socket.close()
            } catch (e: Exception) {}
        }
    }
    
    private suspend fun checkPort(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 300)
            socket.close()
            true
        } catch (e: Exception) { false }
    }
    
    private fun onTargetFound(ip: String, port: Int, path: String, delay: Int, browserName: String) {
        isScanning = false
        scanJob?.cancel()
        
        val ipParts = ip.split(".")
        if (ipParts.size == 4) {
            val newX = ipParts[2].toInt()
            val newY = ipParts[3].toInt()
            addLog("[成功] 发现目标: $ip (方式: $discoverySource)")
            addLog("[更新] 自动更新基准点为: 192.168.$newX.$newY")
            adapter.configFragment.updateBaseAndSave(newX, newY) 
        }

        val url = "http://$ip:$port$path"
        
        CoroutineScope(Dispatchers.Main).launch {
            addLog("[跳转] 延迟 ${delay}s 后跳转...")
            delay(delay * 1000L)
            
            val browserPackage = when {
                browserName.contains("EDGE", true) -> browserList.find { it.first.contains("Edge", true) || it.second.contains("microsoft.emmx", true) }?.second
                browserName.contains("荣耀", true) || browserName.contains("HONOR", true) -> browserList.find { it.first.contains("荣耀", true) || it.second.contains("hihonor", true) || it.second.contains("huawei", true) }?.second
                else -> browserList.find { it.first == browserName }?.second
            }
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserPackage?.let { intent.setPackage(it) }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }
    
    fun stopScanning() { isScanning = false; scanJob?.cancel(); addLog("[停止] 手动中断") }
    private fun addLog(message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        runOnUiThread { adapter.logFragment.addLog("[$ts] $message") }
    }
    override fun onDestroy() { super.onDestroy(); stopScanning() }
}