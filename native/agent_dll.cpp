#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <jni.h>
#include <jvmti.h>
#include <string>
#include <vector>

typedef jint (JNICALL *GetCreatedJVMs_t)(JavaVM **, jsize, jsize *);

static jvmtiEnv  *s_jvmti       = nullptr;
static jobject    s_transformer  = nullptr;
static jmethodID  s_transformMid = nullptr;

// ── JVMTI ClassFileLoadHook ──────────────────────────────────────────────────
void JNICALL ClassFileLoadHook(
    jvmtiEnv *jvmti, JNIEnv *env,
    jclass classBeingRedefined, jobject loader,
    const char *name, jobject protectionDomain,
    jint classDataLen, const unsigned char *classData,
    jint *newClassDataLen, unsigned char **newClassData)
{
    if (!s_transformer || !s_transformMid || !name) return;

    jbyteArray inBytes = env->NewByteArray(classDataLen);
    env->SetByteArrayRegion(inBytes, 0, classDataLen, (const jbyte *)classData);
    jstring nameStr = env->NewStringUTF(name);

    jbyteArray result = (jbyteArray)env->CallObjectMethod(
        s_transformer, s_transformMid,
        loader, nameStr, classBeingRedefined, protectionDomain, inBytes);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    } else if (result) {
        jint len = env->GetArrayLength(result);
        unsigned char *buf = nullptr;
        jvmti->Allocate(len, &buf);
        env->GetByteArrayRegion(result, 0, len, (jbyte *)buf);
        *newClassData    = buf;
        *newClassDataLen = len;
        env->DeleteLocalRef(result);
    }

    env->DeleteLocalRef(inBytes);
    env->DeleteLocalRef(nameStr);
}

