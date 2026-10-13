#ifndef JNI_MD_H
#define JNI_MD_H
/* Windows / MinGW cross-compilation overrides for JNI platform types */
#define JNIEXPORT  __declspec(dllexport)
#define JNIIMPORT  __declspec(dllimport)
#define JNICALL    __stdcall
typedef int          jint;
typedef long long    jlong;
typedef signed char  jbyte;
#endif
