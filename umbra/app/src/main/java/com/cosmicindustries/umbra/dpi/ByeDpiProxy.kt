package com.cosmicindustries.umbra.dpi

/**
 * JNI bridge onto `libbyedpi.so`, built from the hufrea/byedpi sources
 * vendored at `umbra/external/byedpi` (see app/src/main/cpp/byedpi_jni.c and
 * CMakeLists.txt). The native side runs byedpi's own CLI arg parser against
 * whatever we pass here, so [args] must look exactly like `ciadpi`/`byedpi`
 * command-line flags — see [ByeDpiConfig] for the typed builder.
 */
object ByeDpiProxy {
    init {
        System.loadLibrary("byedpi")
    }

    /** Starts the desync proxy on a background native thread. Returns 0 on success. */
    external fun jniStartProxy(args: Array<String>): Int

    /** Blocks until the proxy thread has fully stopped. Safe to call when not running. */
    external fun jniStopProxy()

    /** Force-closes a single client connection fd (used to kick a stuck flow). */
    external fun jniForceClose(fd: Int)
}
