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
        System.out.println("[ClickGui] KeyHook starting...");

        // VK_RSHIFT = 0xA1 = 161
        String script = String.join("; ",
            "Add-Type -TypeDefinition @'",
            "using System; using System.Runtime.InteropServices;",
            "public class HK { [DllImport(\"user32.dll\")] public static extern short GetAsyncKeyState(int k); }",
            "'@",
            "$prev = $false",
            "while($true) {",
            "  $now = ([HK]::GetAsyncKeyState(0xA1) -band 0x8000) -ne 0",
            "  if($now -and !$prev) { [Console]::Out.WriteLine('T'); [Console]::Out.Flush() }",
            "  $prev = $now",
            "  Start-Sleep -Milliseconds 40",
            "}"
        );

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NoLogo",
                "-ExecutionPolicy", "Bypass", "-Command", script
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            System.out.println("[ClickGui] KeyHook ACTIVE: Right Shift to toggle");

            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.equals("T")) {
                    System.out.println("[ClickGui] Right Shift pressed");
                    gui.toggle();
                }
            }

            System.out.println("[ClickGui] KeyHook process ended");
        } catch (Exception e) {
            System.out.println("[ClickGui] KeyHook failed: " + e.getMessage());
            System.out.println("[ClickGui] Right Shift won't work.");
            System.out.println("[ClickGui] Use test.cmd [1] to toggle.");
        }
    }
}
