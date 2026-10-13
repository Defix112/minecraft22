#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <jni.h>
#include <jvmti.h>
#include <string>
#include <map>
#include <vector>

typedef jint (JNICALL *GetCreatedJVMs_t)(JavaVM **, jsize, jsize *);

static jvmtiEnv *s_jvmti = nullptr;

// Phase control: 0=disabled, 1=capture original bytes, 2=return transformed bytes.
static volatile int g_phase = 0;
static std::map<std::string, std::vector<unsigned char>> g_captured;
static std::map<std::string, std::vector<unsigned char>> g_transformed;

// ClassFileLoadHook: pure C, no Java calls in either phase.
void JNICALL ClassFileLoadHook(
    jvmtiEnv *jvmti, JNIEnv * /*env*/,
    jclass /*classBeingRedefined*/, jobject /*loader*/,
    const char *name, jobject /*protectionDomain*/,
    jint classDataLen, const unsigned char *classData,
    jint *newClassDataLen, unsigned char **newClassData)
{
    if (!name) return;

    if (g_phase == 1) {
        // Just store original bytes — no Java calls, no class loading.
        g_captured[name].assign(classData, classData + classDataLen);
        return;
    }

    if (g_phase == 2) {
        // Return pre-transformed bytes — no Java calls.
        auto it = g_transformed.find(name);
        if (it != g_transformed.end() && !it->second.empty()) {
            unsigned char *buf = nullptr;
            if (jvmti->Allocate((jlong)it->second.size(), &buf) == JVMTI_ERROR_NONE && buf) {
                memcpy(buf, it->second.data(), it->second.size());
                *newClassData    = buf;
                *newClassDataLen = (jint)it->second.size();
            }
        }
    }
}

