#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#define LOG_TAG "NativeMonitor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

typedef struct {
    char id[64];
    char name[128];
    char ip[64];
    int is_enabled;
    int is_on; // 0: OFF, 1: ON, -1: PENDING
} DeviceNative;

DeviceNative *g_devices = NULL;
int g_device_count = 0;
pthread_t g_monitor_thread;
int g_is_monitoring = 0;
long g_interval_ms = 5000;
pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;

int native_ping_internal(const char *host) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "ping -c 1 -w 1 %s > /dev/null 2>&1", host);
    return system(cmd) == 0;
}

void *monitor_loop(void *arg) {
    while (g_is_monitoring) {
        pthread_mutex_lock(&g_mutex);
        for (int i = 0; i < g_device_count; i++) {
            if (g_devices[i].is_enabled) {
                char ip_copy[64];
                strncpy(ip_copy, g_devices[i].ip, 63);
                pthread_mutex_unlock(&g_mutex);

                int status = native_ping_internal(ip_copy);

                pthread_mutex_lock(&g_mutex);
                g_devices[i].is_on = status ? 1 : 0;
            }
        }
        pthread_mutex_unlock(&g_mutex);
        usleep(g_interval_ms * 1000);
    }
    return NULL;
}

JNIEXPORT void JNICALL
Java_com_example_myapplication2_MonitorService_startNativeMonitor(JNIEnv *env, jobject thiz,
                                                                  jlong interval) {
    if (g_is_monitoring) return;
    g_is_monitoring = 1;
    g_interval_ms = (long) interval;
    pthread_create(&g_monitor_thread, NULL, monitor_loop, NULL);
}

JNIEXPORT void JNICALL
Java_com_example_myapplication2_MonitorService_stopNativeMonitor(JNIEnv *env, jobject thiz) {
    g_is_monitoring = 0;
    pthread_join(g_monitor_thread, NULL);
}

JNIEXPORT void JNICALL
Java_com_example_myapplication2_DeviceStore_setNativeDevices(JNIEnv *env, jobject thiz,
                                                             jobjectArray devices_array) {
    pthread_mutex_lock(&g_mutex);
    if (g_devices) free(g_devices);

    g_device_count = (*env)->GetArrayLength(env, devices_array);
    g_devices = malloc(sizeof(DeviceNative) * g_device_count);

    jclass device_cls = (*env)->FindClass(env, "com/example/myapplication2/Device");
    jfieldID id_fid = (*env)->GetFieldID(env, device_cls, "id", "Ljava/lang/String;");
    jfieldID name_fid = (*env)->GetFieldID(env, device_cls, "name", "Ljava/lang/String;");
    jfieldID ip_fid = (*env)->GetFieldID(env, device_cls, "ip", "Ljava/lang/String;");
    jfieldID enabled_fid = (*env)->GetFieldID(env, device_cls, "isEnabled", "Z");

    for (int i = 0; i < g_device_count; i++) {
        jobject dev_obj = (*env)->GetObjectArrayElement(env, devices_array, i);

        jstring jid = (jstring) (*env)->GetObjectField(env, dev_obj, id_fid);
        jstring jname = (jstring) (*env)->GetObjectField(env, dev_obj, name_fid);
        jstring jip = (jstring) (*env)->GetObjectField(env, dev_obj, ip_fid);
        jboolean jenabled = (*env)->GetBooleanField(env, dev_obj, enabled_fid);

        const char *cid = (*env)->GetStringUTFChars(env, jid, NULL);
        const char *cname = (*env)->GetStringUTFChars(env, jname, NULL);
        const char *cip = (*env)->GetStringUTFChars(env, jip, NULL);

        strncpy(g_devices[i].id, cid, 63);
        strncpy(g_devices[i].name, cname, 127);
        strncpy(g_devices[i].ip, cip, 63);
        g_devices[i].is_enabled = jenabled;
        g_devices[i].is_on = -1; // PENDING

        (*env)->ReleaseStringUTFChars(env, jid, cid);
        (*env)->ReleaseStringUTFChars(env, jname, cname);
        (*env)->ReleaseStringUTFChars(env, jip, cip);
        (*env)->DeleteLocalRef(env, dev_obj);
    }
    pthread_mutex_unlock(&g_mutex);
}

JNIEXPORT jint JNICALL
Java_com_example_myapplication2_DeviceViewModel_getNativeStatus(JNIEnv *env, jobject thiz,
                                                                jstring device_id) {
    const char *id = (*env)->GetStringUTFChars(env, device_id, NULL);
    int status = -2; // NOT_FOUND

    pthread_mutex_lock(&g_mutex);
    for (int i = 0; i < g_device_count; i++) {
        if (strcmp(g_devices[i].id, id) == 0) {
            status = g_devices[i].is_on;
            break;
        }
    }
    pthread_mutex_unlock(&g_mutex);

    (*env)->ReleaseStringUTFChars(env, device_id, id);
    return status;
}

JNIEXPORT jboolean JNICALL
Java_com_example_myapplication2_MonitorService_nativePing(JNIEnv *env, jobject thiz,
                                                          jstring host_jstr) {
    if (host_jstr == NULL) return JNI_FALSE;
    const char *host = (*env)->GetStringUTFChars(env, host_jstr, NULL);
    if (host == NULL) return JNI_FALSE;
    int result = native_ping_internal(host);
    (*env)->ReleaseStringUTFChars(env, host_jstr, host);
    return result ? JNI_TRUE : JNI_FALSE;
}
