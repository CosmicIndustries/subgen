package com.cosmicindustries.umbra.firewall;

// Runs inside the Shizuku user-service process (UID shell or root), not this
// app's own process — see firewall/UserService.kt and Shizuku.bindUserService().
interface IUserService {
    // AIDL requires every method to have an explicit transaction id once any
    // one of them does (destroy()'s id below is mandated by Shizuku itself),
    // so exec() gets one too — any low number not colliding with destroy()'s
    // works.
    int exec(in String[] cmd) = 1;

    // Required by Shizuku's user-service contract: the transaction code below is
    // mandated by the API (see Shizuku.bindUserService's kdoc) — Shizuku calls this
    // to let the service clean up and System.exit() when unbound.
    void destroy() = 16777114;
}
