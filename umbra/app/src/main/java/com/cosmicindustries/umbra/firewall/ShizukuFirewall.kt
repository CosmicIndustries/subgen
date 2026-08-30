package com.cosmicindustries.umbra.firewall

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
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
 * NOTE ON CONFIDENCE: the exact `cmd netpolicy`/`cmd appops` grammar in
 * [blockCommandsFor]/[unblockCommandsFor] below has not been exercised
 * against a real device — see ARCHITECTURE.md's "What hasn't been
 * verified" section. Treat it as the first thing to check
 * (`adb shell cmd netpolicy --help`) if blocking doesn't take effect.
 */
class ShizukuFirewall {

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("firewall")
        .debuggable(BuildConfig.DEBUG)
        .version(1)

    @Volatile private var service: IUserService? = null

    suspend fun block(rule: AppRule): Boolean = runPrivileged(blockCommandsFor(rule))

    suspend fun unblock(rule: AppRule): Boolean = runPrivileged(unblockCommandsFor(rule))

    suspend fun applyAll(blockedRules: List<AppRule>, unblockedRules: List<AppRule>) {
        blockedRules.forEach { block(it) }
        unblockedRules.forEach { unblock(it) }
    }

    private fun blockCommandsFor(rule: AppRule): List<List<String>> = listOf(
        listOf("cmd", "netpolicy", "set-uid-policy", rule.uid.toString(), "reject-all"),
        listOf("cmd", "appops", "set", rule.packageName, "RUN_IN_BACKGROUND", "deny"),
        listOf("cmd", "appops", "set", rule.packageName, "RUN_ANY_IN_BACKGROUND", "deny"),
    )

    private fun unblockCommandsFor(rule: AppRule): List<List<String>> = listOf(
        listOf("cmd", "netpolicy", "set-uid-policy", rule.uid.toString(), "none"),
        listOf("cmd", "appops", "set", rule.packageName, "RUN_IN_BACKGROUND", "allow"),
        listOf("cmd", "appops", "set", rule.packageName, "RUN_ANY_IN_BACKGROUND", "allow"),
    )

    private suspend fun runPrivileged(commands: List<List<String>>): Boolean = withContext(Dispatchers.IO) {
        commands.all { cmd -> runShizukuCommand(cmd) }
    }

    private suspend fun runShizukuCommand(cmd: List<String>): Boolean = try {
        getService().exec(cmd.toTypedArray()) == 0
    } catch (e: Exception) {
        false
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
