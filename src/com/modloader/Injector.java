package com.modloader;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.File;
import java.util.List;

public class Injector {

    public static String findMinecraftPid() {
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        for (VirtualMachineDescriptor vmd : vms) {
            String name = vmd.displayName().toLowerCase();
            if (name.contains("minecraft") || name.contains("net.minecraft")
                    || name.contains("launchwrapper") || name.contains("cpw.mods")
                    || name.contains("fabricloader") || name.contains("net.fabricmc")) {
                return vmd.id();
            }
        }
        // fallback: any javaw process that looks like a game
        for (VirtualMachineDescriptor vmd : vms) {
            String name = vmd.displayName().toLowerCase();
            if (name.contains("main") || name.isEmpty()) {
                // could be minecraft launched without explicit main class
            }
        }
        return null;
    }

    public static void inject(String pid, String agentJar) throws Exception {
        System.out.println("  Attaching to PID " + pid + "...");
        VirtualMachine vm = VirtualMachine.attach(pid);
        System.out.println("  Loading agent...");
        vm.loadAgent(new File(agentJar).getAbsolutePath());
        vm.detach();
        System.out.println("  Agent loaded!");
    }

    public static void main(String[] args) {
        try {
            String pid;
            String agentJar;

            if (args.length >= 2) {
                pid = args[0];
                agentJar = args[1];
            } else if (args.length == 1) {
                agentJar = args[0];
                pid = findMinecraftPid();
                if (pid == null) {
                    System.out.println("  [-] Minecraft process not found.");
                    System.exit(1);
                }
                System.out.println("  [+] Found Minecraft PID: " + pid);
            } else {
                System.out.println("Usage: Injector [pid] <agent.jar>");
                System.exit(1);
                return;
            }

            inject(pid, agentJar);
            System.out.println("  [+] Injection successful!");

        } catch (Exception e) {
            System.out.println("  [-] Injection failed: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("attach")) {
                System.out.println("  Try running as Administrator.");
                System.out.println("  Make sure Minecraft uses the same Java version.");
            }
            System.exit(1);
        }
    }
}
