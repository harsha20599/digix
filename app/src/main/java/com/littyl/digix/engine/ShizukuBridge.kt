package com.littyl.digix.engine

import android.content.Context
import android.content.pm.PackageManager
import moe.shizuku.server.IShizukuService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import com.littyl.digix.shell.HotspotTool

/** Optional. People who already run Shizuku get faster and more reliable phone controls. */
object ShizukuBridge {
    private const val REQUEST_CODE = 41

    fun isInstalledAndRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isReady(): Boolean = isInstalledAndRunning() && hasPermission()

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    suspend fun setWifi(on: Boolean): Boolean = shell("svc wifi ${if (on) "enable" else "disable"}")

    suspend fun setHotspot(context: Context, on: Boolean): Boolean {
        val apk = context.applicationInfo.sourceDir
        val tool = HotspotTool::class.java.name
        return shell("CLASSPATH='$apk' app_process / $tool ${if (on) "start" else "stop"}")
    }

    /** Whether a Wi-Fi hotspot is up, or null when Shizuku cannot answer. */
    suspend fun isTethering(): Boolean? {
        if (!isReady()) return null
        return shell("dumpsys tethering | grep -E '^ *(wlan|swlan|softap|ap)[a-z_0-9]* - TetheredState'")
    }

    private suspend fun shell(command: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            process.waitFor() == 0
        }.getOrDefault(false)
    }
}
