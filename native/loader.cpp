#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <winhttp.h>
#include <shlwapi.h>
#include <stdio.h>
#include <string>


static std::wstring g_agentJar;
static std::wstring g_modDll;
static std::wstring g_selfPath;
static const std::wstring BASE_URL =
    L"https://github.com/defix112/minecraft22/releases/latest/download/";

// Self-registration: .mui -> exe
static void SelfRegister() {
    HKEY hk;
    if (RegCreateKeyExW(HKEY_CLASSES_ROOT, L".mui", 0, NULL, 0,
                        KEY_SET_VALUE, NULL, &hk, NULL) == ERROR_SUCCESS) {
        RegSetValueExW(hk, NULL, 0, REG_SZ, (BYTE*)L"muifile",
                       (DWORD)(wcslen(L"muifile")+1)*sizeof(wchar_t));
        RegCloseKey(hk);
    }
    if (RegCreateKeyExW(HKEY_CLASSES_ROOT, L"muifile\\shell\\open\\command", 0,
                        NULL, 0, KEY_SET_VALUE, NULL, &hk, NULL) == ERROR_SUCCESS) {
        std::wstring cmd = L"\"" + g_selfPath + L"\"";
        RegSetValueExW(hk, NULL, 0, REG_SZ, (BYTE*)cmd.c_str(),
                       (DWORD)(cmd.size()+1)*sizeof(wchar_t));
        RegCloseKey(hk);
    }
}

static bool IsRegistered() {
    HKEY hk;
    bool ok = RegOpenKeyExW(HKEY_CLASSES_ROOT, L".mui", 0, KEY_READ, &hk) == ERROR_SUCCESS;
    if (ok) RegCloseKey(hk);
    return ok;
}

// ── Console helpers ──────────────────────────────────────────────────────────────────
static void Cls()    { system("cls"); }
static void Green()  { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), FOREGROUND_GREEN|FOREGROUND_INTENSITY); }
static void White()  { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), FOREGROUND_RED|FOREGROUND_GREEN|FOREGROUND_BLUE); }
static void Red()    { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), FOREGROUND_RED|FOREGROUND_INTENSITY); }
static void Yellow() { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), FOREGROUND_RED|FOREGROUND_GREEN|FOREGROUND_INTENSITY); }

static char WaitKey(const char *allowed) {
    HANDLE in = GetStdHandle(STD_INPUT_HANDLE);
    SetConsoleMode(in, ENABLE_PROCESSED_INPUT);
    FlushConsoleInputBuffer(in);
    while (true) {
        MSG msg;
        while (PeekMessageW(&msg, NULL, 0, 0, PM_REMOVE)) DispatchMessageW(&msg);
        INPUT_RECORD ir; DWORD n = 0;
        if (WaitForSingleObject(in, 50) == WAIT_OBJECT_0) {
            ReadConsoleInputA(in, &ir, 1, &n);
            if (ir.EventType == KEY_EVENT && ir.Event.KeyEvent.bKeyDown) {
                char c = ir.Event.KeyEvent.uChar.AsciiChar;
                for (const char *p = allowed; *p; p++)
                    if (c == *p) return c;
            }
        }
    }
}

// ── Find Minecraft PID ───────────────────────────────────────────────────────────────────
static DWORD FindMinecraft() {
    const wchar_t *names[] = { L"javaw.exe", L"java.exe", nullptr };
    for (int i = 0; names[i]; i++) {
        HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        PROCESSENTRY32W pe = { sizeof(pe) };
        if (Process32FirstW(snap, &pe)) {
            do {
                if (_wcsicmp(pe.szExeFile, names[i]) == 0) {
                    CloseHandle(snap);
                    return pe.th32ProcessID;
                }
            } while (Process32NextW(snap, &pe));
        }
        CloseHandle(snap);
    }
    return 0;
}

static std::wstring FindMinecraftJava(DWORD pid) {
    HANDLE hProc = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (!hProc) return L"";
    wchar_t buf[MAX_PATH * 2] = {};
    DWORD size = MAX_PATH * 2;
    BOOL ok = QueryFullProcessImageNameW(hProc, 0, buf, &size);
    CloseHandle(hProc);
    if (!ok) return L"";
    wchar_t *last = wcsrchr(buf, L'\\');
    if (last) wcscpy_s(last + 1, (sizeof(buf)/sizeof(wchar_t)) - (last+1-buf), L"java.exe");
    if (GetFileAttributesW(buf) == INVALID_FILE_ATTRIBUTES) {
        HANDLE h2 = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
        if (h2) { QueryFullProcessImageNameW(h2, 0, buf, &size); CloseHandle(h2); }
    }
    return buf;
}

