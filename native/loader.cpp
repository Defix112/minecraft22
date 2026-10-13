#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <winhttp.h>
#include <shlwapi.h>
#include <stdio.h>
#include <string>

// ── Console helpers ──────────────────────────────────────────────────────────
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
        INPUT_RECORD ir; DWORD n = 0;
        ReadConsoleInputA(in, &ir, 1, &n);
        if (ir.EventType == KEY_EVENT && ir.Event.KeyEvent.bKeyDown) {
            char c = ir.Event.KeyEvent.uChar.AsciiChar;
            for (const char *p = allowed; *p; p++)
                if (c == *p) return c;
        }
    }
}

// ── Find Minecraft PID ───────────────────────────────────────────────────────
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

// ── Download via WinHTTP (follows GitHub redirects) ──────────────────────────
static bool Download(const std::wstring &url, const std::wstring &outPath, int depth = 0) {
    if (depth > 5) return false;

    URL_COMPONENTSW uc = { sizeof(uc) };
    wchar_t host[512] = {}, path[2048] = {};
    uc.lpszHostName = host; uc.dwHostNameLength = 512;
    uc.lpszUrlPath  = path; uc.dwUrlPathLength  = 2048;
    if (!WinHttpCrackUrl(url.c_str(), 0, 0, &uc)) return false;

    HINTERNET ses = WinHttpOpen(L"SystemClient/7.0",
        WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, NULL, NULL, 0);
    HINTERNET con = WinHttpConnect(ses, host, uc.nPort, 0);
    DWORD flags = (uc.nScheme == INTERNET_SCHEME_HTTPS) ? WINHTTP_FLAG_SECURE : 0;
    HINTERNET req = WinHttpOpenRequest(con, L"GET", path, NULL, NULL, NULL, flags);

    WinHttpSendRequest(req, NULL, 0, NULL, 0, 0, 0);
    WinHttpReceiveResponse(req, NULL);

    wchar_t location[2048] = {}; DWORD sz = sizeof(location);
    if (WinHttpQueryHeaders(req, WINHTTP_QUERY_LOCATION, NULL, location, &sz, NULL)) {
        WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
        return Download(location, outPath, depth + 1);
    }

    DWORD status = 0; sz = sizeof(status);
    WinHttpQueryHeaders(req, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
        NULL, &status, &sz, NULL);
    if (status != 200) {
        WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
        return false;
    }

    HANDLE f = CreateFileW(outPath.c_str(), GENERIC_WRITE, 0, NULL,
        CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE) {
        WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
        return false;
    }

    char buf[65536]; DWORD rd = 0, wr = 0;
    while (WinHttpReadData(req, buf, sizeof(buf), &rd) && rd > 0)
        WriteFile(f, buf, rd, &wr, NULL);

    CloseHandle(f);
    WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
    return true;
}

