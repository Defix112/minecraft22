package com.modloader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class GameHook {

    private final Instrumentation inst;

    private Object mcInstance;
    private Field playerField;
    private Field abilitiesField;

    private Object player;
    private Object abilities;

    private final List<Field> abilityBools = new ArrayList<>();
    private final List<Field> abilityFloats = new ArrayList<>();
    private final List<Boolean> origBools = new ArrayList<>();
    private final List<Float> origFloats = new ArrayList<>();

    private boolean ready = false;

    GameHook(Instrumentation inst) {
        this.inst = inst;
    }

    boolean scan() {
        try {
            openModules();

            mcInstance = findSingleton();
            if (mcInstance == null) {
                log("MC singleton not found");
                return false;
            }
            log("MC class: " + mcInstance.getClass().getName()
                + " (" + mcInstance.getClass().getDeclaredFields().length + " fields)");

            if (!findPlayerAndAbilities()) {
                log("Player/abilities not found (enter a world first)");
                return false;
            }

            ready = true;
            return true;
        } catch (Throwable t) {
            log("Scan error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    boolean refreshPlayer() {
        if (mcInstance == null) return false;
        try {
            if (playerField == null) {
                return findPlayerAndAbilities();
            }

            player = playerField.get(mcInstance);
            if (player == null) {
                ready = false;
                return false;
            }

            if (abilitiesField != null) {
                abilities = abilitiesField.get(player);
            }
            ready = abilities != null;
            return ready;
        } catch (Exception e) {
            ready = false;
            return false;
        }
    }

    void setFlying(boolean on) {
        if (abilities == null) return;
        try {
            for (Field f : abilityBools) {
                f.set(abilities, on);
            }
        } catch (Exception ignored) {}
    }

    void setSpeed(float speed) {
        if (abilities == null) return;
        try {
            for (Field f : abilityFloats) {
                f.set(abilities, speed);
            }
        } catch (Exception ignored) {}
    }

    void restore() {
        if (abilities == null) return;
        try {
            for (int i = 0; i < abilityBools.size() && i < origBools.size(); i++) {
                abilityBools.get(i).set(abilities, origBools.get(i));
            }
            for (int i = 0; i < abilityFloats.size() && i < origFloats.size(); i++) {
                abilityFloats.get(i).set(abilities, origFloats.get(i));
            }
        } catch (Exception ignored) {}
    }

    boolean isReady() { return ready; }

    // --- internals ---

    private void openModules() {
        try {
            java.lang.Module target = java.awt.GraphicsEnvironment.class.getModule();
            inst.redefineModule(target,
                Set.of(), Map.of(),
                Map.of("java.awt", Set.of(GameHook.class.getModule())),
                Set.of(), Map.of());
        } catch (Throwable ignored) {}
    }

    private Object findSingleton() {
        Class<?>[] all = inst.getAllLoadedClasses();
        Object best = null;
        int bestFields = 0;

        for (Class<?> cls : all) {
            String n = cls.getName();
            if (n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("sun.")
                || n.startsWith("com.sun.") || n.startsWith("jdk.") || n.startsWith("org.")
                || n.startsWith("com.google") || n.startsWith("com.mojang.")
                || n.startsWith("io.") || n.startsWith("it.") || n.contains("$")) continue;

            Field[] fields = cls.getDeclaredFields();
            if (fields.length < 35) continue;

            for (Field f : fields) {
                if (f.getType() == cls && Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        Object obj = f.get(null);
                        if (obj != null && fields.length > bestFields) {
                            best = obj;
                            bestFields = fields.length;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return best;
    }

    private boolean findPlayerAndAbilities() {
        if (mcInstance == null) return false;

        Class<?> mcClass = mcInstance.getClass();
        for (Field f : mcClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object val = f.get(mcInstance);
                if (val == null) continue;

                Object ab = searchAbilities(val);
                if (ab != null) {
                    playerField = f;
                    player = val;
                    abilities = ab;
                    cacheAbilityFields();
                    log("Player: " + val.getClass().getName());
                    log("Abilities: " + ab.getClass().getName()
                        + " (" + abilityBools.size() + " bools, " + abilityFloats.size() + " floats)");
                    ready = true;
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private Object searchAbilities(Object entity) {
        Class<?> cls = entity.getClass();
        Set<Class<?>> visited = new HashSet<>();
        while (cls != null && cls != Object.class && visited.add(cls)) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(entity);
                    if (val == null) continue;
                    if (isAbilities(val.getClass())) {
                        abilitiesField = f;
                        return val;
                    }
                } catch (Exception ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private boolean isAbilities(Class<?> cls) {
        Field[] fields = cls.getDeclaredFields();
        if (fields.length > 15 || fields.length < 4) return false;

        int bools = 0, floats = 0;
        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == boolean.class) bools++;
            if (f.getType() == float.class) floats++;
        }
        if (bools < 3 || floats < 2) return false;

        // verify float values look like speed defaults
        try {
            Object tmp = null;
            // try to get an instance to check default values
            // if we already have it, check the floats
        } catch (Exception ignored) {}

        return true;
    }

    private void cacheAbilityFields() {
        abilityBools.clear();
        abilityFloats.clear();
        origBools.clear();
        origFloats.clear();

        for (Field f : abilities.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                if (f.getType() == boolean.class) {
                    abilityBools.add(f);
                    origBools.add((Boolean) f.get(abilities));
                }
                if (f.getType() == float.class) {
                    abilityFloats.add(f);
                    origFloats.add((Float) f.get(abilities));
                }
            } catch (Exception ignored) {}
        }
    }

    private void log(String msg) {
        System.out.println("[GameHook] " + msg);
        Agent.writeLog("[Hook] " + msg);
    }
}
