# Native JNI entry points are looked up by name; keep them.
-keepclasseswithmembernames class * {
    native <methods>;
}

# WireGuard's GoBackend calls into generated Go bindings via reflection.
-keep class com.wireguard.android.backend.** { *; }
-keep class com.wireguard.config.** { *; }

# Shizuku IPC uses AIDL-generated stubs.
-keep class rikka.shizuku.** { *; }

# Room entities/DAOs are referenced by the generated implementation classes.
-keep class com.cosmicindustries.umbra.logging.** { *; }
-keep class com.cosmicindustries.umbra.firewall.AppRule { *; }
