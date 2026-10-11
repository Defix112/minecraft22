package com.modloader;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Injector {

    public static void main(String[] args) {
        System.out.println();
        System.out.println("  === ClickGui Injector ===");
        System.out.println();

        String agentJar;
        String pid = null;

        // determine agent jar path
        if (args.length >= 1) {
            agentJar = args[args.length - 1];
        } else {
            System.out.println("  [-] Usage: Injector [pid] <agent.jar>");
            System.exit(1);
            return;
        }

        // determine PID
        if (args.length >= 2) {
            pid = args[0];
        }

        // verify agent jar exists
        File jarFile = new File(agentJar);
        if (!jarFile.exists()) {
            System.out.println("  [-] Agent jar not found: " + agentJar);
            System.exit(1);
        }

        try {
            // list all JVMs
            System.out.println("  Scanning Java processes...");
            List<VirtualMachineDescriptor> allVms = VirtualMachine.list();
            System.out.println("  Found " + allVms.size() + " Java process(es):");

            List<VirtualMachineDescriptor> candidates = new ArrayList<>();

            for (VirtualMachineDescriptor vmd : allVms) {
                String display = vmd.displayName();
                String id = vmd.id();

                // skip ourselves
                if (display.contains("com.modloader.Injector")) continue;

                System.out.println("    PID=" + id + "  " + (display.isEmpty() ? "(no name)" : display));

                // check if this looks like Minecraft
                String lower = display.toLowerCase();
                if (lower.contains("minecraft") || lower.contains("net.minecraft")
                        || lower.contains("launchwrapper") || lower.contains("cpw.mods")
                        || lower.contains("fabricloader") || lower.contains("net.fabricmc")
                        || lower.contains("forge") || lower.contains("gradlestart")
                        || lower.contains("tlauncher") || lower.contains("optifine")) {
                    candidates.add(vmd);
                }
            }

            // if no named candidates, use the provided PID or pick unnamed ones
            if (pid == null) {
                if (!candidates.isEmpty()) {
                    pid = candidates.get(0).id();
                    System.out.println("  [+] Detected Minecraft: PID=" + pid);
                } else {
                    // try unnamed java processes (Minecraft often shows as empty displayName)
                    for (VirtualMachineDescriptor vmd : allVms) {
                        if (vmd.displayName().contains("com.modloader.Injector")) continue;
                        if (vmd.displayName().isEmpty() || vmd.displayName().equals("main")) {
                            pid = vmd.id();
                            System.out.println("  [?] Trying unnamed process: PID=" + pid);
                            break;
                        }
                    }
                    if (pid == null && !allVms.isEmpty()) {
                        // last resort: pick first non-self process
                        for (VirtualMachineDescriptor vmd : allVms) {
                            if (!vmd.displayName().contains("com.modloader.Injector")) {
                                pid = vmd.id();
                                System.out.println("  [?] Trying process: PID=" + pid);
                                break;
                            }
                        }
                    }
                }
            }

            if (pid == null) {
                System.out.println("  [-] No Java process found to inject into.");
                System.out.println("  [-] Make sure Minecraft is running.");
                System.exit(1);
            }

            System.out.println();
            System.out.println("  Attaching to PID " + pid + "...");
            VirtualMachine vm = VirtualMachine.attach(pid);

            System.out.println("  Loading agent: " + jarFile.getAbsolutePath());
            vm.loadAgent(jarFile.getAbsolutePath());

            vm.detach();
            System.out.println();
            System.out.println("  [+] Agent loaded successfully!");

        } catch (com.sun.tools.attach.AttachNotSupportedException e) {
            System.out.println();
            System.out.println("  [-] Cannot attach to process: " + e.getMessage());
            System.out.println("  [-] Try running as Administrator.");
            System.exit(1);
        } catch (Exception e) {
            System.out.println();
            System.out.println("  [-] Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
