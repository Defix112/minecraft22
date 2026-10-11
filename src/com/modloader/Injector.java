package com.modloader;

import com.sun.tools.attach.VirtualMachine;
import java.io.File;

public class Injector {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: Injector <pid> <agent.jar>");
            System.exit(1);
        }

        String pid = args[0];
        String agentPath = args[1];

        try {
            System.out.println("  Подключение к процессу " + pid + "...");
            VirtualMachine vm = VirtualMachine.attach(pid);
            System.out.println("  Загрузка агента...");
            vm.loadAgent(new File(agentPath).getAbsolutePath());
            vm.detach();
            System.out.println("  Агент загружен успешно.");
        } catch (Exception e) {
            System.out.println("  Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }
}
