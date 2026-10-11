package com.modloader;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class KeyHook implements Runnable {

    private final ClickGui gui;

    public KeyHook(ClickGui gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        System.out.println("[ClickGui] Starting Right Shift listener...");

        // Use PowerShell + Windows API GetAsyncKeyState for global hotkey
        // VK_RSHIFT = 0xA1 (161)
        try {
            String psScript =
                "Add-Type -TypeDefinition '" +
                "using System; using System.Runtime.InteropServices; " +
                "public class KS { " +
                "[DllImport(\"user32.dll\")] public static extern short GetAsyncKeyState(int vKey); " +
                "}'; " +
                "$wasp = $false; " +
                "while ($true) { " +
                "  $p = ([KS]::GetAsyncKeyState(0xA1) -band 0x8000) -ne 0; " +
                "  if ($p -and -not $wasp) { " +
                "    [Console]::WriteLine('TOGGLE'); " +
                "    [Console]::Out.Flush(); " +
                "  } " +
                "  $wasp = $p; " +
                "  Start-Sleep -Milliseconds 50 " +
                "}";

            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            System.out.println("[ClickGui] Key listener ACTIVE (Right Shift)");

            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("TOGGLE")) {
                    gui.toggle();
                }
            }

            System.out.println("[ClickGui] Key listener stopped");
        } catch (Exception e) {
            System.out.println("[ClickGui] Key listener error: " + e.getMessage());
            System.out.println("[ClickGui] Use test.cmd [1] to toggle instead");
        }
    }
}
