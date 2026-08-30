package com.cosmicindustries.umbra.firewall

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    NOT_RUNNING,
    PERMISSION_DENIED,
    PERMISSION_GRANTED,
}

/**
 * Tracks whether the separate Shizuku app/service is running and whether
 * Umbra has been granted permission to use it. Umbra never bundles or
 * requires root: users who don't run Shizuku simply don't get the
 * hard-block firewall path (VPN-level app inclusion/exclusion still works
 * without it). See BUILDING.md for on-device Shizuku setup.
 */
class ShizukuPermissionManager {
    private val _status = MutableStateFlow(currentStatus())
    val status: StateFlow<ShizukuStatus> = _status

    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun requestPermission(requestCode: Int = REQUEST_CODE) {
        if (Shizuku.isPreV11()) return
        Shizuku.requestPermission(requestCode)
    }

    fun refresh() {
        _status.value = currentStatus()
    }

    private fun currentStatus(): ShizukuStatus {
        if (!Shizuku.pingBinder()) return ShizukuStatus.NOT_RUNNING
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        return if (granted) ShizukuStatus.PERMISSION_GRANTED else ShizukuStatus.PERMISSION_DENIED
    }

    companion object {
        const val REQUEST_CODE = 5100
    }
}