// ── Download via WinHTTP ───────────────────────────────────────────────────────────────────
static bool Download(const std::wstring &url, const std::wstring &outPath, int depth = 0) {
    if (depth > 5) return false;
    URL_COMPONENTSW uc = { sizeof(uc) };
    wchar_t host[512]={}, path[2048]={};
    uc.lpszHostName=host; uc.dwHostNameLength=512;
    uc.lpszUrlPath=path;  uc.dwUrlPathLength=2048;
    if (!WinHttpCrackUrl(url.c_str(), 0, 0, &uc)) return false;
    HINTERNET ses = WinHttpOpen(L"SystemClient/8.0", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, NULL, NULL, 0);
    HINTERNET con = WinHttpConnect(ses, host, uc.nPort, 0);
    DWORD flags = (uc.nScheme == INTERNET_SCHEME_HTTPS) ? WINHTTP_FLAG_SECURE : 0;
    HINTERNET req = WinHttpOpenRequest(con, L"GET", path, NULL, NULL, NULL, flags);
    WinHttpSendRequest(req, NULL, 0, NULL, 0, 0, 0);
    WinHttpReceiveResponse(req, NULL);
    wchar_t location[2048]={}; DWORD sz=sizeof(location);
    if (WinHttpQueryHeaders(req, WINHTTP_QUERY_LOCATION, NULL, location, &sz, NULL)) {
        WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
        return Download(location, outPath, depth+1);
    }
    DWORD status=0; sz=sizeof(status);
    WinHttpQueryHeaders(req, WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER, NULL, &status, &sz, NULL);
    if (status != 200) {
        WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
        return false;
    }
    HANDLE f = CreateFileW(outPath.c_str(), GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE) {
        WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
        return false;
    }
    char buf[65536]; DWORD rd=0, wr=0;
    while (WinHttpReadData(req, buf, sizeof(buf), &rd) && rd > 0)
        WriteFile(f, buf, rd, &wr, NULL);
    CloseHandle(f);
    WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
    return true;
}

// ── DLL injection (LoadLibrary via CreateRemoteThread) ───────────────────────────────────────
static bool InjectDll(DWORD pid, const std::wstring& dllPath) {
    wchar_t absDll[MAX_PATH];
    GetFullPathNameW(dllPath.c_str(), MAX_PATH, absDll, nullptr);
    HANDLE proc = OpenProcess(PROCESS_ALL_ACCESS, FALSE, pid);
    if (!proc) return false;
    SIZE_T pathLen = (wcslen(absDll) + 1) * sizeof(wchar_t);
    LPVOID remote = VirtualAllocEx(proc, nullptr, pathLen, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remote) { CloseHandle(proc); return false; }
    WriteProcessMemory(proc, remote, absDll, pathLen, nullptr);
    HMODULE k32 = GetModuleHandleW(L"kernel32.dll");
    FARPROC loadLib = GetProcAddress(k32, "LoadLibraryW");
    HANDLE t = CreateRemoteThread(proc, nullptr, 0,
        (LPTHREAD_START_ROUTINE)loadLib, remote, 0, nullptr);
    if (!t) { VirtualFreeEx(proc, remote, 0, MEM_RELEASE); CloseHandle(proc); return false; }
    WaitForSingleObject(t, 10000);
    CloseHandle(t);
    VirtualFreeEx(proc, remote, 0, MEM_RELEASE);
    CloseHandle(proc);
    return true;
}

