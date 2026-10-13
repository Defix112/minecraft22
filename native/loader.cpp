#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <winhttp.h>
#include <shlwapi.h>
#include <stdio.h>
#include <string>

// ── Console helpers ────────────────────────────────────────────
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

// ── Find Minecraft PID ───────────────────────────────────────────
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

// Get the full path to the java.exe that is running Minecraft.
// QueryFullProcessImageName returns e.g. C:\...\jre\bin\javaw.exe;
// we substitute java.exe in the same directory.
static std::wstring FindMinecraftJava(DWORD pid) {
    HANDLE hProc = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (!hProc) return L"";
    wchar_t buf[MAX_PATH * 2] = {};
    DWORD size = MAX_PATH * 2;
    BOOL ok = QueryFullProcessImageNameW(hProc, 0, buf, &size);
    CloseHandle(hProc);
    if (!ok) return L"";
    // Replace the filename part with java.exe
    wchar_t *last = wcsrchr(buf, L'\\');
    if (last) wcscpy_s(last + 1, (sizeof(buf) / sizeof(wchar_t)) - (last + 1 - buf), L"java.exe");
    // If java.exe doesn't exist there, fall back to whatever was found (javaw.exe)
    if (GetFileAttributesW(buf) == INVALID_FILE_ATTRIBUTES) {
        QueryFullProcessImageNameW(
            OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid), 0, buf, &size);
    }
    return buf;
}

// ── Download via WinHTTP (follows GitHub redirects) ──────────────────────
static bool Download(const std::wstring &url, const std::wstring &outPath, int depth = 0) {
    if (depth > 5) return false;

    URL_COMPONENTSW uc = { sizeof(uc) };
    wchar_t host[512] = {}, path[2048] = {};
    uc.lpszHostName = host; uc.dwHostNameLength = 512;
    uc.lpszUrlPath  = path; uc.dwUrlPathLength  = 2048;
    if (!WinHttpCrackUrl(url.c_str(), 0, 0, &uc)) return false;

    HINTERNET ses = WinHttpOpen(L"SystemClient/8.0",
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

// ── Launch Minecraft's own java.exe to attach the agent ───────────────────
// Runs: java.exe --add-modules jdk.attach -cp agent.jar com.modloader.Attach <pid> <jar>
static bool AttachAgent(const std::wstring &javaExe, DWORD pid, const std::wstring &agentJar) {
    wchar_t absJar[MAX_PATH];
    GetFullPathNameW(agentJar.c_str(), MAX_PATH, absJar, nullptr);

    std::wstring cmd = L"\"" + javaExe + L"\""
        L" --add-modules jdk.attach"
        L" -cp \"" + absJar + L"\""
        L" com.modloader.Attach "
        + std::to_wstring(pid)
        + L" \"" + absJar + L"\"";

    STARTUPINFOW si = {};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi = {};

    if (!CreateProcessW(nullptr, &cmd[0], nullptr, nullptr, FALSE,
                        CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi))
        return false;

    WaitForSingleObject(pi.hProcess, 30000);
    DWORD exitCode = 1;
    GetExitCodeProcess(pi.hProcess, &exitCode);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return exitCode == 0;
}

// ── Temp file helpers ────────────────────────────────────────────────
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

// ── Banner ──────────────────────────────────────────────────────────────────
static void Banner() {
    Green();
    printf("\n");
    printf("  ==========================================\n");
    printf("       SYSTEM CLIENT  v8.0\n");
    printf("   Minecraft 1.21.11  |  RIGHT SHIFT = GUI\n");
    printf("  ==========================================\n");
    printf("\n");
    White();
}

// ── Entry point ───────────────────────────────────────────────────────────
int main() {
    SetConsoleTitleA("System Client v8.0");

    wchar_t exePath[MAX_PATH];
    GetModuleFileNameW(NULL, exePath, MAX_PATH);
    wchar_t *sl = wcsrchr(exePath, L'\\');
    std::wstring exeDir  = sl ? std::wstring(exePath, sl + 1) : L"";
    std::wstring agentJar = exeDir + L"agent.jar";

    const std::wstring BASE_URL =
        L"https://github.com/defix112/minecraft22/releases/latest/download/";

    DWORD mcPid    = 0;
    int   step     = 0;
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
                Green(); printf("   [1]  Download + Inject\n"); White();
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

            // ── [1] second press: download + inject ───────────────────────────
            Cls(); Banner();

            // Find Minecraft's java.exe
            std::wstring javaExe = FindMinecraftJava(mcPid);
            if (javaExe.empty()) {
                Red(); printf("   [-] Could not find Minecraft's java.exe.\n\n"); White();
                Sleep(4000); continue;
            }

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

            // Inject via Java agent attach
            printf("   Attaching agent to PID %lu...\n\n", mcPid);
            DeleteTempFile(L"clickgui_log.txt");
            DeleteTempFile(L"clickgui_status.txt");

            if (!AttachAgent(javaExe, mcPid, agentJar)) {
                Red();
                printf("   [-] Attach FAILED!\n");
                printf("   Make sure Minecraft is running and try again.\n\n");
                White();
                Sleep(5000);
                continue;
            }

            Green();
            printf("   [+] Agent attached!\n");
            White();

            // Wait up to 15s for agent to write status
            for (int i = 0; i < 30; i++) {
                Sleep(500);
                std::string st = ReadTempFile(L"clickgui_status.txt");
                if (!st.empty()) break;
            }

            injected = true;

        } else {
            // ── Injected menu ──────────────────────────────────────────────────
            Green();
            printf("        ** INJECTED **\n\n");
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

            // ── PANIC ─────────────────────────────────────────────────────────────────
            Cls(); Banner();
            Red();
            printf("   PANIC - Removing all traces...\n\n");
            White();

            WriteTempFile(L"clickgui_cmd.txt", "0");
            printf("   [1/3] Disable signal sent to agent...\n");
            Sleep(2000);

            DeleteFileW(agentJar.c_str());
            printf("   [2/3] agent.jar deleted\n");

            DeleteTempFile(L"clickgui_cmd.txt");
            DeleteTempFile(L"clickgui_log.txt");
            DeleteTempFile(L"clickgui_status.txt");
            DeleteTempFile(L"clickgui_pause.txt");
            printf("   [3/3] Temp files cleaned\n\n");

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
