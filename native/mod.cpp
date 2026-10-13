/*
 * mod.cpp — Overlay DLL injected into javaw.exe.
 * Creates a transparent topmost window over Minecraft,
 * renders the module list and player coords via GDI,
 * and toggles features by writing to GameState.pendingToggle via JNI.
 *
 * INSERT  = open/close overlay GUI
 * Arrow keys + Enter = navigate and toggle modules
 */

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <jni.h>
#include <string>
#include <vector>
#include <sstream>

// ── Global state ────────────────────────────────────────────────────────────────────
static HINSTANCE  g_hInst     = nullptr;
static HWND       g_overlay   = nullptr;  // our transparent window
static HWND       g_mcWindow  = nullptr;  // Minecraft GLFW window
static JavaVM*    g_jvm       = nullptr;
static jclass     g_gsClass   = nullptr;  // GameState class
static bool       g_guiOpen   = false;
static bool       g_running   = true;
static int        g_selected  = 0;

struct ModEntry { std::string name; bool enabled; };
static std::vector<ModEntry> g_modules;

// ── JNI helpers ────────────────────────────────────────────────────────────────────
static JNIEnv* GetEnv() {
    JNIEnv* env = nullptr;
    if (g_jvm) g_jvm->AttachCurrentThreadAsDaemon((void**)&env, nullptr);
    return env;
}

static std::string GetStaticStr(JNIEnv* env, jclass cls, const char* field) {
    jfieldID fid = env->GetStaticFieldID(cls, field, "Ljava/lang/String;");
    if (!fid) return "";
    jstring js = (jstring)env->GetStaticObjectField(cls, fid);
    if (!js) return "";
    const char* c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    env->DeleteLocalRef(js);
    return s;
}

static void SetStaticStr(JNIEnv* env, jclass cls, const char* field, const char* value) {
    jfieldID fid = env->GetStaticFieldID(cls, field, "Ljava/lang/String;");
    if (!fid) return;
    jstring js = env->NewStringUTF(value);
    env->SetStaticObjectField(cls, fid, js);
    env->DeleteLocalRef(js);
}

static double GetStaticDouble(JNIEnv* env, jclass cls, const char* field) {
    jfieldID fid = env->GetStaticFieldID(cls, field, "D");
    if (!fid) return 0.0;
    return env->GetStaticDoubleField(cls, fid);
}

// ── Parse module states "Name:1,Name:0,..." ──────────────────────────────────────────
static void ParseModules(const std::string& s) {
    g_modules.clear();
    std::stringstream ss(s);
    std::string tok;
    while (std::getline(ss, tok, ',')) {
        size_t colon = tok.find(':');
        if (colon == std::string::npos) continue;
        ModEntry e;
        e.name    = tok.substr(0, colon);
        e.enabled = tok.substr(colon + 1) == "1";
        g_modules.push_back(e);
    }
    if (g_selected >= (int)g_modules.size()) g_selected = 0;
}

// ── GDI rendering ────────────────────────────────────────────────────────────────────
static COLORREF COL_BG      = RGB(10, 10, 20);
static COLORREF COL_BORDER  = RGB(80, 120, 255);
static COLORREF COL_ON      = RGB(80, 255, 100);
static COLORREF COL_OFF     = RGB(180, 180, 180);
static COLORREF COL_SEL     = RGB(255, 210, 50);
static COLORREF COL_TITLE   = RGB(255, 255, 255);
static COLORREF COL_COORD   = RGB(200, 200, 255);

static void DrawRoundRect(HDC hdc, int x, int y, int w, int h, int r, COLORREF fill, COLORREF border) {
    HPEN   pen  = CreatePen(PS_SOLID, 1, border);
    HBRUSH brs  = CreateSolidBrush(fill);
    HPEN   oldP = (HPEN)  SelectObject(hdc, pen);
    HBRUSH oldB = (HBRUSH)SelectObject(hdc, brs);
    RoundRect(hdc, x, y, x + w, y + h, r, r);
    SelectObject(hdc, oldP);
    SelectObject(hdc, oldB);
    DeleteObject(pen);
    DeleteObject(brs);
}

static void DrawText_(HDC hdc, const char* txt, int x, int y, COLORREF col) {
    SetTextColor(hdc, col);
    TextOutA(hdc, x, y, txt, (int)strlen(txt));
}

