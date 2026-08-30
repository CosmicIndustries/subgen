package com.cosmicindustries.umbra.ui

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.cosmicindustries.umbra.UmbraApp
import com.cosmicindustries.umbra.ui.theme.UmbraTheme
import rikka.shizuku.Shizuku

/**
 * Hosts the VPN-consent flow (`VpnService.prepare()` can only be launched
 * from an Activity) and forwards it to whichever ViewModel action actually
 * starts a tunnel; the Dashboard composables never touch VpnService.prepare()
 * directly.
 */
class MainActivity : ComponentActivity() {

    private lateinit var app: UmbraApp
    private var pendingAfterConsent: (() -> Unit)? = null

    private val vpnConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = pendingAfterConsent
        pendingAfterConsent = null
        if (result.resultCode == RESULT_OK) action?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as UmbraApp

        setContent {
            UmbraTheme {
                UmbraNavHost(
                    app = app,
                    requestVpnConsent = ::requestVpnConsentThen,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.shizukuPermissionManager.refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Shizuku's pre-API-23-style permission path funnels through this callback too.
        Shizuku.onRequestPermissionResult(requestCode, if (grantResults.isNotEmpty()) grantResults[0] else -1)
    }

    /** Runs [onGranted] immediately if VPN permission is already held, otherwise after the system consent dialog. */
    private fun requestVpnConsentThen(onGranted: () -> Unit) {
        val consentIntent = VpnService.prepare(this)
        if (consentIntent != null) {
            pendingAfterConsent = onGranted
            vpnConsentLauncher.launch(consentIntent)
        } else {
            onGranted()
        }
    }
}
