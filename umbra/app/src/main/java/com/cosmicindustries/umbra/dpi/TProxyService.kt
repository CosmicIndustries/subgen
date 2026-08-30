package com.cosmicindustries.umbra.dpi

/**
 * JNI bridge onto `libhev-socks5-tunnel.so` (heiher/hev-socks5-tunnel,
 * vendored at `umbra/external/hev-socks5-tunnel`). This is the TUN <->
 * SOCKS5 bridge that turns the raw IP packets Umbra reads off its TUN
 * device into connections against byedpi's local SOCKS5 listener.
 *
 * Unlike byedpi, hev-socks5-tunnel ships its OWN ready-made Android JNI
 * glue (src/hev-jni.c) that binds to a Java class via the `PKGNAME`/
 * `CLSNAME` compile-time macros instead of hand-written `Java_*` symbols —
 * CMakeLists.txt points those macros at this exact class
 * (`com/cosmicindustries/umbra/dpi/TProxyService`), so the method names and
 * signatures below must match `native_methods[]` in hev-jni.c precisely.
 */
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    /** @param configPath path to a YAML config file (see [TunnelConfig]). @param fd the TUN device fd. */
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    external fun TProxyStopService(): Boolean

    external fun TProxyIsRunning(): Boolean

    /** [txPackets, txBytes, rxPackets, rxBytes]. */
    external fun TProxyGetStats(): LongArray
}
