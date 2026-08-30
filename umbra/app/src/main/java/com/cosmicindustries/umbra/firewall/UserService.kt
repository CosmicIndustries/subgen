package com.cosmicindustries.umbra.firewall

import java.io.IOException

/**
 * Runs in a *separate* process Shizuku spawns with elevated UID (shell or
 * root), not in Umbra's normal app process — see IUserService.aidl and
 * Shizuku.bindUserService()'s kdoc. No Context/Application lifecycle here;
 * this is effectively a tiny standalone process whose only job is running
 * commands on ShizukuFirewall's behalf.
 */
class UserService : IUserService.Stub() {

    override fun exec(cmd: Array<String>): Int = try {
        val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        process.waitFor()
    } catch (e: IOException) {
        -1
    }

    override fun destroy() {
        System.exit(0)
    }
}
