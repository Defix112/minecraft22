package com.modloader;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

public class HookTransformer implements ClassFileTransformer {

    private static final String TARGET = "com/mojang/blaze3d/platform/Window";
    private static final String METHOD = "updateDisplay";

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (!TARGET.equals(className)) return null;
        try {
            Agent.writeLog("Transforming " + className);
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (METHOD.equals(name) && "()V".equals(descriptor)) {
                        Agent.writeLog("Hooking " + METHOD);
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "com/modloader/Overlay", "onFrame", "()V", false);
                            }
                        };
                    }
                    return mv;
                }
            }, 0);
            Agent.writeLog("Transform complete");
            return cw.toByteArray();
        } catch (Throwable t) {
            Agent.writeLog("Transform error: " + t);
            return null;
        }
    }

    private static class SafeClassWriter extends ClassWriter {
        SafeClassWriter(ClassReader cr, int flags) { super(cr, flags); }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (Throwable e) {
                return "java/lang/Object";
            }
        }
    }
}
