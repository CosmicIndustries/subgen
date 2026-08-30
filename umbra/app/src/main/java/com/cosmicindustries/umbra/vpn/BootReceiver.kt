package com.cosmicindustries.umbra.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cosmicindustries.umbra.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restarts the DPI-bypass tunnel after reboot, if the user had it enabled and asked for that. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(appContext)
                if (settings.startOnBoot()) {
                    val serviceIntent = Intent(appContext, UmbraVpnService::class.java)
                        .setAction(UmbraVpnService.ACTION_START)
                    ContextCompat.startForegroundService(appContext, serviceIntent)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