static void RenderOverlay(HDC hdc, int W, int H,
                          double px, double py, double pz,
                          bool guiOpen) {
    // Always-visible: enabled modules strip on right side
    if (!g_modules.empty()) {
        int rx = W - 130, ry = 6;
        for (auto& m : g_modules) {
            if (!m.enabled) continue;
            DrawText_(hdc, m.name.c_str(), rx, ry, COL_ON);
            ry += 16;
        }
    }

    // Coordinates top-left
    {
        char buf[128];
        snprintf(buf, sizeof(buf), "X:%.1f Y:%.1f Z:%.1f", px, py, pz);
        DrawText_(hdc, buf, 4, 4, COL_COORD);
    }

    if (!guiOpen) return;

    // GUI panel
    int pw = 180, ph = 30 + (int)g_modules.size() * 22 + 8;
    int px2 = (W - pw) / 2, py2 = (H - ph) / 2;

    DrawRoundRect(hdc, px2, py2, pw, ph, 8, COL_BG, COL_BORDER);

    // Title
    DrawText_(hdc, "  SYSTEM CLIENT", px2 + 6, py2 + 6, COL_TITLE);

    int iy = py2 + 26;
    for (int i = 0; i < (int)g_modules.size(); i++) {
        auto& m = g_modules[i];
        bool sel = (i == g_selected);
        char buf[64];
        snprintf(buf, sizeof(buf), "  %s [%s]", m.name.c_str(), m.enabled ? "ON" : "OFF");
        COLORREF col = sel ? COL_SEL : (m.enabled ? COL_ON : COL_OFF);
        if (sel) DrawRoundRect(hdc, px2 + 2, iy - 1, pw - 4, 20, 4, RGB(30, 30, 60), COL_BORDER);
        DrawText_(hdc, buf, px2 + 6, iy + 1, col);
        iy += 22;
    }
}

// ── Overlay window proc ───────────────────────────────────────────────────────────────────
static LRESULT CALLBACK OverlayProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_PAINT: {
        RECT rc; GetClientRect(hwnd, &rc);
        int W = rc.right, H = rc.bottom;

        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd, &ps);

        // Double-buffer
        HDC memDC = CreateCompatibleDC(hdc);
        HBITMAP bmp = CreateCompatibleBitmap(hdc, W, H);
        HBITMAP old = (HBITMAP)SelectObject(memDC, bmp);

        BLENDFUNCTION bf = {AC_SRC_OVER, 0, 255, AC_SRC_ALPHA};
        PatBlt(memDC, 0, 0, W, H, BLACKNESS);

        HFONT font = CreateFontA(14, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
                                 DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                                 CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, "Consolas");
        HFONT oldFont = (HFONT)SelectObject(memDC, font);
        SetBkMode(memDC, TRANSPARENT);

        double px = 0, py = 0, pz = 0;
        JNIEnv* env = GetEnv();
        if (env && g_gsClass) {
            px = GetStaticDouble(env, g_gsClass, "playerX");
            py = GetStaticDouble(env, g_gsClass, "playerY");
            pz = GetStaticDouble(env, g_gsClass, "playerZ");
            std::string states = GetStaticStr(env, g_gsClass, "moduleStates");
            if (!states.empty()) ParseModules(states);
        }

        RenderOverlay(memDC, W, H, px, py, pz, g_guiOpen);

        POINT ptSrc = {0, 0};
        SIZE  szWnd = {W, H};
        POINT origin = {0, 0};
        ClientToScreen(hwnd, &origin);
        UpdateLayeredWindow(hwnd, nullptr, &origin, &szWnd, memDC, &ptSrc, 0, &bf, ULW_ALPHA);

        SelectObject(memDC, oldFont);
        DeleteObject(font);
        SelectObject(memDC, old);
        DeleteObject(bmp);
        DeleteDC(memDC);
        EndPaint(hwnd, &ps);
        return 0;
    }
    case WM_DESTROY:
        return 0;
    default:
        return DefWindowProcA(hwnd, msg, wp, lp);
    }
}

// ── Find Minecraft GLFW window ────────────────────────────────────────────────────────
static HWND FindMcWindow() {
    HWND h = FindWindowA("GLFW30", nullptr);
    if (!h) h = FindWindowA(nullptr, "Minecraft 1.21.11");
    return h;
}

static void UpdateOverlayGeometry() {
    if (!g_mcWindow || !g_overlay) return;
    RECT rc;
    GetClientRect(g_mcWindow, &rc);
    POINT pt = {rc.left, rc.top};
    ClientToScreen(g_mcWindow, &pt);
    SetWindowPos(g_overlay, HWND_TOPMOST,
                 pt.x, pt.y, rc.right - rc.left, rc.bottom - rc.top,
                 SWP_NOACTIVATE);
}