// ── DLL injection via CreateRemoteThread + LoadLibraryA ──────────────────────
static bool InjectDll(DWORD pid, const std::wstring &dllPath) {
    wchar_t absPath[MAX_PATH];
    GetFullPathNameW(dllPath.c_str(), MAX_PATH, absPath, nullptr);

    char ansiPath[MAX_PATH];
    WideCharToMultiByte(CP_ACP, 0, absPath, -1, ansiPath, MAX_PATH, nullptr, nullptr);
    size_t len = strlen(ansiPath) + 1;

    HANDLE hProc = OpenProcess(
        PROCESS_CREATE_THREAD | PROCESS_VM_OPERATION |
        PROCESS_VM_WRITE     | PROCESS_VM_READ, FALSE, pid);
    if (!hProc) return false;

    void *mem = VirtualAllocEx(hProc, nullptr, len, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!mem) { CloseHandle(hProc); return false; }

    WriteProcessMemory(hProc, mem, ansiPath, len, nullptr);

    FARPROC loadLib = GetProcAddress(GetModuleHandleA("kernel32.dll"), "LoadLibraryA");
    HANDLE ht = CreateRemoteThread(hProc, nullptr, 0,
        (LPTHREAD_START_ROUTINE)loadLib, mem, 0, nullptr);
    if (!ht) {
        VirtualFreeEx(hProc, mem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return false;
    }

    WaitForSingleObject(ht, 30000);
    DWORD exitCode = 0;
    GetExitCodeThread(ht, &exitCode);

    VirtualFreeEx(hProc, mem, 0, MEM_RELEASE);
    CloseHandle(ht);
    CloseHandle(hProc);
    return exitCode != 0;  // LoadLibraryA returns HMODULE; 0 = failure
}

// ── Temp file helpers ────────────────────────────────────────────────────────
static std::string ReadTempFile(const wchar_t *name) {
    wchar_t tmp[MAX_PATH], p[MAX_PATH];
    GetTempPathW(MAX_PATH, tmp);
    swprintf_s(p, L"%s%s", tmp, name);
    HANDLE f = CreateFileW(p, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
    if (f == INVALID_HANDLE_VALUE) return {};
    char buf[512] = {}; DWORD rd;
    ReadFile(f, buf, 511, &rd, NULL);
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

// ── Banner ───────────────────────────────────────────────────────────────────
static void Banner() {
    Green();
    printf("\n");
    printf("  ==========================================\n");
    printf("       SYSTEM CLIENT  v7.0  [DLL]\n");
    printf("   Minecraft 1.21.11  |  RIGHT SHIFT = GUI\n");
    printf("  ==========================================\n");
    printf("\n");
    White();
}

// ── Entry point ──────────────────────────────────────────────────────────────
int main() {
    SetConsoleTitleA("System Client v7.0");

    wchar_t exePath[MAX_PATH];
    GetModuleFileNameW(NULL, exePath, MAX_PATH);
    wchar_t *sl = wcsrchr(exePath, L'\\');
    std::wstring exeDir = sl ? std::wstring(exePath, sl + 1) : L"";
    std::wstring agentJar = exeDir + L"agent.jar";
    std::wstring agentDll = exeDir + L"agent.dll";

    const std::wstring BASE_URL =
        L"https://github.com/defix112/minecraft22/releases/latest/download/";

    DWORD mcPid   = 0;
    int   step    = 0;
    bool  injected = false;

    while (true) {
        Cls();
        Banner();

        if (!injected) {
            if (step == 0) {
                printf("   Minecraft : not found\n\n");
                Green(); printf("   [1]  Find Minecraft\n"); White();
            } else {
                printf("   Minecraft : PID %lu\n\n", mcPid);
                Green(); printf("   [1]  Download + Inject DLL\n"); White();
            }
            Red(); printf("   [0]  Exit\n"); White();
            printf("  ==========================================\n\n");

            char k = WaitKey("10");
            if (k == '0') break;

            // ── [1] first press: find MC ────────────────────────────────────
            if (step == 0) {
                Cls(); Banner();
                printf("   Searching for Minecraft...\n\n");
                mcPid = FindMinecraft();
                if (!mcPid) {
                    Red();
                    printf("   [-] Minecraft not found.\n");
                    printf("   Start Minecraft 1.21.11 and join a world!\n\n");
                    White();
                    Sleep(3000);
                    continue;
                }
                Green();
                printf("   [+] Found PID: %lu\n\n", mcPid);
                White();
                step = 1;
                Sleep(1200);
                continue;
            }

            // ── [1] second press: download + DLL inject ─────────────────────
            Cls(); Banner();

            // Download agent.jar
            if (GetFileAttributesW(agentJar.c_str()) == INVALID_FILE_ATTRIBUTES) {
                printf("   Downloading agent.jar...\n\n");
                if (!Download(BASE_URL + L"agent.jar", agentJar)) {
                    Red(); printf("   [-] Download failed (agent.jar).\n\n"); White();
                    Sleep(4000); continue;
                }
                Green(); printf("   [+] agent.jar OK\n\n"); White();
            } else {
                Green(); printf("   [+] agent.jar present\n\n"); White();
            }

            // Download agent.dll
            if (GetFileAttributesW(agentDll.c_str()) == INVALID_FILE_ATTRIBUTES) {
                printf("   Downloading agent.dll...\n\n");
                if (!Download(BASE_URL + L"agent.dll", agentDll)) {
                    Red(); printf("   [-] Download failed (agent.dll).\n\n"); White();
                    Sleep(4000); continue;
                }
                Green(); printf("   [+] agent.dll OK\n\n"); White();
            } else {
                Green(); printf("   [+] agent.dll present\n\n"); White();
            }

            // DLL injection
            printf("   Injecting DLL into PID %lu...\n\n", mcPid);
            DeleteTempFile(L"clickgui_log.txt");
            DeleteTempFile(L"clickgui_status.txt");

            if (!InjectDll(mcPid, agentDll)) {
                Red();
                printf("   [-] Injection FAILED!\n");
                printf("   Run loader.exe as Administrator.\n\n");
                White();
                Sleep(5000);
                continue;
            }

            Green();
            printf("   [+] DLL injected! Agent is loading...\n");
            White();

            // Wait for agent to write status (up to 15s)
            for (int i = 0; i < 30; i++) {
                Sleep(500);
                std::string st = ReadTempFile(L"clickgui_status.txt");
                if (!st.empty()) break;
            }

            injected = true;

        } else {
            // ── Injected menu ───────────────────────────────────────────────
            Green();
            printf("        ** INJECTED  (DLL) **\n\n");
            White();
            printf("   Press RIGHT SHIFT in game to open GUI.\n");
            printf("   Works in main menu and in-world.\n\n");

            std::string status = ReadTempFile(L"clickgui_status.txt");
            if (!status.empty()) {
                printf("   Status: "); Green(); printf("%s\n\n", status.c_str()); White();
            } else {
                Yellow(); printf("   Status: agent loading...\n\n"); White();
            }

            Red();    printf("   [0]  PANIC - remove mod\n"); White();
            Yellow(); printf("   [9]  Close (mod stays active)\n"); White();
            printf("  ==========================================\n\n");

            char k = WaitKey("09");
            if (k == '9') break;

            // ── PANIC ───────────────────────────────────────────────────────
            Cls(); Banner();
            Red();
            printf("   PANIC - Removing all traces...\n\n");
            White();

            WriteTempFile(L"clickgui_cmd.txt", "0");
            printf("   [1/4] Disable signal sent to agent...\n");
            Sleep(2000);

            DeleteFileW(agentDll.c_str());
            printf("   [2/4] agent.dll deleted\n");

            DeleteFileW(agentJar.c_str());
            printf("   [3/4] agent.jar deleted\n");

            DeleteTempFile(L"clickgui_cmd.txt");
            DeleteTempFile(L"clickgui_log.txt");
            DeleteTempFile(L"clickgui_status.txt");
            DeleteTempFile(L"clickgui_pause.txt");
            printf("   [4/4] Temp files cleaned\n\n");

            Green();
            printf("   All traces removed. Game restored.\n");
            printf("   loader.exe kept - run again to reinstall.\n\n");
            White();
            Sleep(3000);
            break;
        }
    }
    return 0;
}
