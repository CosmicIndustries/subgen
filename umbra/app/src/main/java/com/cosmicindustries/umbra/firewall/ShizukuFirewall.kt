package com.cosmicindustries.umbra.firewall

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Hard per-app network block, enforced at the OS level via a privileged
 * shell (obtained through Shizuku, no root required) rather than by routing
 * — this is what makes [AppMode.BLOCKED] apply regardless of which VPN mode
 * is active, or whether any VPN is running at all. Same mechanism ShizuWall
 * uses (per its exported settings: `working_mode: "SHIZUKU"`).
 *
 * NOTE ON CONFIDENCE: [Shizuku.newProcess] and the exact `cmd netpolicy`
 * grammar below are written to the best of available documentation, but
 * neither has been exercised against a real device/Shizuku instance in this
 * environment (no Android runtime here — see BUILDING.md). Treat the
 * commands in [blockCommandsFor]/[unblockCommandsFor] as the first thing to
 * verify (`adb shell cmd netpolicy --help` on a test device) if blocking
 * doesn't take effect.
 */
class ShizukuFirewall {

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

    private fun runShizukuCommand(cmd: List<String>): Boolean = try {
        val process = Shizuku.newProcess(cmd.toTypedArray(), null, null)
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (e: Exception) {
        false
    }
}
