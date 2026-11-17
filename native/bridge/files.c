#define _GNU_SOURCE
#include <jni.h>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>

JNIEXPORT jint JNICALL Java_com_koinos_teleno_NativeFiles_lock(JNIEnv *env, jclass type, jstring path) {
    (void)type;
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (!name) return -1;
    int fd = open(name, O_RDWR | O_CREAT | O_NOFOLLOW | O_CLOEXEC, 0600);
    (*env)->ReleaseStringUTFChars(env, path, name);
    struct stat st;
    if (fd < 0 || fstat(fd, &st) || !S_ISREG(st.st_mode) || st.st_nlink != 1 || st.st_uid != geteuid()
        || (st.st_mode & 077) || flock(fd, LOCK_EX | LOCK_NB)) {
        if (fd >= 0) close(fd);
        jclass error = (*env)->FindClass(env, "java/io/IOException");
        if (error) (*env)->ThrowNew(env, error, "The node still owns its database. Wait for it to stop before changing storage.");
        return -1;
    }
    return fd;
}
JNIEXPORT void JNICALL Java_com_koinos_teleno_NativeFiles_unlock(JNIEnv *env, jclass type, jint fd) {
    (void)env; (void)type; if (fd >= 0) close(fd);
}
