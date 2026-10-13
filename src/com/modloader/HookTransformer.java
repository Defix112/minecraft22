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
        new Target(ObfNames.onHud_OWNER,  ObfNames.onHud_NAME,  ObfNames.onHud_DESC,  "onHud",  true),
        new Target(ObfNames.onKey_OWNER,  ObfNames.onKey_NAME,  ObfNames.onKey_DESC,  "onKey",  false),
    };

    // Pre-load every class that transform() will touch so that ClassFileLoadHook
    // never triggers class loading while the JVM holds its class-loading lock.
    // An anonymous inner class created inside the hook callback would also need
    // loading at that moment, causing a deadlock — hence HookClassVisitor is a
    // named static inner class that can be loaded here beforehand.
    public static void preload() {
        ClassLoader cl = HookTransformer.class.getClassLoader();
        String[] asm = {
            "org.objectweb.asm.ClassReader",
            "org.objectweb.asm.ClassWriter",
            "org.objectweb.asm.ClassVisitor",
            "org.objectweb.asm.MethodVisitor",
            "org.objectweb.asm.FieldVisitor",
            "org.objectweb.asm.Opcodes",
            "org.objectweb.asm.Type",
            "org.objectweb.asm.Label",
            "org.objectweb.asm.Symbol",
            "org.objectweb.asm.SymbolTable",
            "org.objectweb.asm.MethodWriter",
            "org.objectweb.asm.FieldWriter",
            "org.objectweb.asm.AnnotationWriter",
            "org.objectweb.asm.ByteVector",
            "org.objectweb.asm.Handle",
        };
        for (String c : asm) { try { Class.forName(c, true, cl); } catch (Throwable ignored) {} }

        String[] own = {
            "com.modloader.HookTransformer$Target",
            "com.modloader.HookTransformer$HookClassVisitor",
            "com.modloader.HookTransformer$HookCall",
            "com.modloader.Agent",
            "com.modloader.ObfNames",
        };
        for (String c : own) { try { Class.forName(c, true, cl); } catch (Throwable ignored) {} }

        // Warm-up: run a full transform() dry-run to trigger any remaining lazy loads
        // inside ASM (SymbolTable, MethodWriter internals, etc.) before the hook fires.
        try {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, ObfNames.onTick_OWNER, null, "java/lang/Object", null);
            MethodVisitor mv = cw.visitMethod(0, ObfNames.onTick_NAME, ObfNames.onTick_DESC, null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 100);
            mv.visitEnd();
            cw.visitEnd();
            new HookTransformer().transform(null, ObfNames.onTick_OWNER, null, null, cw.toByteArray());
        } catch (Throwable ignored) {}
    }

    static Set<String> targetClassNames() {
        Set<String> out = new LinkedHashSet<>();
        for (Target t : TARGETS) out.add(t.owner().replace('/', '.'));
        return out;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                            ProtectionDomain domain, byte[] bytes) {
        List<Target> mine = new ArrayList<>();
        for (Target t : TARGETS) if (t.owner().equals(className)) mine.add(t);
        if (mine.isEmpty()) return null;
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            List<String> hooked = new ArrayList<>();
            reader.accept(new HookClassVisitor(writer, mine, hooked), 0);
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

    // Named static inner class (not anonymous) so it can be loaded by Class.forName
    // in preload() before ClassFileLoadHook is registered.
    private static final class HookClassVisitor extends ClassVisitor {
        private final List<Target> mine;
        private final List<String> hooked;

        HookClassVisitor(ClassVisitor cv, List<Target> mine, List<String> hooked) {
            super(Opcodes.ASM9, cv);
            this.mine = mine;
            this.hooked = hooked;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            for (Target t : mine) {
                if (t.name().equals(name) && t.desc().equals(desc)) {
                    hooked.add(t.hook());
                    return new HookCall(mv, t);
                }
            }
            return mv;
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
            if (!target.atReturn()) emit();
        }

        @Override
        public void visitInsn(int opcode) {
            if (target.atReturn() && opcode == Opcodes.RETURN) emit();
            super.visitInsn(opcode);
        }

        private void emit() {
            int slot = 1;
            for (Type arg : Type.getArgumentTypes(target.desc())) {
                super.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot);
                slot += arg.getSize();
            }
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, target.hook(), target.desc(), false);
        }
    }
}
