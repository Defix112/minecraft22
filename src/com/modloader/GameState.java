package com.modloader;

// Shared state between Java agent and C++ overlay DLL via JNI.
// Fields are written on the game thread (onTick); C++ reads them from its own thread.
public final class GameState {

    public static volatile double playerX;
    public static volatile double playerY;
    public static volatile double playerZ;

    // Comma-separated "Name:1" or "Name:0" pairs, updated each tick
    public static volatile String moduleStates = "";

    // C++ writes a module name here; onTick toggles it and clears
    public static volatile String pendingToggle = "";

    public static volatile boolean active;

    private GameState() {}
}
