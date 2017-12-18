#include <jni.h>
#include <unistd.h>
#include "src/ndkhelper.h"
#include "src/GeoLite2PP.hpp"

#ifdef __cplusplus
extern "C" {
#endif

using namespace GeoLite2PP;

DB *mmdb;

JNIEXPORT jint JNICALL
Java_com_hzy_maxmind_MaxMindApi_open(JNIEnv *env, jclass type, jstring dbPath_) {
    const char *dbPath = env->GetStringUTFChars(dbPath_, 0);
    int ret = 1;
    if (0 != access(dbPath, R_OK)) {
        LOGE("db file access failed[%s]!", dbPath);
    } else {
        mmdb = new DB(dbPath);
        ret = 0;
    }
    env->ReleaseStringUTFChars(dbPath_, dbPath);
    return ret;
}

JNIEXPORT void JNICALL
Java_com_hzy_maxmind_MaxMindApi_close(JNIEnv *env, jclass type) {
    if (mmdb != NULL) {
        delete (mmdb);
    }
}

JNIEXPORT jstring JNICALL
Java_com_hzy_maxmind_MaxMindApi_lookupIpString(JNIEnv *env, jclass type, jstring ipAddr_) {
    const char *ipAddr = env->GetStringUTFChars(ipAddr_, 0);
    std::string result = mmdb->lookup(ipAddr);
    env->ReleaseStringUTFChars(ipAddr_, ipAddr);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_hzy_maxmind_MaxMindApi_getLibVersion(JNIEnv *env, jclass type) {
    std::string result = mmdb->get_lib_version_mmdb();
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_hzy_maxmind_MaxMindApi_getLib2PPVersion(JNIEnv *env, jclass type) {
    std::string result = mmdb->get_lib_version_geolite2pp();
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_hzy_maxmind_MaxMindApi_getMetaData(JNIEnv *env, jclass type) {
    std::string result = mmdb->get_metadata();
    return env->NewStringUTF(result.c_str());
}

#ifdef __cplusplus
}
#endif