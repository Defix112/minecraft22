#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <winhttp.h>
#include <shlwapi.h>
#include <stdio.h>
#include <string>

#define HOTKEY_ID 0x4D43

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
        DWORD avail = 0;
        GetNumberOfConsoleInputEvents(in, &avail);
        if (avail == 0) { Sleep(20); continue; }
        INPUT_RECORD ir; DWORD n = 0;
        ReadConsoleInputA(in, &ir, 1, &n);
        if (ir.EventType == KEY_EVENT && ir.Event.KeyEvent.bKeyDown) {
            char c = ir.Event.KeyEvent.uChar.AsciiChar;
            for (const char *p = allowed; *p; p++)
                if (c == *p) return c;
        }
    }
}

static DWORD FindMinecraft() {
    const wchar_t *names[] = { L"javaw.exe", L"java.exe", nullptr };
    for (int i = 0; names[i]; i++) {
        HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        PROCESSENTRY32W pe = { sizeof(pe) };
        if (Process32FirstW(snap, &pe)) {
            do {
                if (_wcsicmp(pe.szExeFile, names[i]) == 0) {
                    CloseHandle(snap); return pe.th32ProcessID;
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
    wchar_t buf[MAX_PATH * 2] = {}; DWORD size = MAX_PATH * 2;
    BOOL ok = QueryFullProcessImageNameW(hProc, 0, buf, &size);
    CloseHandle(hProc);
    if (!ok) return L"";
    wchar_t *last = wcsrchr(buf, L'\\');
    if (last) wcscpy_s(last + 1, (sizeof(buf)/sizeof(wchar_t))-(last+1-buf), L"java.exe");
    if (GetFileAttributesW(buf) == INVALID_FILE_ATTRIBUTES) {
        HANDLE h2 = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
        if (h2) { QueryFullProcessImageNameW(h2, 0, buf, &size); CloseHandle(h2); }
    }
    return buf;
}

static bool Download(const std::wstring &url, const std::wstring &outPath, int depth = 0) {
    if (depth > 5) return false;
    URL_COMPONENTSW uc = { sizeof(uc) };
    wchar_t host[512]={}, path[2048]={};
    uc.lpszHostName=host; uc.dwHostNameLength=512;
    uc.lpszUrlPath=path;  uc.dwUrlPathLength=2048;
    if (!WinHttpCrackUrl(url.c_str(), 0, 0, &uc)) return false;
    HINTERNET ses=WinHttpOpen(L"Mozilla/5.0",WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,NULL,NULL,0);
    HINTERNET con=WinHttpConnect(ses,host,uc.nPort,0);
    DWORD flags=(uc.nScheme==INTERNET_SCHEME_HTTPS)?WINHTTP_FLAG_SECURE:0;
    HINTERNET req=WinHttpOpenRequest(con,L"GET",path,NULL,NULL,NULL,flags);
    WinHttpSendRequest(req,NULL,0,NULL,0,0,0);
    WinHttpReceiveResponse(req,NULL);
    wchar_t loc[2048]={}; DWORD sz=sizeof(loc);
    if (WinHttpQueryHeaders(req,WINHTTP_QUERY_LOCATION,NULL,loc,&sz,NULL)) {
        WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
        return Download(loc,outPath,depth+1);
    }
    DWORD status=0; sz=sizeof(status);
    WinHttpQueryHeaders(req,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,NULL,&status,&sz,NULL);
    if (status!=200){WinHttpCloseHandle(req);WinHttpCloseHandle(con);WinHttpCloseHandle(ses);return false;}
    HANDLE f=CreateFileW(outPath.c_str(),GENERIC_WRITE,0,NULL,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if (f==INVALID_HANDLE_VALUE){WinHttpCloseHandle(req);WinHttpCloseHandle(con);WinHttpCloseHandle(ses);return false;}
    char buf[65536]; DWORD rd=0,wr=0;
    while(WinHttpReadData(req,buf,sizeof(buf),&rd)&&rd>0) WriteFile(f,buf,rd,&wr,NULL);
    CloseHandle(f); WinHttpCloseHandle(req); WinHttpCloseHandle(con); WinHttpCloseHandle(ses);
    return true;
}

static bool AttachAgent(const std::wstring &javaExe, DWORD pid,
                        const std::wstring &agentJar, std::string &outLog) {
    wchar_t absJar[MAX_PATH];
    GetFullPathNameW(agentJar.c_str(), MAX_PATH, absJar, nullptr);
    std::wstring cmd = L"\"" + javaExe + L"\""
        L" --add-modules jdk.attach -cp \"" + absJar + L"\""
        L" com.modloader.Injector " + std::to_wstring(pid) + L" \"" + absJar + L"\"";
    SECURITY_ATTRIBUTES sa={sizeof(sa),NULL,TRUE};
    HANDLE pipeR,pipeW;
    CreatePipe(&pipeR,&pipeW,&sa,0);
    SetHandleInformation(pipeR,HANDLE_FLAG_INHERIT,0);
    STARTUPINFOW si={}; si.cb=sizeof(si);
    si.dwFlags=STARTF_USESTDHANDLES|STARTF_USESHOWWINDOW;
    si.wShowWindow=SW_HIDE;
    si.hStdOutput=pipeW; si.hStdError=pipeW;
    si.hStdInput=GetStdHandle(STD_INPUT_HANDLE);
    PROCESS_INFORMATION pi={};
    if (!CreateProcessW(nullptr,&cmd[0],nullptr,nullptr,TRUE,CREATE_NO_WINDOW,nullptr,nullptr,&si,&pi)) {
        CloseHandle(pipeR); CloseHandle(pipeW); return false;
    }
    CloseHandle(pipeW);
    WaitForSingleObject(pi.hProcess,30000);
    char buf[4096]={}; DWORD rd=0;
    ReadFile(pipeR,buf,sizeof(buf)-1,&rd,NULL); CloseHandle(pipeR);
    if (rd>0) outLog=buf;
    DWORD exitCode=1;
    GetExitCodeProcess(pi.hProcess,&exitCode);
    CloseHandle(pi.hProcess); CloseHandle(pi.hThread);
    return exitCode==0;
}

static std::string ReadTempFile(const wchar_t *name) {
    wchar_t tmp[MAX_PATH],p[MAX_PATH];
    GetTempPathW(MAX_PATH,tmp); swprintf_s(p,L"%s%s",tmp,name);
    HANDLE f=CreateFileW(p,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,0,NULL);
    if (f==INVALID_HANDLE_VALUE) return {};
    char buf[8192]={}; DWORD rd;
    ReadFile(f,buf,sizeof(buf)-1,&rd,NULL); CloseHandle(f);
    return buf;
}
static void DeleteTempFile(const wchar_t *name) {
    wchar_t tmp[MAX_PATH],p[MAX_PATH];
    GetTempPathW(MAX_PATH,tmp); swprintf_s(p,L"%s%s",tmp,name);
    DeleteFileW(p);
}
static void WriteTempFile(const wchar_t *name, const char *data) {
    wchar_t tmp[MAX_PATH],p[MAX_PATH];
    GetTempPathW(MAX_PATH,tmp); swprintf_s(p,L"%s%s",tmp,name);
    HANDLE f=CreateFileW(p,GENERIC_WRITE,0,NULL,CREATE_ALWAYS,0,NULL);
    if (f!=INVALID_HANDLE_VALUE){DWORD w;WriteFile(f,data,(DWORD)strlen(data),&w,NULL);CloseHandle(f);}
}

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

static bool   g_injected = false;
static std::wstring g_agentJar;
static std::wstring g_baseUrl;

static void DoPanic() {
    Cls(); Banner();
    Red(); printf("   Removing all traces...\n\n"); White();
    WriteTempFile(L"clickgui_cmd.txt","0");
    printf("   [1/4] Signal sent...\n"); Sleep(2000);
    DeleteFileW(g_agentJar.c_str());
    printf("   [2/4] agent.jar deleted\n");
    DeleteTempFile(L"clickgui_cmd.txt"); DeleteTempFile(L"clickgui_log.txt");
    DeleteTempFile(L"clickgui_status.txt"); DeleteTempFile(L"clickgui_pause.txt");
    printf("   [3/4] Temp files cleaned\n");
    printf("   [4/4] Done\n\n");
    Green(); printf("   All traces removed.\n\n"); White();
    Sleep(2000);
}

static void RunLoader() {
    HWND con = GetConsoleWindow();
    if (!con) {
        AllocConsole();
        freopen("CONOUT$", "w", stdout);
        freopen("CONIN$",  "r", stdin);
        con = GetConsoleWindow();
    }
    ShowWindow(con, SW_SHOW);
    SetForegroundWindow(con);
    SetConsoleTitleA("System Client v8.0");

    while (true) {
        Cls(); Banner();
        DWORD mcPid = FindMinecraft();

        if (!g_injected) {
            if (mcPid) {
                Green(); printf("   Minecraft : PID %lu\n\n", mcPid); White();
                Green(); printf("   [1]  Install & Inject mod\n"); White();
            } else {
                Red(); printf("   Minecraft : NOT FOUND\n\n"); White();
                Yellow(); printf("   Start Minecraft 1.21.11 first!\n\n"); White();
                Green(); printf("   [1]  Retry\n"); White();
            }
            Red();    printf("   [2]  Remove all mod files\n"); White();
            Yellow(); printf("   [0]  Close\n"); White();
            printf("  ==========================================\n\n");

            char k = WaitKey("120");
            if (k=='0') break;
            if (k=='2') { DoPanic(); g_injected=false; continue; }
            if (!mcPid) continue;

            Cls(); Banner();
            std::wstring javaExe = FindMinecraftJava(mcPid);
            if (javaExe.empty()) {
                Red(); printf("   [-] Cannot find java.exe\n\n"); White();
                Sleep(3000); continue;
            }
            char jbuf[MAX_PATH*2];
            WideCharToMultiByte(CP_ACP,0,javaExe.c_str(),-1,jbuf,sizeof(jbuf),0,0);
            Green(); printf("   [+] java: %s\n\n", jbuf); White();

            if (GetFileAttributesW(g_agentJar.c_str())==INVALID_FILE_ATTRIBUTES) {
                printf("   Downloading agent.jar...\n");
                if (!Download(g_baseUrl+L"agent.jar", g_agentJar)) {
                    Red(); printf("   [-] Download failed!\n\n"); White();
                    Sleep(4000); continue;
                }
                Green(); printf("   [+] Downloaded OK\n\n"); White();
            } else {
                Green(); printf("   [+] agent.jar present\n\n"); White();
            }

            printf("   Injecting into PID %lu...\n\n", mcPid);
            DeleteTempFile(L"clickgui_log.txt"); DeleteTempFile(L"clickgui_status.txt");

            std::string attachLog;
            if (!AttachAgent(javaExe, mcPid, g_agentJar, attachLog)) {
                Red(); printf("   [-] Injection FAILED!\n");
                if (!attachLog.empty()) printf("%s\n", attachLog.c_str());
                printf("\n   Run as Administrator.\n\n"); White();
                Sleep(5000); continue;
            }
            if (!attachLog.empty()) printf("%s\n", attachLog.c_str());

            printf("   Waiting for agent...\n");
            for (int i=0; i<20; i++) {
                Sleep(500);
                if (!ReadTempFile(L"clickgui_status.txt").empty()) break;
            }
            g_injected = true;

        } else {
            std::string status = ReadTempFile(L"clickgui_status.txt");
            std::string log    = ReadTempFile(L"clickgui_log.txt");
            Green(); printf("        ** INJECTED **\n\n"); White();
            printf("   RIGHT SHIFT = open GUI (works anywhere)\n\n");
            if (!status.empty()) {
                if (status.find("ERROR")!=std::string::npos) Red(); else Green();
                printf("   Status: %s\n\n", status.c_str()); White();
            }
            if (!log.empty()) {
                Yellow(); printf("   --- Log ---\n"); White();
                printf("%s\n", log.c_str());
            }
            Red();    printf("   [2]  Remove all traces\n"); White();
            Yellow(); printf("   [0]  Close\n"); White();
            printf("  ==========================================\n\n");
            char k = WaitKey("20");
            if (k=='0') break;
            DoPanic(); g_injected=false;
        }
    }

    ShowWindow(con, SW_HIDE);
}

int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int) {
    wchar_t exePath[MAX_PATH];
    GetModuleFileNameW(NULL, exePath, MAX_PATH);
    wchar_t *sl = wcsrchr(exePath, L'\\');
    std::wstring exeDir = sl ? std::wstring(exePath, sl+1) : L"";
    g_agentJar = exeDir + L"agent.jar";
    g_baseUrl   = L"https://github.com/defix112/minecraft22/releases/latest/download/";

    // Register global hotkey Ctrl+U
    if (!RegisterHotKey(NULL, HOTKEY_ID, MOD_CONTROL|MOD_NOREPEAT, 'U')) {
        RunLoader();
        return 0;
    }

    // Silent message loop
    MSG msg;
    while (GetMessageW(&msg, NULL, 0, 0)) {
        if (msg.message == WM_HOTKEY && msg.wParam == HOTKEY_ID) {
            RunLoader();
        }
        DispatchMessageW(&msg);
    }

    UnregisterHotKey(NULL, HOTKEY_ID);
    return 0;
}
