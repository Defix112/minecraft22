#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <winhttp.h>
#include <shlwapi.h>
#include <stdio.h>
#include <string>
#include <vector>

// ──────────────────────────────────────────────────────────────────────────────
// Console helpers
// ──────────────────────────────────────────────────────────────────────────────
static void Cls() { system("cls"); }
static void Green()  { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), FOREGROUND_GREEN|FOREGROUND_INTENSITY); }
static void White()  { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), FOREGROUND_RED|FOREGROUND_GREEN|FOREGROUND_BLUE); }
static void Red()    { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), FOREGROUND_RED|FOREGROUND_INTENSITY); }
static void Yellow() { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), FOREGROUND_RED|FOREGROUND_GREEN|FOREGROUND_INTENSITY); }

static char WaitKey(const char* allowed) {
    HANDLE in = GetStdHandle(STD_INPUT_HANDLE);
    SetConsoleMode(in, ENABLE_PROCESSED_INPUT);
    FlushConsoleInputBuffer(in);
    while (true) {
        INPUT_RECORD ir; DWORD n = 0;
        ReadConsoleInputA(in, &ir, 1, &n);
        if (ir.EventType == KEY_EVENT && ir.Event.KeyEvent.bKeyDown) {
            char c = ir.Event.KeyEvent.uChar.AsciiChar;
            for (const char* p = allowed; *p; p++)
                if (c == *p) return c;
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Find Minecraft PID
// ──────────────────────────────────────────────────────────────────────────────
static DWORD FindMinecraft() {
    const wchar_t* names[] = { L"javaw.exe", L"java.exe", nullptr };
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

// ──────────────────────────────────────────────────────────────────────────────
// Download via WinHTTP (follows GitHub redirects)
// ──────────────────────────────────────────────────────────────────────────────
static bool Download(const std::wstring& url, const std::wstring& outPath, int depth = 0) {
    if (depth > 5) return false;

    URL_COMPONENTSW uc = { sizeof(uc) };
    wchar_t host[512] = {}, path[2048] = {};
    uc.lpszHostName = host; uc.dwHostNameLength = 512;
    uc.lpszUrlPath  = path; uc.dwUrlPathLength  = 2048;
    if (!WinHttpCrackUrl(url.c_str(), 0, 0, &uc)) return false;

    HINTERNET ses = WinHttpOpen(L"SystemClient/6.0",
        WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, NULL, NULL, 0);
    HINTERNET con = WinHttpConnect(ses, host, uc.nPort, 0);
    DWORD flags = (uc.nScheme == INTERNET_SCHEME_HTTPS) ? WINHTTP_FLAG_SECURE : 0;
    HINTERNET req = WinHttpOpenRequest(con, L"GET", path, NULL, NULL, NULL, flags);

    WinHttpSendRequest(req, NULL, 0, NULL, 0, 0, 0);
    WinHttpReceiveResponse(req, NULL);

    // Check for redirect
    wchar_t location[2048] = {}; DWORD sz = sizeof(location);
    if (WinHttpQueryHeaders(req, WINHTTP_QUERY_LOCATION, NULL, location, &sz, NULL)) {
        WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
        return Download(location, outPath, depth + 1);
    }

    // Check HTTP status
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

// ──────────────────────────────────────────────────────────────────────────────
// Inject agent via Java Attach API (spawn java.exe with attach command)
// Requires JDK; searches common install paths.
// ──────────────────────────────────────────────────────────────────────────────
static std::wstring FindJava() {
    // 1. JAVA_HOME
    wchar_t jh[MAX_PATH] = {};
    if (GetEnvironmentVariableW(L"JAVA_HOME", jh, MAX_PATH)) {
        std::wstring p = std::wstring(jh) + L"\\bin\\java.exe";
        if (GetFileAttributesW(p.c_str()) != INVALID_FILE_ATTRIBUTES) return p;
    }
    // 2. PATH
    wchar_t buf[MAX_PATH] = {};
    if (SearchPathW(NULL, L"java.exe", NULL, MAX_PATH, buf, NULL)) return buf;
    // 3. Common install dirs
    const wchar_t* roots[] = {
        L"C:\\Program Files\\Java",
        L"C:\\Program Files\\Eclipse Adoptium",
        L"C:\\Program Files\\Microsoft",
        L"C:\\Program Files\\Amazon Corretto",
        nullptr
    };
    for (int i = 0; roots[i]; i++) {
        std::wstring pat = std::wstring(roots[i]) + L"\\jdk-*";
        WIN32_FIND_DATAW fd; HANDLE h = FindFirstFileW(pat.c_str(), &fd);
        if (h != INVALID_HANDLE_VALUE) {
            do {
                if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                    std::wstring p = std::wstring(roots[i]) + L"\\" + fd.cFileName + L"\\bin\\java.exe";
                    if (GetFileAttributesW(p.c_str()) != INVALID_FILE_ATTRIBUTES) {
                        FindClose(h); return p;
                    }
                }
            } while (FindNextFileW(h, &fd));
            FindClose(h);
        }
    }
    return {};
}

static bool Inject(DWORD pid, const std::wstring& javaExe, const std::wstring& agentJar) {
    // Build command:
    // java --add-modules jdk.attach --add-opens java.base/java.lang=ALL-UNNAMED
    //      -cp agent.jar com.modloader.Injector <pid> <agentJar>
    wchar_t cmd[4096];
    swprintf_s(cmd,
        L"\"%s\" --add-modules jdk.attach "
        L"--add-opens java.base/java.lang=ALL-UNNAMED "
        L"-cp \"%s\" com.modloader.Injector %lu \"%s\"",
        javaExe.c_str(), agentJar.c_str(), pid, agentJar.c_str());

    STARTUPINFOW si = { sizeof(si) };
    si.dwFlags = STARTF_USESTDHANDLES;
    // Pipe stdout/stderr so we can read it
    HANDLE pipeR, pipeW;
    SECURITY_ATTRIBUTES sa = { sizeof(sa), NULL, TRUE };
    CreatePipe(&pipeR, &pipeW, &sa, 0);
    SetHandleInformation(pipeR, HANDLE_FLAG_INHERIT, 0);
    si.hStdOutput = pipeW;
    si.hStdError  = pipeW;
    si.hStdInput  = GetStdHandle(STD_INPUT_HANDLE);

    PROCESS_INFORMATION pi = {};
    if (!CreateProcessW(NULL, cmd, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
        CloseHandle(pipeR); CloseHandle(pipeW);
        return false;
    }
    CloseHandle(pipeW);
    WaitForSingleObject(pi.hProcess, 15000);

    // Read output
    char out[4096] = {}; DWORD rd = 0;
    ReadFile(pipeR, out, sizeof(out)-1, &rd, NULL);
    CloseHandle(pipeR);

    DWORD code = 1;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hProcess); CloseHandle(pi.hThread);

    if (rd > 0) { printf("%s\n", out); }
    return code == 0;
}

// ──────────────────────────────────────────────────────────────────────────────
// Read temp file
// ──────────────────────────────────────────────────────────────────────────────
static std::string ReadTempFile(const wchar_t* name) {
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

static void DeleteTempFile(const wchar_t* name) {
    wchar_t tmp[MAX_PATH], p[MAX_PATH];
    GetTempPathW(MAX_PATH, tmp);
    swprintf_s(p, L"%s%s", tmp, name);
    DeleteFileW(p);
}

static void WriteTempFile(const wchar_t* name, const char* data) {
    wchar_t tmp[MAX_PATH], p[MAX_PATH];
    GetTempPathW(MAX_PATH, tmp);
    swprintf_s(p, L"%s%s", tmp, name);
    HANDLE f = CreateFileW(p, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, 0, NULL);
    if (f != INVALID_HANDLE_VALUE) { DWORD w; WriteFile(f, data, (DWORD)strlen(data), &w, NULL); CloseHandle(f); }
}

// ──────────────────────────────────────────────────────────────────────────────
// Banner
// ──────────────────────────────────────────────────────────────────────────────
static void Banner() {
    Green();
    printf("\n");
    printf("  ==========================================\n");
    printf("       SYSTEM CLIENT  v6.0  [C++]\n");
    printf("   Minecraft 1.21.11  |  RIGHT SHIFT = GUI\n");
    printf("  ==========================================\n");
    printf("\n");
    White();
}

// ──────────────────────────────────────────────────────────────────────────────
// Entry point
// ──────────────────────────────────────────────────────────────────────────────
int main() {
    SetConsoleTitleA("System Client v6.0");
    Green();

    // Exe directory -> agent.jar path
    wchar_t exePath[MAX_PATH];
    GetModuleFileNameW(NULL, exePath, MAX_PATH);
    wchar_t* sl = wcsrchr(exePath, L'\\');
    std::wstring exeDir = sl ? std::wstring(exePath, sl + 1) : L"";
    std::wstring agentJar = exeDir + L"agent.jar";

    const std::wstring RELEASE_URL =
        L"https://github.com/defix112/minecraft22/releases/latest/download/agent.jar";

    DWORD mcPid = 0;
    int   step  = 0;   // 0=not found, 1=pid found
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
                Green(); printf("   [1]  Download mod + Inject\n"); White();
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

            // ── [1] second press: download + inject ─────────────────────────
            Cls(); Banner();

            // Download if missing
            if (GetFileAttributesW(agentJar.c_str()) == INVALID_FILE_ATTRIBUTES) {
                printf("   Downloading agent.jar...\n\n");
                if (!Download(RELEASE_URL, agentJar)) {
                    Red();
                    printf("   [-] Download failed.\n");
                    printf("   Check your internet connection.\n\n");
                    White();
                    Sleep(4000);
                    continue;
                }
                Green(); printf("   [+] Downloaded OK.\n\n"); White();
            } else {
                Green(); printf("   [+] agent.jar present.\n\n"); White();
            }

            // Find Java
            std::wstring javaExe = FindJava();
            if (javaExe.empty()) {
                Red();
                printf("   [-] JDK not found!\n");
                printf("   Download JDK 21 from: https://adoptium.net/\n\n");
                White();
                Sleep(5000);
                continue;
            }

            // Inject
            printf("   Injecting into PID %lu...\n\n", mcPid);
            DeleteTempFile(L"clickgui_log.txt");
            DeleteTempFile(L"clickgui_status.txt");

            if (!Inject(mcPid, javaExe, agentJar)) {
                Red();
                printf("\n   [-] Injection FAILED!\n");
                printf("   Run loader.exe as Administrator.\n");
                printf("   Make sure you joined a Minecraft world.\n\n");
                White();
                Sleep(5000);
                continue;
            }

            Green();
            printf("\n   [+] Injected OK! Waiting for agent...\n");
            White();
            Sleep(4000);

            // Show agent log
            std::string log = ReadTempFile(L"clickgui_log.txt");
            if (!log.empty()) {
                printf("\n   Agent log:\n");
                printf("%s\n", log.c_str());
            }
            injected = true;

        } else {
            // ── Injected menu ───────────────────────────────────────────────
            Green();
            printf("        ** INJECTED **\n\n");
            White();
            printf("   Press RIGHT SHIFT in game to open GUI.\n");
            printf("   (must be in a world, no screen open)\n\n");

            std::string status = ReadTempFile(L"clickgui_status.txt");
            if (!status.empty()) {
                printf("   Status: "); Green(); printf("%s\n\n", status.c_str()); White();
            } else {
                printf("   Status: waiting...\n\n");
            }

            std::string log2 = ReadTempFile(L"clickgui_log.txt");
            if (!log2.empty()) {
                printf("   Log: %s\n\n", log2.c_str());
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