static bool CreateOverlay() {
    WNDCLASSEXA wc = {};
    wc.cbSize        = sizeof(wc);
    wc.lpfnWndProc   = OverlayProc;
    wc.hInstance     = g_hInst;
    wc.lpszClassName = "SC_Overlay";
    wc.hCursor       = LoadCursor(nullptr, IDC_ARROW);
    RegisterClassExA(&wc);

    RECT rc = {0, 0, 1920, 1080};
    if (g_mcWindow) {
        GetClientRect(g_mcWindow, &rc);
        POINT pt = {0, 0};
        ClientToScreen(g_mcWindow, &pt);
        rc.left = pt.x; rc.top = pt.y;
        rc.right += pt.x; rc.bottom += pt.y;
    }

    g_overlay = CreateWindowExA(
        WS_EX_TOPMOST | WS_EX_TRANSPARENT | WS_EX_LAYERED | WS_EX_NOACTIVATE,
        "SC_Overlay", "",
        WS_POPUP,
        rc.left, rc.top, rc.right - rc.left, rc.bottom - rc.top,
        nullptr, nullptr, g_hInst, nullptr);

    if (!g_overlay) return false;
    SetLayeredWindowAttributes(g_overlay, 0, 0, LWA_ALPHA);
    ShowWindow(g_overlay, SW_SHOW);
    return true;
}

static void RequestToggle(const std::string& name) {
    JNIEnv* env = GetEnv();
    if (!env || !g_gsClass) return;
    SetStaticStr(env, g_gsClass, "pendingToggle", name.c_str());
}

static void HandleKey(int vk) {
    if (vk == VK_INSERT) {
        g_guiOpen = !g_guiOpen;
        LONG_PTR ex = GetWindowLongPtrA(g_overlay, GWL_EXSTYLE);
        if (g_guiOpen) ex &= ~WS_EX_TRANSPARENT;
        else           ex |=  WS_EX_TRANSPARENT;
        SetWindowLongPtrA(g_overlay, GWL_EXSTYLE, ex);
        InvalidateRect(g_overlay, nullptr, FALSE);
        return;
    }
    if (!g_guiOpen || g_modules.empty()) return;
    if (vk == VK_DOWN) {
        g_selected = (g_selected + 1) % (int)g_modules.size();
        InvalidateRect(g_overlay, nullptr, FALSE);
    } else if (vk == VK_UP) {
        g_selected = (g_selected - 1 + (int)g_modules.size()) % (int)g_modules.size();
        InvalidateRect(g_overlay, nullptr, FALSE);
    } else if (vk == VK_RETURN || vk == VK_SPACE) {
        if (g_selected < (int)g_modules.size())
            RequestToggle(g_modules[g_selected].name);
        InvalidateRect(g_overlay, nullptr, FALSE);
    }
}

static DWORD WINAPI ModThread(LPVOID) {
    for (int i = 0; i < 200; i++) {
        jsize count = 0;
        JNI_GetCreatedJavaVMs(&g_jvm, 1, &count);
        if (count > 0) break;
        Sleep(200);
    }
    if (!g_jvm) return 1;

    JNIEnv* env = GetEnv();
    if (!env) return 1;

    for (int i = 0; i < 100; i++) {
        g_gsClass = env->FindClass("com/modloader/GameState");
        if (g_gsClass) {
            g_gsClass = (jclass)env->NewGlobalRef(g_gsClass);
            break;
        }
        env->ExceptionClear();
        Sleep(500);
    }
    if (!g_gsClass) return 1;

    for (int i = 0; i < 60; i++) {
        g_mcWindow = FindMcWindow();
        if (g_mcWindow) break;
        Sleep(500);
    }

    CreateOverlay();
    UpdateOverlayGeometry();

    bool keyState[256] = {};

    while (g_running && g_overlay) {
        if (g_mcWindow) UpdateOverlayGeometry();

        for (int vk : {VK_INSERT, VK_UP, VK_DOWN, VK_RETURN, VK_SPACE}) {
            bool down = (GetAsyncKeyState(vk) & 0x8000) != 0;
            if (down && !keyState[vk]) HandleKey(vk);
            keyState[vk] = down;
        }

        InvalidateRect(g_overlay, nullptr, FALSE);
        UpdateWindow(g_overlay);

        MSG msg;
        while (PeekMessageA(&msg, nullptr, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) { g_running = false; break; }
            TranslateMessage(&msg);
            DispatchMessageA(&msg);
        }

        Sleep(50);
    }

    if (g_overlay) { DestroyWindow(g_overlay); g_overlay = nullptr; }
    return 0;
}

BOOL WINAPI DllMain(HINSTANCE hInst, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        g_hInst = hInst;
        DisableThreadLibraryCalls(hInst);
        CreateThread(nullptr, 0, ModThread, nullptr, 0, nullptr);
    } else if (reason == DLL_PROCESS_DETACH) {
        g_running = false;
    }
    return TRUE;
}