// ── Main agent thread ────────────────────────────────────────────────────────
static DWORD WINAPI AgentThread(LPVOID hMod)
{
    // 1. Build path to agent.jar (same directory as this DLL)
    char selfPath[MAX_PATH];
    GetModuleFileNameA((HMODULE)hMod, selfPath, MAX_PATH);
    char *sl = strrchr(selfPath, '\\');
    std::string dir = sl ? std::string(selfPath, sl + 1) : "";
    std::string jarPath = dir + "agent.jar";

    // 2. Wait for jvm.dll to appear in this process
    HMODULE jvmDll = nullptr;
    for (int i = 0; i < 150 && !jvmDll; i++) {
        jvmDll = GetModuleHandleA("jvm.dll");
        if (!jvmDll) Sleep(200);
    }
    if (!jvmDll) return 1;

    auto getVMs = (GetCreatedJVMs_t)GetProcAddress(jvmDll, "JNI_GetCreatedJavaVMs");
    if (!getVMs) return 1;

    // 3. Wait for a JavaVM to be created
    JavaVM *jvm = nullptr;
    jsize count = 0;
    for (int i = 0; i < 100 && count == 0; i++) {
        getVMs(&jvm, 1, &count);
        if (count == 0) Sleep(500);
    }
    if (!jvm || count == 0) return 1;

    // 4. Get JVMTI environment
    if (jvm->GetEnv((void **)&s_jvmti, JVMTI_VERSION_1_2) != JNI_OK) return 1;

    // 5. Add agent.jar to the bootstrap classloader so Hooks/Modules are visible
    //    to Minecraft's own classloader after retransformation.
    if (s_jvmti->AddToBootstrapClassLoaderSearch(jarPath.c_str()) != JVMTI_ERROR_NONE)
        return 1;

    // 6. Enable class-retransformation capability
    jvmtiCapabilities caps = {};
    caps.can_retransform_classes = 1;
    if (s_jvmti->AddCapabilities(&caps) != JVMTI_ERROR_NONE) return 1;

    // 7. Attach this thread to the JVM
    JNIEnv *env = nullptr;
    JavaVMAttachArgs aa = {JNI_VERSION_1_8, (char *)"SystemClientAgent", nullptr};
    if (jvm->AttachCurrentThread((void **)&env, &aa) != JNI_OK) return 1;

    // 8. Give Minecraft time to finish loading
    Sleep(6000);

    // 9. Find HookTransformer (now on bootstrap path)
    jclass tClass = env->FindClass("com/modloader/HookTransformer");
    if (!tClass || env->ExceptionCheck()) {
        env->ExceptionClear();
        jvm->DetachCurrentThread();
        return 1;
    }

    // Pre-load ASM classes before we register the JVMTI hook to avoid
    // class-loading re-entrancy inside the ClassFileLoadHook callback.
    jmethodID preloadMid = env->GetStaticMethodID(tClass, "preload", "()V");
    if (preloadMid && !env->ExceptionCheck())
        env->CallStaticVoidMethod(tClass, preloadMid);
    if (env->ExceptionCheck()) env->ExceptionClear();

    // 10. Create HookTransformer instance and grab transform() method ID
    jmethodID ctor = env->GetMethodID(tClass, "<init>", "()V");
    jobject inst = env->NewObject(tClass, ctor);
    if (!inst || env->ExceptionCheck()) {
        env->ExceptionClear();
        jvm->DetachCurrentThread();
        return 1;
    }
    s_transformer  = env->NewGlobalRef(inst);
    s_transformMid = env->GetMethodID(tClass, "transform",
        "(Ljava/lang/ClassLoader;Ljava/lang/String;"
        "Ljava/lang/Class;Ljava/security/ProtectionDomain;[B)[B");
    if (!s_transformMid || env->ExceptionCheck()) {
        env->ExceptionClear();
        jvm->DetachCurrentThread();
        return 1;
    }

    // 11. Register JVMTI ClassFileLoadHook
    jvmtiEventCallbacks cbs = {};
    cbs.ClassFileLoadHook = ClassFileLoadHook;
    s_jvmti->SetEventCallbacks(&cbs, sizeof(cbs));
    s_jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);

    // 12. Resolve target class names from ObfNames
    jclass obf = env->FindClass("com/modloader/ObfNames");
    if (!obf || env->ExceptionCheck()) {
        env->ExceptionClear();
        s_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
        jvm->DetachCurrentThread();
        return 1;
    }

    const char *ownerFields[] = {"onTick_OWNER", "onHud_OWNER", "onKey_OWNER", nullptr};
    std::vector<jclass> targets;
    for (int i = 0; ownerFields[i]; i++) {
        jfieldID fid = env->GetStaticFieldID(obf, ownerFields[i], "Ljava/lang/String;");
        if (!fid || env->ExceptionCheck()) { env->ExceptionClear(); continue; }
        jstring s = (jstring)env->GetStaticObjectField(obf, fid);
        if (!s) continue;
        const char *cs = env->GetStringUTFChars(s, nullptr);
        jclass cls = env->FindClass(cs);
        env->ReleaseStringUTFChars(s, cs);
        env->DeleteLocalRef(s);
        if (!cls || env->ExceptionCheck()) { env->ExceptionClear(); continue; }
        targets.push_back((jclass)env->NewGlobalRef(cls));
        env->DeleteLocalRef(cls);
    }

    // 13. Retransform the 3 Minecraft classes to inject our hooks
    if (!targets.empty()) {
        s_jvmti->RetransformClasses((jint)targets.size(), targets.data());
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    for (auto c : targets) env->DeleteGlobalRef(c);

    // 14. Disable hook — retransformation is done
    s_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);

    // 15. Call Agent.start() to enable Hooks.active and start IPC thread
    jclass agCls = env->FindClass("com/modloader/Agent");
    if (agCls && !env->ExceptionCheck()) {
        jmethodID startMid = env->GetStaticMethodID(agCls, "start", "()V");
        if (startMid && !env->ExceptionCheck())
            env->CallStaticVoidMethod(agCls, startMid);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    jvm->DetachCurrentThread();
    return 0;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD reason, LPVOID)
{
    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(hModule);
        CloseHandle(CreateThread(nullptr, 0, AgentThread, (LPVOID)hModule, 0, nullptr));
    }
    return TRUE;
}
