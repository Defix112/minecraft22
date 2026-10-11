package com.modloader;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.File;
import java.util.List;

public class Injector {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: Injector <pid> <agent.jar>");
            System.exit(1);
        }

        String pid = args[0];
        String agentPath = new File(args[1]).getAbsolutePath();

        System.out.println("  PID: " + pid);
        System.out.println("  Agent: " + agentPath);

        if (!new File(agentPath).exists()) {
            System.out.println("  ERROR: agent.jar not found at " + agentPath);
            System.exit(1);
        }

        try {
            System.out.println("  Attaching...");
            VirtualMachine vm = VirtualMachine.attach(pid);
            System.out.println("  Attached OK. Loading agent...");
            vm.loadAgent(agentPath, agentPath);
            System.out.println("  Agent loaded OK.");
            vm.detach();
            System.out.println("  Done.");
        } catch (Exception e) {
            System.out.println("  FAILED: " + e.getClass().getSimpleName());
            System.out.println("  " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Non-numeric")) {
                System.out.println("  Wrong PID. Make sure Minecraft is running.");
            }
            System.exit(1);
        }
    }
}
