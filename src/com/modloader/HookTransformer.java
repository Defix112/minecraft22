package com.modloader;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

// Inserts a call to the same-named static method in Hooks, passing the hooked method's arguments.
public class HookTransformer implements ClassFileTransformer {

    private static final String HOOKS = "com/modloader/Hooks";

    record Target(String owner, String name, String desc, String hook, boolean atReturn) {}

    static final Target[] TARGETS = {
        new Target(ObfNames.onTick_OWNER, ObfNames.onTick_NAME, ObfNames.onTick_DESC, "onTick", false),
        new Target(ObfNames.onHud_OWNER, ObfNames.onHud_NAME, ObfNames.onHud_DESC, "onHud", true),
        new Target(ObfNames.onKey_OWNER, ObfNames.onKey_NAME, ObfNames.onKey_DESC, "onKey", false),
    };

    // Pre-load ASM classes so they are already in the JVM when ClassFileLoadHook fires.
    // Without this, loading ASM during a JVMTI class-retransform callback can deadlock.
    public static void preload() {
        try { new ClassWriter(ClassWriter.COMPUTE_MAXS); } catch (Throwable ignored) {}
        try { Class.forName("org.objectweb.asm.ClassReader",  true, HookTransformer.class.getClassLoader()); } catch (Throwable ignored) {}
        try { Class.forName("org.objectweb.asm.ClassVisitor", true, HookTransformer.class.getClassLoader()); } catch (Throwable ignored) {}
        try { Class.forName("org.objectweb.asm.MethodVisitor",true, HookTransformer.class.getClassLoader()); } catch (Throwable ignored) {}
        try { Class.forName("org.objectweb.asm.Opcodes",      true, HookTransformer.class.getClassLoader()); } catch (Throwable ignored) {}
        try { Class.forName("org.objectweb.asm.Type",         true, HookTransformer.class.getClassLoader()); } catch (Throwable ignored) {}
    }

    static Set<String> targetClassNames() {
        Set<String> out = new LinkedHashSet<>();
        for (Target t : TARGETS) out.add(t.owner.replace('/', '.'));
        return out;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                            ProtectionDomain domain, byte[] bytes) {
        List<Target> mine = new ArrayList<>();
        for (Target t : TARGETS) if (t.owner.equals(className)) mine.add(t);
        if (mine.isEmpty()) return null;
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            List<String> hooked = new ArrayList<>();
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                    for (Target t : mine) {
                        if (t.name.equals(name) && t.desc.equals(desc)) {
                            hooked.add(t.hook);
                            return new HookCall(mv, t);
                        }
                    }
                    return mv;
                }
            }, 0);
            if (hooked.isEmpty()) {
                Agent.log("No hook methods found in " + className);
                return null;
            }
            Agent.log("Hooked " + className + " -> " + hooked);
            return writer.toByteArray();
        } catch (Throwable t) {
            Agent.logError("transform " + className, t);
            return null;
        }
    }

    private static final class HookCall extends MethodVisitor {
        private final Target target;

        HookCall(MethodVisitor mv, Target target) {
            super(Opcodes.ASM9, mv);
            this.target = target;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            if (!target.atReturn) emit();
        }

        @Override
        public void visitInsn(int opcode) {
            if (target.atReturn && opcode == Opcodes.RETURN) emit();
            super.visitInsn(opcode);
        }

        private void emit() {
            int slot = 1;
            for (Type arg : Type.getArgumentTypes(target.desc)) {
                super.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot);
                slot += arg.getSize();
            }
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, target.hook, target.desc, false);
        }
    }
}
