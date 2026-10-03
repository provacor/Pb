package com.provacor.sathi.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.provacor.sathi.agent.DeviceControls
import com.provacor.sathi.core.model.DeviceAction
import com.provacor.sathi.core.model.DeviceTarget

/**
 * Flashlight and volume through their public APIs. Wi-Fi, Bluetooth, data and
 * location can't be switched by apps on modern Android, so their system panel
 * or settings page is opened for the user.
 */
class AndroidDeviceControls(private val context: Context) : DeviceControls {

    override fun torch(on: Boolean): Boolean = try {
        val cm = context.getSystemService(CameraManager::class.java) ?: return false
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (id == null) false else { cm.setTorchMode(id, on); true }
    } catch (e: Exception) {
        Log.w(TAG, "Torch failed", e)
        false
    }

    override fun volume(action: DeviceAction): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val direction = when (action) {
            DeviceAction.UP -> AudioManager.ADJUST_RAISE
            DeviceAction.DOWN -> AudioManager.ADJUST_LOWER
            DeviceAction.MUTE, DeviceAction.OFF -> AudioManager.ADJUST_MUTE
            DeviceAction.ON, DeviceAction.OPEN -> AudioManager.ADJUST_UNMUTE
        }
        return try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: SecurityException) {
            // Muting can be refused while Do Not Disturb is on.
            Log.w(TAG, "Volume change refused", e)
            false
        }
    }

    override fun openPanel(target: DeviceTarget): Boolean {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val action = when (target) {
            DeviceTarget.WIFI -> if (q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            DeviceTarget.MOBILE_DATA -> if (q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
            DeviceTarget.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            DeviceTarget.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            DeviceTarget.BRIGHTNESS -> Settings.ACTION_DISPLAY_SETTINGS
            DeviceTarget.FLASHLIGHT, DeviceTarget.VOLUME -> if (q) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val TAG = "DeviceControls"
    }
}
