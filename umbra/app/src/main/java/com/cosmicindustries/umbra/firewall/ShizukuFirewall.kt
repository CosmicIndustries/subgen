package com.cosmicindustries.umbra.firewall

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.cosmicindustries.umbra.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Hard per-app network block, enforced at the OS level via a privileged
 * process Shizuku spawns for us (no root required) rather than by routing
 * — this is what makes [AppMode.BLOCKED] apply regardless of which VPN mode
 * is active, or whether any VPN is running at all. Same mechanism ShizuWall
 * uses (per its exported settings: `working_mode: "SHIZUKU"`).
 *
 * `Shizuku.newProcess(...)` (the old direct "run a shell command" call) is
 * `private` as of shizuku-api 13.1.5 — confirmed against the real published
 * artifact, not assumed. The current supported path is
 * [Shizuku.bindUserService]: you define your own AIDL service
 * ([IUserService]) and Shizuku launches it in a separate process running as
 * UID shell (or root); [UserService.exec] is what actually runs our
 * commands there.
 *
 * ## Why this isn't just `cmd netpolicy set-uid-policy ... reject-all`
 *
 * That was the original (unverified) implementation. Investigating
 * [dorumrr/de1984](https://github.com/dorumrr/de1984) — an actively
 * hardware-tested Shizuku/root firewall — turned up a real, documented
 * problem with it: `POLICY_REJECT_ALL` (the netpolicy value `reject-all`
 * maps to) **is not part of AOSP at all**; it only exists on LineageOS-type
 * ROMs (de1984's `FIREWALL.md`, section 4, verified on hardware). On a
 * stock/OEM ROM, storing that policy value doesn't get you a WiFi block —
 * de1984 calibrates for this at runtime (write it, read back via
 * `dumpsys netpolicy`, confirm the ROM actually enforces it as REJECT_ALL)
 * and falls back to `POLICY_REJECT_METERED_BACKGROUND`, which is AOSP-
 * standard but **only blocks background mobile data — not WiFi, not
 * foreground use at all**. So the original single netpolicy command here
 * was likely a silent no-op for "block WiFi" on most non-LineageOS
 * devices — not a hypothetical, a confirmed-elsewhere gap.
 *
 * The fix: prefer Android 13+'s `OEM_DENY_3` UID firewall chain instead
 * (`cmd connectivity set-chain3-enabled`/`set-package-networking-enabled`),
 * the same AOSP-standard mechanism de1984's `ConnectivityManagerFirewallBackend`
 * uses and has verified actually blocks all networking (WiFi included) for
 * a package, no root needed — just Shizuku (ADB pairing is fine) and API
 * 33+. `netpolicy`/`appops` remain as the fallback below API 33, with the
 * same honest caveat de1984 documents: it may only stop background mobile
 * data on a non-LineageOS ROM, not a full block.
 */
class ShizukuFirewall {

    companion object {
        private const val TAG = "Umbra/Shizuku"
    }

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("firewall")
        .debuggable(BuildConfig.DEBUG)
        .version(1)

    @Volatile private var service: IUserService? = null
    @Volatile private var chain3Enabled = false

    private val supportsConnectivityChain: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    suspend fun block(rule: AppRule): Boolean = runPrivileged(blockCommandsFor(rule)).also {
        Log.d(TAG, "block(${rule.packageName}): ${if (it) "ok" else "FAILED"}")
    }

    suspend fun unblock(rule: AppRule): Boolean = runPrivileged(unblockCommandsFor(rule)).also {
        Log.d(TAG, "unblock(${rule.packageName}): ${if (it) "ok" else "FAILED"}")
    }

    suspend fun applyAll(blockedRules: List<AppRule>, unblockedRules: List<AppRule>) {
        Log.d(TAG, "applyAll: ${blockedRules.size} to block, ${unblockedRules.size} to unblock")
        if (supportsConnectivityChain) {
            setChain3Enabled(blockedRules.isNotEmpty())
        }
        blockedRules.forEach { block(it) }
        unblockedRules.forEach { unblock(it) }
    }

    /** Idempotent: `set-chain3-enabled` is safe to call repeatedly with the same value. */
    private suspend fun setChain3Enabled(enabled: Boolean) {
        if (chain3Enabled == enabled) return
        if (runShizukuCommand(listOf("cmd", "connectivity", "set-chain3-enabled", enabled.toString()))) {
            chain3Enabled = enabled
        } else {
            Log.w(TAG, "setChain3Enabled($enabled): command failed")
        }
    }

    private fun blockCommandsFor(rule: AppRule): List<List<String>> = buildList {
        if (supportsConnectivityChain) {
            add(listOf("cmd", "connectivity", "set-package-networking-enabled", "false", rule.packageName))
        } else {
            add(listOf("cmd", "netpolicy", "set-uid-policy", rule.uid.toString(), "reject-all"))
        }
        add(listOf("cmd", "appops", "set", rule.packageName, "RUN_IN_BACKGROUND", "deny"))
        add(listOf("cmd", "appops", "set", rule.packageName, "RUN_ANY_IN_BACKGROUND", "deny"))
    }

    private fun unblockCommandsFor(rule: AppRule): List<List<String>> = buildList {
        if (supportsConnectivityChain) {
            add(listOf("cmd", "connectivity", "set-package-networking-enabled", "true", rule.packageName))
        } else {
            add(listOf("cmd", "netpolicy", "set-uid-policy", rule.uid.toString(), "none"))
        }
        add(listOf("cmd", "appops", "set", rule.packageName, "RUN_IN_BACKGROUND", "allow"))
        add(listOf("cmd", "appops", "set", rule.packageName, "RUN_ANY_IN_BACKGROUND", "allow"))
    }

    private suspend fun runPrivileged(commands: List<List<String>>): Boolean = withContext(Dispatchers.IO) {
        commands.all { cmd -> runShizukuCommand(cmd) }
    }

    private suspend fun runShizukuCommand(cmd: List<String>): Boolean {
        val cmdStr = cmd.joinToString(" ")
        return try {
            val exitCode = getService().exec(cmd.toTypedArray())
            Log.d(TAG, "exec: $cmdStr -> exit $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "exec: $cmdStr -> threw", e)
            false
        }
    }

    private suspend fun getService(): IUserService {
        service?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val bound = IUserService.Stub.asInterface(binder)
                    service = bound
                    if (cont.isActive) cont.resume(bound)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }
            Shizuku.bindUserService(userServiceArgs, connection)
        }
    }
}
