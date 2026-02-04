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
    
    private val browserList = mutableListOf<Pair<String, String>>() // <名称, 包名>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)
            
            // 初始化 ViewPager 和 TabLayout
            viewPager = findViewById(R.id.viewPager)
            tabLayout = findViewById(R.id.tabLayout)
            
            adapter = ViewPagerAdapter(this)
            viewPager.adapter = adapter
            
            // 关联 TabLayout 和 ViewPager
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "配置"
                    1 -> "日志"
                    else -> "未知"
                }
            }.attach()
            
            // 扫描系统浏览器
            try {
                scanInstalledBrowsers()
            } catch (e: Exception) {
                // 忽略浏览器扫描错误
            }
            
            // 启动日志
            viewPager.postDelayed({
                try {
                    addLog("[启动] 应用已启动，请点击“重新探测”开始扫描")
                } catch (e: Exception) {
                    // 忽略日志错误
                }
            }, 500)
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }
    
    /**
     * 扫描系统已安装的浏览器
     */
    private fun scanInstalledBrowsers() {
        browserList.clear()
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            val appName = activityInfo.loadLabel(packageManager).toString()
            val packageName = activityInfo.packageName
            
            browserList.add(Pair(appName, packageName))
        }
        
        addLog("[浏览器] 已扫描到 ${browserList.size} 个浏览器")
        browserList.forEach {
            addLog("[浏览器] ${it.first} (${it.second})")
        }
    }
    
    /**
     * 获取已安装的浏览器列表
     */
    fun getInstalledBrowsers(): List<Pair<String, String>> {
        return browserList
    }
    
    /**
     * 开始扫描
     */
    fun startScanning() {
        if (isScanning) {
            addLog("[警告] 扫描已在进行中...")
            return
        }
        
        // 检查 Fragment 是否已初始化
        if (!adapter.configFragment.isAdded || !adapter.logFragment.isAdded) {
            addLog("[错误] Fragment 未初始化，请稍后重试")
            return
        }
        
        // 切换到日志页
        viewPager.currentItem = 1
        
        // 清空日志
        adapter.logFragment.clearLog()
        
        isScanning = true
        foundTarget = false
        
        // 获取配置
        val baseX = adapter.configFragment.getBaseX()
        val baseY = adapter.configFragment.getBaseY()
        val yUp = adapter.configFragment.getYUp()
        val yDown = adapter.configFragment.getYDown()
        val yStep = adapter.configFragment.getYStep()
        val xUp = adapter.configFragment.getXUp()
        val xDown = adapter.configFragment.getXDown()
        val xStep = adapter.configFragment.getXStep()
        val port = adapter.configFragment.getPort()
        val path = adapter.configFragment.getPath()
        val concurrency = adapter.configFragment.getConcurrency()
        val delay = adapter.configFragment.getDelay()
        val browserName = adapter.configFragment.getBrowserName()
        
        addLog("[配置] 基准点: 192.168.$baseX.$baseY")
        addLog("[配置] Y轴: 上$yUp 下$yDown 步长$yStep")
        addLog("[配置] X轴: 上$xUp 下$xDown 步长$xStep")
        addLog("[配置] 端口: $port, 路径: $path")
        addLog("[配置] 并发数: $concurrency, 延迟: ${delay}s")
        addLog("[配置] 浏览器: $browserName")
        
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            // 快车道：立即探测基准点
            addLog("[快车道] 探测基准点: 192.168.$baseX.$baseY:$port")
            if (checkPort("192.168.$baseX.$baseY", port)) {
                addLog("[快车道] ✓ 基准点可达！")
                withContext(Dispatchers.Main) {
                    onTargetFound("192.168.$baseX.$baseY", port, path, delay, browserName)
                }
                return@launch
            } else {
                addLog("[快车道] ✗ 基准点不可达，启动全面扫描...")
            }
            
            // 同时启动主动扫描和被动抓包
            val activeJob = launch { startActiveScan(baseX, baseY, xUp, xDown, xStep, yUp, yDown, yStep, port, path, concurrency, delay, browserName) }
            val passiveJob = launch { startPassiveScan(port, path, delay, browserName) }
            
            // 等待任意一个完成
            activeJob.join()
            passiveJob.cancelAndJoin()
        }
    }
    
    /**
     * 主动扫描
     */
    private suspend fun startActiveScan(
        baseX: Int, baseY: Int,
        xUp: Int, xDown: Int, xStep: Int,
        yUp: Int, yDown: Int, yStep: Int,
        port: Int, path: String,
        concurrency: Int, delay: Int, browserName: String
    ) {
        addLog("[主动扫描] 开始基准点扩散扫描...")
        addLog("[主动扫描] 基准点: 192.168.$baseX.$baseY")
        addLog("[主动扫描] Y轴范围: [${baseY - yDown}..${baseY + yUp}], 步长: $yStep")
        addLog("[主动扫描] X轴范围: [${baseX - xDown}..${baseX + xUp}], 步长: $xStep")
        addLog("[主动扫描] 并发数: $concurrency")
        
        val targets = ArrayList<String>()
        
        // 生成所有目标 IP
        for (y in (baseY - yDown)..(baseY + yUp) step yStep) {
            for (x in (baseX - xDown)..(baseX + xUp) step xStep) {
                if (x in 0..255 && y in 0..255) {
                    targets.add("192.168.$x.$y")
                }
            }
        }
        
        addLog("[主动扫描] 共生成 ${targets.size} 个目标地址")
        
        // 使用信号量控制并发数
        val semaphore = Semaphore(concurrency)
        val jobs = targets.map { ip ->
            CoroutineScope(Dispatchers.IO).async {
                semaphore.acquire()
                try {
                    if (!isScanning || foundTarget) return@async
                    
                    if (checkPort(ip, port)) {
                        addLog("[主动扫描] ✓ 发现目标: $ip:$port")
                        if (!foundTarget) {
                            foundTarget = true
                            withContext(Dispatchers.Main) {
                                onTargetFound(ip, port, path, delay, browserName)
                            }
                        }
                    }
                } finally {
                    semaphore.release()
                }
            }
        }
        
        jobs.awaitAll()
        
        if (!foundTarget && isScanning) {
            addLog("[主动扫描] 扫描完成，未发现目标")
        }
    }
    
    /**
     * 被动抓包
     */
    private suspend fun startPassiveScan(port: Int, path: String, delay: Int, browserName: String) {
        addLog("[被动抓包] 启动 UDP 监听...")
        
        val checkedIps = mutableSetOf<String>()
        
        withContext(Dispatchers.IO) {
            try {
                val socket = DatagramSocket(null)
                socket.reuseAddress = true
                socket.broadcast = true
                socket.soTimeout = 1000
                
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                
                while (isScanning && !foundTarget) {
                    try {
                        socket.receive(packet)
                        val sourceIp = packet.address.hostAddress ?: continue
                        
                        // 只处理局域网 IP
                        if (sourceIp.startsWith("192.168.") && !checkedIps.contains(sourceIp)) {
                            checkedIps.add(sourceIp)
                            addLog("[被动抓包] 截获新 IP: $sourceIp，正在验证...")
                            
                            if (checkPort(sourceIp, port)) {
                                addLog("[被动抓包] ✓ 发现目标: $sourceIp:$port")
                                if (!foundTarget) {
                                    foundTarget = true
                                    withContext(Dispatchers.Main) {
                                        onTargetFound(sourceIp, port, path, delay, browserName)
                                    }
                                }
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // 超时或其他错误，继续监听
                    }
                }
                
                socket.close()
            } catch (e: Exception) {
                addLog("[被动抓包] 错误: ${e.message}")
            }
        }
    }
    
    /**
     * 检查端口是否开放
     */
    private suspend fun checkPort(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 300)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 发现目标后的处理
     */
    private fun onTargetFound(ip: String, port: Int, path: String, delay: Int, browserName: String) {
        isScanning = false
        scanJob?.cancel()
        
        addLog("[成功] 目标已锁定: $ip:$port")
        addLog("[跳转] 延迟 ${delay}s 后跳转...")
        
        val url = "http://$ip:$port$path"
        
        // 延迟跳转
        CoroutineScope(Dispatchers.Main).launch {
            delay(delay * 1000L)
            
            addLog("[跳转] 目标 URL: $url")
            
            // 查找浏览器包名
            val browserPackage = browserList.find { it.first == browserName }?.second
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            
            if (browserPackage != null) {
                intent.setPackage(browserPackage)
                addLog("[跳转] 使用浏览器: $browserName ($browserPackage)")
            } else {
                addLog("[跳转] 使用系统默认浏览器")
            }
            
            try {
                startActivity(intent)
                addLog("[跳转] 已唤起浏览器")
            } catch (e: Exception) {
                addLog("[跳转] 失败: ${e.message}")
                // 降级处理：使用系统默认浏览器
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    startActivity(fallbackIntent)
                    addLog("[跳转] 已使用系统默认浏览器")
                } catch (e2: Exception) {
                    addLog("[跳转] 完全失败: ${e2.message}")
                }
            }
        }
    }
    
    /**
     * 停止扫描
     */
    fun stopScanning() {
        if (!isScanning) {
            addLog("[警告] 当前没有正在进行的扫描")
            return
        }
        
        isScanning = false
        foundTarget = false
        scanJob?.cancel()
        addLog("[停止] 用户手动中断")
    }
    
    /**
     * 添加日志
     */
    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message"
        
        runOnUiThread {
            adapter.logFragment.addLog(logMessage)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
    }
}
