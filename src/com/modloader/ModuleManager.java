package com.modloader;

import java.util.*;
import java.io.*;
import java.nio.file.*;

public class ModuleManager {

    final List<Module> modules = new ArrayList<>();

    ModuleManager() {
        // === Combat ===
        Module ac = new Module("AutoClick", "Combat", "Fast click (12 CPS)");
        ac.onEnable = () -> ac.startPS(
            "Add-Type -TypeDefinition @'\n" +
            "using System; using System.Runtime.InteropServices;\n" +
            "public class M {\n" +
            "  [DllImport(\"user32.dll\")] public static extern void mouse_event(int f,int x,int y,int d,int e);\n" +
            "  [DllImport(\"user32.dll\")] public static extern short GetAsyncKeyState(int k);\n" +
            "}\n'@\n" +
            "while($true) {\n" +
            "  if (-not (Test-Path \"$env:TEMP\\clickgui_pause.txt\")) {\n" +
            "    $held = ([M]::GetAsyncKeyState(1) -band 0x8000) -ne 0\n" +
            "    if($held) {\n" +
            "      [M]::mouse_event(2,0,0,0,0); Start-Sleep -Milliseconds 10\n" +
            "      [M]::mouse_event(4,0,0,0,0)\n" +
            "    }\n" +
            "  }\n" +
            "  Start-Sleep -Milliseconds 73\n" +
            "}"
        );
        modules.add(ac);

        Module triggerBot = new Module("TriggerBot", "Combat", "Auto-attack on aim");
        triggerBot.onEnable = () -> triggerBot.startPS(
            "Add-Type -TypeDefinition @'\n" +
            "using System; using System.Runtime.InteropServices;\n" +
            "public class T {\n" +
            "  [DllImport(\"user32.dll\")] public static extern void mouse_event(int f,int x,int y,int d,int e);\n" +
            "  [DllImport(\"user32.dll\")] public static extern short GetAsyncKeyState(int k);\n" +
            "}\n'@\n" +
            "while($true) {\n" +
            "  if (-not (Test-Path \"$env:TEMP\\clickgui_pause.txt\")) {\n" +
            "    $rmb = ([T]::GetAsyncKeyState(2) -band 0x8000) -ne 0\n" +
            "    if($rmb) {\n" +
            "      [T]::mouse_event(2,0,0,0,0); Start-Sleep -Milliseconds 15\n" +
            "      [T]::mouse_event(4,0,0,0,0)\n" +
            "      Start-Sleep -Milliseconds (Get-Random -Min 80 -Max 200)\n" +
            "    }\n" +
            "  }\n" +
            "  Start-Sleep -Milliseconds 30\n" +
            "}"
        );
        modules.add(triggerBot);

        // === Movement ===
        Module sprint = new Module("Sprint", "Movement", "Always sprint");
        sprint.onEnable = () -> sprint.startPS(
            "Add-Type -TypeDefinition @'\n" +
            "using System; using System.Runtime.InteropServices;\n" +
            "public class K { [DllImport(\"user32.dll\")] public static extern void keybd_event(byte v,byte s,int f,int e); }\n" +
            "'@\n" +
            "while($true) {\n" +
            "  if (-not (Test-Path \"$env:TEMP\\clickgui_pause.txt\")) {\n" +
            "    [K]::keybd_event(0xA2,0,0,0)\n" +
            "  }\n" +
            "  Start-Sleep -Milliseconds 50\n" +
            "}"
        );
        sprint.onDisable = () -> releaseKey((byte) 0xA2);
        modules.add(sprint);

        Module autoJump = new Module("BHop", "Movement", "Auto bunny hop");
        autoJump.onEnable = () -> autoJump.startPS(
            "Add-Type -TypeDefinition @'\n" +
            "using System; using System.Runtime.InteropServices;\n" +
            "public class J {\n" +
            "  [DllImport(\"user32.dll\")] public static extern void keybd_event(byte v,byte s,int f,int e);\n" +
            "  [DllImport(\"user32.dll\")] public static extern short GetAsyncKeyState(int k);\n" +
            "}\n'@\n" +
            "while($true) {\n" +
            "  if (-not (Test-Path \"$env:TEMP\\clickgui_pause.txt\")) {\n" +
            "    $w = ([J]::GetAsyncKeyState(0x57) -band 0x8000) -ne 0\n" +
            "    if($w) {\n" +
            "      [J]::keybd_event(0x20,0,0,0); Start-Sleep -Milliseconds 50\n" +
            "      [J]::keybd_event(0x20,0,2,0)\n" +
            "    }\n" +
            "  }\n" +
            "  Start-Sleep -Milliseconds 100\n" +
            "}"
        );
        modules.add(autoJump);

        // === Render ===
        Module fb = new Module("Fullbright", "Render", "Max gamma brightness");
        fb.onEnable = () -> setGamma(16.0);
        fb.onDisable = () -> setGamma(1.0);
        modules.add(fb);

        Module zoom = new Module("Zoom", "Render", "Hold C to zoom");
        zoom.onEnable = () -> zoom.startPS(
            "Add-Type -TypeDefinition @'\n" +
            "using System; using System.Runtime.InteropServices;\n" +
            "public class Z {\n" +
            "  [DllImport(\"user32.dll\")] public static extern short GetAsyncKeyState(int k);\n" +
            "  [DllImport(\"user32.dll\")] public static extern void keybd_event(byte v,byte s,int f,int e);\n" +
            "}\n'@\n" +
            "$zoomed=$false\n" +
            "while($true) {\n" +
            "  $c = ([Z]::GetAsyncKeyState(0x43) -band 0x8000) -ne 0\n" +
            "  if($c -and !$zoomed) {\n" +
            "    1..15 | %{ [Z]::keybd_event(0xBD,0,0,0); [Z]::keybd_event(0xBD,0,2,0); Start-Sleep -Milliseconds 5 }\n" +
            "    $zoomed=$true\n" +
            "  }\n" +
            "  if(!$c -and $zoomed) {\n" +
            "    1..15 | %{ [Z]::keybd_event(0xBB,0,0,0); [Z]::keybd_event(0xBB,0,2,0); Start-Sleep -Milliseconds 5 }\n" +
            "    $zoomed=$false\n" +
            "  }\n" +
            "  Start-Sleep -Milliseconds 30\n" +
            "}"
        );
        modules.add(zoom);

        // === Player ===
        Module noBreakDelay = new Module("FastBreak", "Player", "No block break delay");
        noBreakDelay.onEnable = () -> noBreakDelay.startPS(
            "Add-Type -TypeDefinition @'\n" +
            "using System; using System.Runtime.InteropServices;\n" +
            "public class FB {\n" +
            "  [DllImport(\"user32.dll\")] public static extern void mouse_event(int f,int x,int y,int d,int e);\n" +
            "  [DllImport(\"user32.dll\")] public static extern short GetAsyncKeyState(int k);\n" +
            "}\n'@\n" +
            "while($true) {\n" +
            "  if (-not (Test-Path \"$env:TEMP\\clickgui_pause.txt\")) {\n" +
            "    $lmb = ([FB]::GetAsyncKeyState(1) -band 0x8000) -ne 0\n" +
            "    if($lmb) {\n" +
            "      [FB]::mouse_event(8,0,0,0,0); Start-Sleep -Milliseconds 5\n" +
            "      [FB]::mouse_event(16,0,0,0,0); Start-Sleep -Milliseconds 5\n" +
            "      [FB]::mouse_event(8,0,0,0,0)\n" +
            "    }\n" +
            "  }\n" +
            "  Start-Sleep -Milliseconds 20\n" +
            "}"
        );
        modules.add(noBreakDelay);

        // === World ===
        modules.add(new Module("Coords", "World", "Show coordinates HUD"));

        // === Misc ===
        Module panic = new Module("Panic", "Misc", "Turn off all modules");
        panic.onEnable = () -> {
            for (Module m : modules) {
                if (m != panic && m.enabled) m.toggle();
            }
            panic.enabled = false;
        };
        modules.add(panic);

        Module autoGG = new Module("AutoGG", "Misc", "Say GG on kill");
        modules.add(autoGG);
    }

