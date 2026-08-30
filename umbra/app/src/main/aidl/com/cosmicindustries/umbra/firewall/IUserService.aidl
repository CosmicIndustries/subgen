package com.cosmicindustries.umbra.firewall;

// Runs inside the Shizuku user-service process (UID shell or root), not this
// app's own process — see firewall/UserService.kt and Shizuku.bindUserService().
interface IUserService {
    // Runs `cmd` via ProcessBuilder in the elevated process and returns its exit code.
    int exec(in String[] cmd);

    // Required by Shizuku's user-service contract: the transaction code below is
    // mandated by the API (see Shizuku.bindUserService's kdoc) — Shizuku calls this
    // to let the service clean up and System.exit() when unbound.
    void destroy() = 16777114;
}