static DWORD WINAPI AgentThread(LPVOID hMod)
{
    // Build path to agent.jar in the same directory as this DLL.
    char selfPath[MAX_PATH];
    GetModuleFileNameA((HMODULE)hMod, selfPath, MAX_PATH);
    char *sl = strrchr(selfPath, '\\');
    std::string dir = sl ? std::string(selfPath, sl + 1) : "";
    std::string jarPath = dir + "agent.jar";

    // Wait for jvm.dll.
    HMODULE jvmDll = nullptr;
    for (int i = 0; i < 150 && !jvmDll; i++) {
        jvmDll = GetModuleHandleA("jvm.dll");
        if (!jvmDll) Sleep(200);
    }
    if (!jvmDll) return 1;

    auto getVMs = (GetCreatedJVMs_t)GetProcAddress(jvmDll, "JNI_GetCreatedJavaVMs");
    if (!getVMs) return 1;

    // Wait for JavaVM.
    JavaVM *jvm = nullptr;
    jsize count = 0;
    for (int i = 0; i < 100 && count == 0; i++) {
        getVMs(&jvm, 1, &count);
        if (count == 0) Sleep(500);
    }
    if (!jvm || count == 0) return 1;

    // Get JVMTI env.
    if (jvm->GetEnv((void **)&s_jvmti, JVMTI_VERSION_1_2) != JNI_OK) return 1;

    // Add agent.jar to bootstrap so com.modloader.* is visible everywhere.
    if (s_jvmti->AddToBootstrapClassLoaderSearch(jarPath.c_str()) != JVMTI_ERROR_NONE)
        return 1;

    // Enable retransform + redefine.
    jvmtiCapabilities caps = {};
    caps.can_retransform_classes = 1;
    caps.can_redefine_classes    = 1;
    s_jvmti->AddCapabilities(&caps);

    // Register ClassFileLoadHook.
    jvmtiEventCallbacks cbs = {};
    cbs.ClassFileLoadHook = ClassFileLoadHook;
    s_jvmti->SetEventCallbacks(&cbs, sizeof(cbs));

    // Attach this thread to the JVM.
    JNIEnv *env = nullptr;
    JavaVMAttachArgs aa = {JNI_VERSION_1_8, (char *)"SystemClientAgent", nullptr};
    if (jvm->AttachCurrentThread((void **)&env, &aa) != JNI_OK) return 1;

    // Wait for Minecraft to finish loading all classes.
    Sleep(6000);

    // Find HookTransformer (on bootstrap path).
    jclass tClass = env->FindClass("com/modloader/HookTransformer");
    if (!tClass || env->ExceptionCheck()) { env->ExceptionClear(); jvm->DetachCurrentThread(); return 1; }

    // Preload all ASM + inner classes before any hook fires.
    jmethodID preloadMid = env->GetStaticMethodID(tClass, "preload", "()V");
    if (preloadMid && !env->ExceptionCheck())
        env->CallStaticVoidMethod(tClass, preloadMid);
    if (env->ExceptionCheck()) env->ExceptionClear();

    // Create HookTransformer instance and get transform() method ID.
    jmethodID ctor = env->GetMethodID(tClass, "<init>", "()V");
    jobject transformer = env->NewObject(tClass, ctor);
    if (!transformer || env->ExceptionCheck()) { env->ExceptionClear(); jvm->DetachCurrentThread(); return 1; }
    transformer = env->NewGlobalRef(transformer);

    jmethodID transformMid = env->GetMethodID(tClass, "transform",
        "(Ljava/lang/ClassLoader;Ljava/lang/String;"
        "Ljava/lang/Class;Ljava/security/ProtectionDomain;[B)[B");
    if (!transformMid || env->ExceptionCheck()) { env->ExceptionClear(); jvm->DetachCurrentThread(); return 1; }

    // Resolve target class jclass handles via ObfNames.
    jclass obf = env->FindClass("com/modloader/ObfNames");
    if (!obf || env->ExceptionCheck()) { env->ExceptionClear(); jvm->DetachCurrentThread(); return 1; }

    std::vector<jclass> targets;
    const char *ownerFields[] = {"onTick_OWNER", "onHud_OWNER", "onKey_OWNER", nullptr};
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
    if (targets.empty()) { jvm->DetachCurrentThread(); return 1; }

    // ── PHASE 1: capture original bytecode (pure C callback, no Java) ────────
    g_phase = 1;
    s_jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    s_jvmti->RetransformClasses((jint)targets.size(), targets.data());
    s_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    g_phase = 0;
    if (env->ExceptionCheck()) env->ExceptionClear();

    // ── Transform on agent thread (no JVMTI locks held) ──────────────────────
    for (auto &kv : g_captured) {
        const std::string &name  = kv.first;
        const auto        &bytes = kv.second;
        if (bytes.empty()) continue;

        jbyteArray inArr = env->NewByteArray((jint)bytes.size());
        if (!inArr) continue;
        env->SetByteArrayRegion(inArr, 0, (jint)bytes.size(), (const jbyte *)bytes.data());
        jstring nameStr = env->NewStringUTF(name.c_str());

        jbyteArray result = (jbyteArray)env->CallObjectMethod(
            transformer, transformMid,
            nullptr, nameStr, nullptr, nullptr, inArr);

        if (!env->ExceptionCheck() && result) {
            jint len = env->GetArrayLength(result);
            std::vector<unsigned char> out(len);
            env->GetByteArrayRegion(result, 0, len, (jbyte *)out.data());
            g_transformed[name] = std::move(out);
            env->DeleteLocalRef(result);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(inArr);
        env->DeleteLocalRef(nameStr);
    }

    // ── PHASE 2: retransform again, returning pre-computed bytes (pure C) ────
    g_phase = 2;
    s_jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    s_jvmti->RetransformClasses((jint)targets.size(), targets.data());
    s_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    g_phase = 0;
    if (env->ExceptionCheck()) env->ExceptionClear();

    for (auto c : targets) env->DeleteGlobalRef(c);

    // Start command listener and enable hooks.
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