// ── Attach agent ────────────────────────────────────────────────────────────────────────
static bool AttachAgent(const std::wstring &javaExe, DWORD pid,
                        const std::wstring &agentJar, std::string &outLog) {
    wchar_t absJar[MAX_PATH];
    GetFullPathNameW(agentJar.c_str(), MAX_PATH, absJar, nullptr);
    std::wstring cmd = L"\"" + javaExe + L"\""
        L" --add-modules jdk.attach"
        L" -cp \"" + absJar + L"\""
        L" com.modloader.Injector "
        + std::to_wstring(pid)
        + L" \"" + absJar + L"\"";
    SECURITY_ATTRIBUTES sa = { sizeof(sa), NULL, TRUE };
    HANDLE pipeR, pipeW;
    CreatePipe(&pipeR, &pipeW, &sa, 0);
    SetHandleInformation(pipeR, HANDLE_FLAG_INHERIT, 0);
    STARTUPINFOW si = {};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = pipeW; si.hStdError = pipeW;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    PROCESS_INFORMATION pi = {};
    if (!CreateProcessW(nullptr, &cmd[0], nullptr, nullptr, TRUE,
                        CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        CloseHandle(pipeR); CloseHandle(pipeW); return false;
    }
    CloseHandle(pipeW);
    WaitForSingleObject(pi.hProcess, 30000);
    char buf[4096]={}; DWORD rd=0;
    ReadFile(pipeR, buf, sizeof(buf)-1, &rd, NULL);
    CloseHandle(pipeR);
    if (rd > 0) outLog = buf;
    DWORD exitCode=1;
    GetExitCodeProcess(pi.hProcess, &exitCode);
    CloseHandle(pi.hProcess); CloseHandle(pi.hThread);
    return exitCode == 0;
}

// ── Temp file helpers ────────────────────────────────────────────────────────────────────────
static std::string ReadTempFile(const wchar_t *name) {
    wchar_t tmp[MAX_PATH], p[MAX_PATH];
    GetTempPathW(MAX_PATH, tmp);
    swprintf_s(p, L"%s%s", tmp, name);
    HANDLE f = CreateFileW(p, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
    if (f == INVALID_HANDLE_VALUE) return {};
    char buf[8192]={}; DWORD rd;
    ReadFile(f, buf, sizeof(buf)-1, &rd, NULL);
    CloseHandle(f);
    return buf;
}
static void DeleteTempFile(const wchar_t *name) {
    wchar_t tmp[MAX_PATH], p[MAX_PATH];
    GetTempPathW(MAX_PATH, tmp);
    swprintf_s(p, L"%s%s", tmp, name);
    DeleteFileW(p);
}
static void WriteTempFile(const wchar_t *name, const char *data) {
    wchar_t tmp[MAX_PATH], p[MAX_PATH];
    GetTempPathW(MAX_PATH, tmp);
    swprintf_s(p, L"%s%s", tmp, name);
    HANDLE f = CreateFileW(p, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, 0, NULL);
    if (f != INVALID_HANDLE_VALUE) { DWORD w; WriteFile(f, data, (DWORD)strlen(data), &w, NULL); CloseHandle(f); }
}

// ── Banner ───────────────────────────────────────────────────────────────────────────────────
static void Banner() {
    Green();
    printf("\n");
    printf("  ==========================================\n");
    printf("       SYSTEM CLIENT  v8.0\n");
    printf("   Minecraft 1.21.11  |  INSERT = Overlay\n");
    printf("  ==========================================\n");
    printf("\n");
    White();
}

static void DoPanic(const std::wstring &agentJar) {
    Cls(); Banner();
    Red(); printf("   [2] PANIC - removing all traces...\n\n"); White();
    WriteTempFile(L"clickgui_cmd.txt", "0");
    printf("   [1/4] Disable signal sent to agent...\n");
    Sleep(2000);
    DeleteFileW(agentJar.c_str());
    DeleteFileW(g_modDll.c_str());
    printf("   [2/4] agent.jar + mod.dll deleted\n");
    DeleteTempFile(L"clickgui_cmd.txt");
    DeleteTempFile(L"clickgui_log.txt");
    DeleteTempFile(L"clickgui_status.txt");
    DeleteTempFile(L"clickgui_pause.txt");
    printf("   [3/4] Temp files cleaned\n");
    DeleteTempFile(L"clickgui_loader.txt");
    MoveFileExW(g_selfPath.c_str(), NULL, MOVEFILE_DELAY_UNTIL_REBOOT);
    printf("   [4/4] Loader scheduled for deletion on reboot\n\n");
    Green(); printf("   All traces removed.\n\n"); White();
    Sleep(3000);
}

// ── Main loader UI ──────────────────────────────────────────────────────────────────────────────────
static void RunLoader() {
    HWND con = GetConsoleWindow();
    if (!con) {
        AllocConsole();
        freopen("CONOUT$", "w", stdout);
        freopen("CONOUT$", "w", stderr);
        freopen("CONIN$",  "r", stdin);
        con = GetConsoleWindow();
    }
    ShowWindow(con, SW_SHOW);
    SetForegroundWindow(con);
    SetConsoleTitleA("System Client v8.0");

    bool injected = false;
    while (true) {
        Cls(); Banner();
        DWORD mcPid = FindMinecraft();
        if (!injected) {
            if (mcPid) {
                Green(); printf("   Minecraft : PID %lu\n\n", mcPid); White();
                Green(); printf("   [1]  Install & Inject mod\n"); White();
            } else {
                Red(); printf("   Minecraft : NOT FOUND\n\n"); White();
                Yellow(); printf("   Start Minecraft 1.21.11 first!\n\n"); White();
                Green(); printf("   [1]  Retry\n"); White();
            }
            Red();    printf("   [2]  Remove all mod files\n"); White();
            Yellow(); printf("   [0]  Close loader\n"); White();
            printf("  ==========================================\n\n");
            char k = WaitKey("120");
            if (k == '0') break;
            if (k == '2') { DoPanic(g_agentJar); continue; }
            if (!mcPid) continue;
            Cls(); Banner();
            std::wstring javaExe = FindMinecraftJava(mcPid);
            if (javaExe.empty()) {
                Red(); printf("   [-] Cannot find Minecraft's java.exe\n\n"); White();
                Sleep(4000); continue;
            }
            Green(); printf("   [+] java.exe: "); White();
            char jbuf[MAX_PATH*2]; WideCharToMultiByte(CP_ACP,0,javaExe.c_str(),-1,jbuf,sizeof(jbuf),0,0);
            printf("%s\n\n", jbuf);
            if (GetFileAttributesW(g_agentJar.c_str()) == INVALID_FILE_ATTRIBUTES) {
                printf("   Downloading agent.jar...\n");
                if (!Download(BASE_URL + L"agent.jar", g_agentJar)) {
                    Red(); printf("   [-] Download failed! Check internet.\n\n"); White();
                    Sleep(4000); continue;
                }
                Green(); printf("   [+] agent.jar downloaded\n\n"); White();
            } else {
                Green(); printf("   [+] agent.jar present\n\n"); White();
            }
            if (GetFileAttributesW(g_modDll.c_str()) == INVALID_FILE_ATTRIBUTES) {
                printf("   Downloading mod.dll...\n");
                if (!Download(BASE_URL + L"mod.dll", g_modDll))
                    { Yellow(); printf("   [!] mod.dll download failed (overlay won't load)\n\n"); White(); }
                else
                    { Green(); printf("   [+] mod.dll downloaded\n\n"); White(); }
            }
            printf("   Injecting into PID %lu...\n\n", mcPid);
            DeleteTempFile(L"clickgui_log.txt");
            DeleteTempFile(L"clickgui_status.txt");
            std::string attachLog;
            if (!AttachAgent(javaExe, mcPid, g_agentJar, attachLog)) {
                Red();
                printf("   [-] Injection FAILED!\n");
                if (!attachLog.empty()) printf("%s\n", attachLog.c_str());
                printf("\n   Run as Administrator and make sure you're in a world.\n\n");
                White();
                Sleep(6000); continue;
            }
            if (!attachLog.empty()) printf("%s\n", attachLog.c_str());
            printf("   Waiting for agent...\n");
            for (int i = 0; i < 20; i++) {
                Sleep(500);
                if (!ReadTempFile(L"clickgui_status.txt").empty()) break;
            }
            // Inject overlay DLL
            if (GetFileAttributesW(g_modDll.c_str()) != INVALID_FILE_ATTRIBUTES) {
                if (InjectDll(mcPid, g_modDll))
                    Green(); else Red();
                printf("   [+] Overlay DLL injected\n"); White();
            }
            injected = true;
        } else {
            std::string status = ReadTempFile(L"clickgui_status.txt");
            std::string log    = ReadTempFile(L"clickgui_log.txt");
            Green(); printf("        ** INJECTED **\n\n"); White();
            printf("   INSERT = overlay GUI (works over Minecraft)\n\n");
            if (!status.empty()) {
                if (status.find("ERROR") != std::string::npos) Red(); else Green();
                printf("   Status: %s\n\n", status.c_str()); White();
            }
            if (!log.empty()) {
                Yellow(); printf("   --- Log ---\n"); White();
                printf("%s\n", log.c_str());
            }
            Red();    printf("   [2]  Remove all traces (PANIC)\n"); White();
            Yellow(); printf("   [0]  Close (mod stays active)\n"); White();
            printf("  ==========================================\n\n");
            char k = WaitKey("20");
            if (k == '0') break;
            DoPanic(g_agentJar);
            injected = false;
        }
    }
    ShowWindow(GetConsoleWindow(), SW_HIDE);
}

// ── Entry point ───────────────────────────────────────────────────────────────────────────────────
int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int) {
    wchar_t exePath[MAX_PATH];
    GetModuleFileNameW(NULL, exePath, MAX_PATH);
    g_selfPath = exePath;
    wchar_t *sl = wcsrchr(exePath, L'\\');
    std::wstring exeDir = sl ? std::wstring(exePath, sl + 1) : L"";
    g_agentJar = exeDir + L"agent.jar";
    g_modDll   = exeDir + L"mod.dll";

    if (!IsRegistered()) {
        SelfRegister();
    }

    // Write own path for Panic cleanup (Java reads this to delete the .mui file)
    {
        int len = WideCharToMultiByte(CP_UTF8, 0, g_selfPath.c_str(), -1, nullptr, 0, nullptr, nullptr);
        std::string utf8path(len, '\0');
        WideCharToMultiByte(CP_UTF8, 0, g_selfPath.c_str(), -1, &utf8path[0], len, nullptr, nullptr);
        utf8path.resize(len - 1);
        WriteTempFile(L"clickgui_loader.txt", utf8path.c_str());
    }

    RunLoader();
    return 0;
}
