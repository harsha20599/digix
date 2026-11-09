package com.littyl.digix.engine

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class Torch(context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    private val cameraId: String? by lazy {
        runCatching {
            camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    val isAvailable: Boolean get() = hasFlash && cameraId != null

    /** Android only reports torch state through a callback, which answers as soon as it is registered. */
    suspend fun isOn(): Boolean {
        val id = cameraId ?: return false
        val result = CompletableDeferred<Boolean>()
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(changedId: String, enabled: Boolean) {
                if (changedId == id) result.complete(enabled)
            }

            override fun onTorchModeUnavailable(changedId: String) {
                if (changedId == id) result.complete(false)
            }
        }
        camera.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        return try {
            withTimeoutOrNull(STATE_TIMEOUT_MS) { result.await() } ?: false
        } finally {
            camera.unregisterTorchCallback(callback)
        }
    }

    fun set(on: Boolean): Boolean {
        val id = cameraId ?: return false
        return try {
            camera.setTorchMode(id, on)
            true
        } catch (_: CameraAccessException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 500L
    }
}
