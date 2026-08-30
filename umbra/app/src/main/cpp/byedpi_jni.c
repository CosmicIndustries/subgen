/*
 * Thin JNI bridge onto hufrea/byedpi (external/byedpi, unmodified).
 *
 * byedpi is a CLI tool: main.c's main() does
 *     parse_args(argc, argv) -> init() -> run(&params.laddr)
 * where run() blocks forever servicing the desync proxy event loop and
 * installs `signal(SIGTERM, on_cancel)` / `signal(SIGINT, on_cancel)` before
 * doing so (see external/byedpi/proxy.c). on_cancel() just does
 * `shutdown(server_fd, SHUT_RDWR)`, which breaks epoll/poll out of its wait
 * with EINTR and lets the `while (!pool->brk)` loop in conev.c's
 * loop_event() unwind.
 *
 * We reuse that exact, upstream-tested shutdown path rather than poking at
 * byedpi's internal pool/brk state ourselves: jniStopProxy() delivers
 * SIGTERM to the specific pthread running run(), which invokes byedpi's own
 * on_cancel() on that thread.
 *
 * parse_args()/init() are plain (non-static) C functions defined in
 * external/byedpi/main.c but never declared in a header upstream (main.c is
 * the only caller in the original CLI), so we declare them ourselves here.
 */

#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>

#include "params.h"
#include "proxy.h"

extern int parse_args(int argc, char **argv);
extern int init(void);

static pthread_t g_thread;
static volatile int g_running = 0;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

struct start_args {
    int argc;
    char **argv;
};

static void *run_proxy(void *arg) {
    struct start_args *a = (struct start_args *)arg;

    int status = parse_args(a->argc, a->argv);
    if (status == 0) {
        status = init();
    }
    if (status == 0) {
        run(&params.laddr);
    }

    for (int i = 0; i < a->argc; i++) {
        free(a->argv[i]);
    }
    free(a->argv);
    free(a);

    pthread_mutex_lock(&g_lock);
    g_running = 0;
    pthread_mutex_unlock(&g_lock);
    return NULL;
}

JNIEXPORT jint JNICALL
Java_com_cosmicindustries_umbra_dpi_ByeDpiProxy_jniStartProxy(
        JNIEnv *env, jobject thiz, jobjectArray jargs) {
    (void)thiz;

    pthread_mutex_lock(&g_lock);
    if (g_running) {
        pthread_mutex_unlock(&g_lock);
        return -1;
    }

    jsize argc = (*env)->GetArrayLength(env, jargs);
    // argv[0] is conventionally the program name; byedpi's getopt-based
    // parse_args() expects it even though it never inspects it.
    char **argv = calloc((size_t)argc + 1, sizeof(char *));
    if (!argv) {
        pthread_mutex_unlock(&g_lock);
        return -1;
    }
    argv[0] = strdup("byedpi");

    for (jsize i = 0; i < argc; i++) {
        jstring jstr = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
        const char *chars = (*env)->GetStringUTFChars(env, jstr, NULL);
        argv[i + 1] = strdup(chars);
        (*env)->ReleaseStringUTFChars(env, jstr, chars);
        (*env)->DeleteLocalRef(env, jstr);
    }

    struct start_args *a = malloc(sizeof(struct start_args));
    a->argc = (int)argc + 1;
    a->argv = argv;

    g_running = 1;
    int rc = pthread_create(&g_thread, NULL, run_proxy, a);
    if (rc != 0) {
        g_running = 0;
        pthread_mutex_unlock(&g_lock);
        return -1;
    }
    pthread_mutex_unlock(&g_lock);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_cosmicindustries_umbra_dpi_ByeDpiProxy_jniStopProxy(
        JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;

    pthread_mutex_lock(&g_lock);
    int was_running = g_running;
    pthread_t t = g_thread;
    pthread_mutex_unlock(&g_lock);

    if (!was_running) {
        return;
    }
    pthread_kill(t, SIGTERM);
    pthread_join(t, NULL);
}

JNIEXPORT void JNICALL
Java_com_cosmicindustries_umbra_dpi_ByeDpiProxy_jniForceClose(
        JNIEnv *env, jobject thiz, jint fd) {
    (void)env;
    (void)thiz;
    shutdown((int)fd, SHUT_RDWR);
    close((int)fd);
}
