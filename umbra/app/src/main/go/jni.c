/* SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from WireGuard for Android's tunnel/tools/libwg-go/jni.c
 * (Copyright © 2017-2021 Jason A. Donenfeld <Jason@zx2c4.com>): cgo's
 * `-buildmode=c-shared` exports Go functions as plain C functions taking a
 * `go_string{ptr,len}` struct for each Go `string` parameter rather than a
 * null-terminated C string, and JNI dynamic symbol resolution requires the
 * exact `Java_<package>_<Class>_<method>` name — so a thin C shim doing the
 * JNI<->Go type conversion is required either way. cgo automatically
 * compiles and links any .c file sitting alongside a cgo-using .go file in
 * the same package directory, which is how this gets built without a
 * separate build step.
 *
 * The only functional difference from upstream's jni.c is the target class
 * (WireGuardBridge instead of GoBackend) and the added wgTurnOnViaByedpi
 * export.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>

struct go_string { const char *str; long n; };

extern int wgTurnOn(struct go_string ifname, int tun_fd, struct go_string settings);
extern int wgTurnOnViaByedpi(struct go_string ifname, int tun_fd, struct go_string settings, struct go_string byedpi_addr);
extern void wgTurnOff(int handle);
extern int wgGetSocketV4(int handle);
extern int wgGetSocketV6(int handle);
extern char *wgGetConfig(int handle);
extern char *wgVersion(void);

JNIEXPORT jint JNICALL Java_com_cosmicindustries_umbra_tunnel_WireGuardBridge_wgTurnOn(JNIEnv *env, jclass c, jstring ifname, jint tun_fd, jstring settings)
{
	const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
	size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
	const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
	size_t settings_len = (*env)->GetStringUTFLength(env, settings);
	int ret = wgTurnOn((struct go_string){
		.str = ifname_str,
		.n = ifname_len
	}, tun_fd, (struct go_string){
		.str = settings_str,
		.n = settings_len
	});
	(*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
	(*env)->ReleaseStringUTFChars(env, settings, settings_str);
	return ret;
}

JNIEXPORT jint JNICALL Java_com_cosmicindustries_umbra_tunnel_WireGuardBridge_wgTurnOnViaByedpi(JNIEnv *env, jclass c, jstring ifname, jint tun_fd, jstring settings, jstring byedpi_addr)
{
	const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
	size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
	const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
	size_t settings_len = (*env)->GetStringUTFLength(env, settings);
	const char *byedpi_addr_str = (*env)->GetStringUTFChars(env, byedpi_addr, 0);
	size_t byedpi_addr_len = (*env)->GetStringUTFLength(env, byedpi_addr);
	int ret = wgTurnOnViaByedpi((struct go_string){
		.str = ifname_str,
		.n = ifname_len
	}, tun_fd, (struct go_string){
		.str = settings_str,
		.n = settings_len
	}, (struct go_string){
		.str = byedpi_addr_str,
		.n = byedpi_addr_len
	});
	(*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
	(*env)->ReleaseStringUTFChars(env, settings, settings_str);
	(*env)->ReleaseStringUTFChars(env, byedpi_addr, byedpi_addr_str);
	return ret;
}

JNIEXPORT void JNICALL Java_com_cosmicindustries_umbra_tunnel_WireGuardBridge_wgTurnOff(JNIEnv *env, jclass c, jint handle)
{
	wgTurnOff(handle);
}

JNIEXPORT jint JNICALL Java_com_cosmicindustries_umbra_tunnel_WireGuardBridge_wgGetSocketV4(JNIEnv *env, jclass c, jint handle)
{
	return wgGetSocketV4(handle);
}

JNIEXPORT jint JNICALL Java_com_cosmicindustries_umbra_tunnel_WireGuardBridge_wgGetSocketV6(JNIEnv *env, jclass c, jint handle)
{
	return wgGetSocketV6(handle);
}

JNIEXPORT jstring JNICALL Java_com_cosmicindustries_umbra_tunnel_WireGuardBridge_wgGetConfig(JNIEnv *env, jclass c, jint handle)
{
	jstring ret;
	char *config = wgGetConfig(handle);
	if (!config)
		return NULL;
	ret = (*env)->NewStringUTF(env, config);
	free(config);
	return ret;
}

JNIEXPORT jstring JNICALL Java_com_cosmicindustries_umbra_tunnel_WireGuardBridge_wgVersion(JNIEnv *env, jclass c)
{
	jstring ret;
	char *version = wgVersion();
	if (!version)
		return NULL;
	ret = (*env)->NewStringUTF(env, version);
	free(version);
	return ret;
}
