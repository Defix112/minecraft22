package com.modloader;

import com.sun.tools.attach.VirtualMachine;

// Spawned by loader.exe as a subprocess to attach agent.jar to the running Minecraft JVM.
// Requires --add-modules jdk.attach on the command line.
public class Attach {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: Attach <pid> <agentJar>");
            System.exit(1);
        }
        String pid      = args[0];
        String agentJar = args[1];
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(agentJar);
        } finally {
            vm.detach();
        }
    }
}
