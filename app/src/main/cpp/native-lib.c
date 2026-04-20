#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>

JNIEXPORT jboolean

JNICALL
Java_com_example_myapplication2_MonitorService_nativePing(JNIEnv *env, jobject thiz,
                                                          jstring host_jstr) {
    if (host_jstr == NULL) return JNI_FALSE;

    const char *host = (*env)->GetStringUTFChars(env, host_jstr, NULL);
    if (host == NULL) return JNI_FALSE;

    // We use the system ping command for simplicity and reliability regarding permissions.
    // However, we execute it via a native fork/exec to demonstrate native integration.
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "ping -c 1 -w 1 %s > /dev/null 2>&1", host);

    int result = system(cmd);

    (*env)->ReleaseStringUTFChars(env, host_jstr, host);

    return (result == 0) ? JNI_TRUE : JNI_FALSE;
}
