package com.modloader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Fullbright {

    static volatile boolean enabled = false;
    private static final double BRIGHT = 16.0;
    private static final double NORMAL = 1.0;

    static void toggle() {
        enabled = !enabled;
        Agent.writeLog("Fullbright: " + (enabled ? "ON" : "OFF"));
        setGamma(enabled ? BRIGHT : NORMAL);
    }

    static void tick() {
        // gamma set once on toggle
    }

    static void disable() {
        enabled = false;
        setGamma(NORMAL);
    }

    private static void setGamma(double v) {
        try {
            String ad = System.getenv("APPDATA");
            if (ad == null) return;
            File f = new File(ad, ".minecraft" + File.separator + "options.txt");
            if (!f.exists()) return;
            List<String> lines = Files.readAllLines(f.toPath());
            List<String> out = new ArrayList<>();
            boolean found = false;
            for (String l : lines) {
                if (l.startsWith("gamma:")) {
                    out.add("gamma:" + v);
                    found = true;
                } else {
                    out.add(l);
                }
            }
            if (!found) out.add("gamma:" + v);
            Files.write(f.toPath(), out);
            Agent.writeLog("Gamma -> " + v);
        } catch (Exception e) {
            Agent.writeLog("Gamma error: " + e.getMessage());
        }
    }
}