    List<Module> get(String cat) {
        List<Module> r = new ArrayList<>();
        for (Module m : modules) if (m.category.equals(cat)) r.add(m);
        return r;
    }

    int enabledCount(String cat) {
        int c = 0;
        for (Module m : modules) if (m.category.equals(cat) && m.enabled) c++;
        return c;
    }

    private void setGamma(double v) {
        try {
            String ad = System.getenv("APPDATA");
            if (ad == null) return;
            File f = new File(ad, ".minecraft" + File.separator + "options.txt");
            if (!f.exists()) return;
            List<String> lines = Files.readAllLines(f.toPath());
            List<String> out = new ArrayList<>();
            boolean found = false;
            for (String l : lines) {
                if (l.startsWith("gamma:")) { out.add("gamma:" + v); found = true; }
                else out.add(l);
            }
            if (!found) out.add("gamma:" + v);
            Files.write(f.toPath(), out);
            System.out.println("[ClickGui] Gamma -> " + v + " (F3+T in game to apply)");
        } catch (Exception e) {
            System.out.println("[ClickGui] Gamma error: " + e.getMessage());
        }
    }

    private void releaseKey(byte vk) {
        try {
            new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                "Add-Type -TypeDefinition @'\nusing System; using System.Runtime.InteropServices;\n" +
                "public class R { [DllImport(\"user32.dll\")] public static extern void keybd_event(byte v,byte s,int f,int e); }\n" +
                "'@; [R]::keybd_event(" + (vk & 0xFF) + ",0,2,0)"
            ).start();
        } catch (Exception e) {}
    }
}
