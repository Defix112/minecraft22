package com.modloader;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.*;

public class Overlay {

    static volatile boolean visible = false;
    private static boolean glReady = false;
    private static long windowHandle;

    static boolean flyOn, speedOn, sprintOn, autoClickOn, fullbrightOn;
    private static int selectedCat = 0;

    private static final String[] CATS = {"Movement", "Combat", "Render"};
    private static final String[][] MOD_NAMES = {
        {"Fly", "Speed", "Sprint"},
        {"AutoClick"},
        {"Fullbright"}
    };
    private static final String[][] MOD_DESCS = {
        {"Fly in survival", "Move 3x faster", "Always sprint"},
        {"Fast click 12 CPS"},
        {"Max brightness"}
    };

    private static MethodHandle hGlEnable, hGlDisable, hGlBegin, hGlEnd;
    private static MethodHandle hGlVertex2f, hGlColor4f;
    private static MethodHandle hGlPushMatrix, hGlPopMatrix;
    private static MethodHandle hGlMatrixMode, hGlLoadIdentity, hGlOrtho;
    private static MethodHandle hGlBlendFunc, hGlPushAttrib, hGlPopAttrib;
    private static MethodHandle hGlLineWidth;

    private static int GL_QUADS, GL_BLEND, GL_DEPTH_TEST, GL_TEXTURE_2D;
    private static int GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA;
    private static int GL_PROJECTION, GL_MODELVIEW;
    private static int GL_ALL_ATTRIB_BITS;

    private static Method mGetCursorPos, mGetMouseButton, mGetFbSize, mSetInputMode;
    private static int GLFW_CURSOR, GLFW_CURSOR_NORMAL, GLFW_CURSOR_DISABLED;
    private static int GLFW_PRESS, GLFW_MOUSE_BUTTON_LEFT;
    private static int GLFW_KEY_RIGHT_SHIFT;

    private static DoubleBuffer mxBuf, myBuf;
    private static IntBuffer wBuf, hBuf;

    private static double mouseX, mouseY;
    private static int screenW = 800, screenH = 600;
    private static boolean mouseDown, mouseClicked;
    private static boolean prevMouseDown;

    private static final int GUI_W = 320, GUI_H = 220;
    private static int guiX, guiY;

    static boolean init(long window) {
        windowHandle = window;
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = ClassLoader.getSystemClassLoader();

            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11", true, cl);
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            hGlEnable = lookup.findStatic(gl11, "glEnable", MethodType.methodType(void.class, int.class));
            hGlDisable = lookup.findStatic(gl11, "glDisable", MethodType.methodType(void.class, int.class));
            hGlBegin = lookup.findStatic(gl11, "glBegin", MethodType.methodType(void.class, int.class));
            hGlEnd = lookup.findStatic(gl11, "glEnd", MethodType.methodType(void.class));
            hGlVertex2f = lookup.findStatic(gl11, "glVertex2f", MethodType.methodType(void.class, float.class, float.class));
            hGlColor4f = lookup.findStatic(gl11, "glColor4f", MethodType.methodType(void.class, float.class, float.class, float.class, float.class));
            hGlPushMatrix = lookup.findStatic(gl11, "glPushMatrix", MethodType.methodType(void.class));
            hGlPopMatrix = lookup.findStatic(gl11, "glPopMatrix", MethodType.methodType(void.class));
            hGlMatrixMode = lookup.findStatic(gl11, "glMatrixMode", MethodType.methodType(void.class, int.class));
            hGlLoadIdentity = lookup.findStatic(gl11, "glLoadIdentity", MethodType.methodType(void.class));
            hGlOrtho = lookup.findStatic(gl11, "glOrtho", MethodType.methodType(void.class, double.class, double.class, double.class, double.class, double.class, double.class));
            hGlBlendFunc = lookup.findStatic(gl11, "glBlendFunc", MethodType.methodType(void.class, int.class, int.class));
            hGlPushAttrib = lookup.findStatic(gl11, "glPushAttrib", MethodType.methodType(void.class, int.class));
            hGlPopAttrib = lookup.findStatic(gl11, "glPopAttrib", MethodType.methodType(void.class));
            hGlLineWidth = lookup.findStatic(gl11, "glLineWidth", MethodType.methodType(void.class, float.class));

            GL_QUADS = gl11.getField("GL_QUADS").getInt(null);
            GL_BLEND = gl11.getField("GL_BLEND").getInt(null);
            GL_DEPTH_TEST = gl11.getField("GL_DEPTH_TEST").getInt(null);
            GL_TEXTURE_2D = gl11.getField("GL_TEXTURE_2D").getInt(null);
            GL_SRC_ALPHA = gl11.getField("GL_SRC_ALPHA").getInt(null);
            GL_ONE_MINUS_SRC_ALPHA = gl11.getField("GL_ONE_MINUS_SRC_ALPHA").getInt(null);
            GL_PROJECTION = gl11.getField("GL_PROJECTION").getInt(null);
            GL_MODELVIEW = gl11.getField("GL_MODELVIEW").getInt(null);
            GL_ALL_ATTRIB_BITS = gl11.getField("GL_ALL_ATTRIB_BITS").getInt(null);

            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", true, cl);
            mGetCursorPos = glfw.getMethod("glfwGetCursorPos", long.class, DoubleBuffer.class, DoubleBuffer.class);
            mGetMouseButton = glfw.getMethod("glfwGetMouseButton", long.class, int.class);
            mGetFbSize = glfw.getMethod("glfwGetFramebufferSize", long.class, IntBuffer.class, IntBuffer.class);
            mSetInputMode = glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class);

            GLFW_CURSOR = glfw.getField("GLFW_CURSOR").getInt(null);
            GLFW_CURSOR_NORMAL = glfw.getField("GLFW_CURSOR_NORMAL").getInt(null);
            GLFW_CURSOR_DISABLED = glfw.getField("GLFW_CURSOR_DISABLED").getInt(null);
            GLFW_PRESS = glfw.getField("GLFW_PRESS").getInt(null);
            GLFW_MOUSE_BUTTON_LEFT = glfw.getField("GLFW_MOUSE_BUTTON_LEFT").getInt(null);
            GLFW_KEY_RIGHT_SHIFT = glfw.getField("GLFW_KEY_RIGHT_SHIFT").getInt(null);

            ByteBuffer bb = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
            mxBuf = bb.asDoubleBuffer();
            bb = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
            myBuf = bb.asDoubleBuffer();
            bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            wBuf = bb.asIntBuffer();
            bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            hBuf = bb.asIntBuffer();

            glReady = true;
            Agent.writeLog("Overlay GL init OK");
            return true;
        } catch (Throwable e) {
            Agent.writeLog("Overlay GL init FAILED: " + e);
            return false;
        }
    }

    public static void onFrame() {
        if (!glReady || !visible) return;
        try {
            updateScreen();
            updateMouse();

            guiX = (screenW - GUI_W) / 2;
            guiY = (screenH - GUI_H) / 2;

            hGlPushAttrib.invokeExact(GL_ALL_ATTRIB_BITS);
            hGlMatrixMode.invokeExact(GL_PROJECTION);
            hGlPushMatrix.invokeExact();
            hGlLoadIdentity.invokeExact();
            hGlOrtho.invokeExact(0.0, (double) screenW, (double) screenH, 0.0, -1.0, 1.0);
            hGlMatrixMode.invokeExact(GL_MODELVIEW);
            hGlPushMatrix.invokeExact();
            hGlLoadIdentity.invokeExact();

            hGlDisable.invokeExact(GL_DEPTH_TEST);
            hGlDisable.invokeExact(GL_TEXTURE_2D);
            hGlEnable.invokeExact(GL_BLEND);
            hGlBlendFunc.invokeExact(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            drawGui();

            hGlMatrixMode.invokeExact(GL_PROJECTION);
            hGlPopMatrix.invokeExact();
            hGlMatrixMode.invokeExact(GL_MODELVIEW);
            hGlPopMatrix.invokeExact();
            hGlPopAttrib.invokeExact();

            mouseClicked = false;
        } catch (Throwable t) {
            // ignore render errors
        }
    }

    public static void onKey(int key, int action) {
        if (key == GLFW_KEY_RIGHT_SHIFT && action == GLFW_PRESS) {
            visible = !visible;
            try {
                if (visible) {
                    mSetInputMode.invoke(null, windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                } else {
                    mSetInputMode.invoke(null, windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                }
            } catch (Exception e) {}
        }
    }

    static boolean isModEnabled(int cat, int mod) {
        if (cat == 0) {
            if (mod == 0) return flyOn;
            if (mod == 1) return speedOn;
            if (mod == 2) return sprintOn;
        } else if (cat == 1) {
            if (mod == 0) return autoClickOn;
        } else if (cat == 2) {
            if (mod == 0) return fullbrightOn;
        }
        return false;
    }

    static void toggleMod(int cat, int mod) {
        if (cat == 0) {
            if (mod == 0) flyOn = !flyOn;
            else if (mod == 1) speedOn = !speedOn;
            else if (mod == 2) sprintOn = !sprintOn;
        } else if (cat == 1) {
            if (mod == 0) autoClickOn = !autoClickOn;
        } else if (cat == 2) {
            if (mod == 0) fullbrightOn = !fullbrightOn;
        }
        Agent.onModuleToggle(cat, mod);
    }

    private static void drawGui() throws Throwable {
        int x = guiX, y = guiY;

        drawRect(x, y, GUI_W, GUI_H, 12, 12, 24, 230);

        drawRect(x, y, GUI_W, 28, 18, 18, 34, 255);
        drawRect(x, y + 28, GUI_W, 2, 80, 130, 255, 255);

        drawString(x + 10, y + 8, "CLICKGUI", 80, 140, 255, 2.0f);
        drawString(x + 130, y + 12, "RSHIFT=TOGGLE", 70, 70, 100, 1.0f);

        int leftW = 90;
        int catY = y + 34;
        for (int i = 0; i < CATS.length; i++) {
            int cy = catY + i * 56;
            boolean sel = (i == selectedCat);
            boolean hov = inRect(mouseX, mouseY, x + 4, cy, leftW, 50);

            if (mouseClicked && hov) selectedCat = i;

            drawRect(x + 4, cy, leftW, 50, sel ? 28 : hov ? 22 : 16, sel ? 28 : hov ? 22 : 16, sel ? 50 : hov ? 42 : 32, 255);

            if (sel) drawRect(x + 4, cy, 3, 50, catColor(i, 0), catColor(i, 1), catColor(i, 2), 255);

            drawString(x + 16, cy + 20, CATS[i].toUpperCase(), sel ? 255 : 180, sel ? 255 : 180, sel ? 255 : 200, 1.5f);
        }

        int rightX = x + leftW + 10;
        int rightW = GUI_W - leftW - 16;
        int modY = y + 34;

        String[] mods = MOD_NAMES[selectedCat];
        String[] descs = MOD_DESCS[selectedCat];

        for (int i = 0; i < mods.length; i++) {
            int my = modY + i * 50;
            boolean enabled = isModEnabled(selectedCat, i);
            boolean hov = inRect(mouseX, mouseY, rightX, my, rightW, 44);

            if (mouseClicked && hov) toggleMod(selectedCat, i);

            drawRect(rightX, my, rightW, 44, hov ? 26 : 18, hov ? 26 : 18, hov ? 46 : 34, 255);

            if (enabled) drawRect(rightX, my, rightW, 44, 60, 200, 100, 30);

            int tx = rightX + rightW - 44;
            int ty = my + 14;
            if (enabled) {
                drawRect(tx, ty, 36, 18, 60, 200, 100, 255);
                drawRect(tx + 18, ty + 2, 14, 14, 255, 255, 255, 255);
            } else {
                drawRect(tx, ty, 36, 18, 50, 50, 70, 255);
                drawRect(tx + 2, ty + 2, 14, 14, 80, 80, 100, 255);
            }

            drawString(rightX + 10, my + 12, mods[i].toUpperCase(), enabled ? 255 : 160, enabled ? 255 : 160, enabled ? 255 : 180, 1.5f);
            drawString(rightX + 10, my + 30, descs[i].toUpperCase(), 65, 65, 90, 1.0f);
        }

        drawString(x + GUI_W - 26, y + GUI_H - 10, "V2", 40, 40, 60, 1.0f);
    }

    private static int catColor(int cat, int ch) {
        int[][] c = {{255, 75, 120}, {255, 75, 75}, {75, 200, 255}};
        return c[cat][ch];
    }

    private static void drawRect(int x, int y, int w, int h, int r, int g, int b, int a) throws Throwable {
        hGlColor4f.invokeExact(r / 255f, g / 255f, b / 255f, a / 255f);
        hGlBegin.invokeExact(GL_QUADS);
        hGlVertex2f.invokeExact((float) x, (float) y);
        hGlVertex2f.invokeExact((float) (x + w), (float) y);
        hGlVertex2f.invokeExact((float) (x + w), (float) (y + h));
        hGlVertex2f.invokeExact((float) x, (float) (y + h));
        hGlEnd.invokeExact();
    }

    private static void drawString(int x, int y, String text, int r, int g, int b, float scale) throws Throwable {
        hGlColor4f.invokeExact(r / 255f, g / 255f, b / 255f, 1.0f);
        hGlBegin.invokeExact(GL_QUADS);
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int idx = ch < 128 ? ch : 0;
            if (idx < FONT.length && FONT[idx] != null) {
                int[] glyph = FONT[idx];
                for (int row = 0; row < 7; row++) {
                    for (int col = 0; col < 5; col++) {
                        if ((glyph[row] & (1 << (4 - col))) != 0) {
                            float px = cx + col * scale;
                            float py = y + row * scale;
                            hGlVertex2f.invokeExact(px, py);
                            hGlVertex2f.invokeExact(px + scale, py);
                            hGlVertex2f.invokeExact(px + scale, py + scale);
                            hGlVertex2f.invokeExact(px, py + scale);
                        }
                    }
                }
            }
            cx += 6 * scale;
        }
        hGlEnd.invokeExact();
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static void updateScreen() {
        try {
            wBuf.clear();
            hBuf.clear();
            mGetFbSize.invoke(null, windowHandle, wBuf, hBuf);
            screenW = wBuf.get(0);
            screenH = hBuf.get(0);
        } catch (Exception e) {}
    }

    private static void updateMouse() {
        try {
            mxBuf.clear();
            myBuf.clear();
            mGetCursorPos.invoke(null, windowHandle, mxBuf, myBuf);
            mouseX = mxBuf.get(0);
            mouseY = myBuf.get(0);
            int btn = (int) mGetMouseButton.invoke(null, windowHandle, GLFW_MOUSE_BUTTON_LEFT);
            mouseDown = (btn == GLFW_PRESS);
            mouseClicked = mouseDown && !prevMouseDown;
            prevMouseDown = mouseDown;
        } catch (Exception e) {}
    }

    private static final int[][] FONT = new int[128][];
    static {
        FONT[' '] = new int[]{0,0,0,0,0,0,0};
        FONT['!'] = new int[]{0x04,0x04,0x04,0x04,0x04,0x00,0x04};
        FONT['-'] = new int[]{0,0,0,0x0E,0,0,0};
        FONT[':'] = new int[]{0,0x04,0x04,0,0x04,0x04,0};
        FONT['.'] = new int[]{0,0,0,0,0,0,0x04};
        FONT['='] = new int[]{0,0,0x1F,0,0x1F,0,0};
        FONT['0'] = new int[]{0x0E,0x11,0x13,0x15,0x19,0x11,0x0E};
        FONT['1'] = new int[]{0x04,0x0C,0x04,0x04,0x04,0x04,0x0E};
        FONT['2'] = new int[]{0x0E,0x11,0x01,0x02,0x04,0x08,0x1F};
        FONT['3'] = new int[]{0x0E,0x11,0x01,0x06,0x01,0x11,0x0E};
        FONT['4'] = new int[]{0x02,0x06,0x0A,0x12,0x1F,0x02,0x02};
        FONT['5'] = new int[]{0x1F,0x10,0x1E,0x01,0x01,0x11,0x0E};
        FONT['6'] = new int[]{0x06,0x08,0x10,0x1E,0x11,0x11,0x0E};
        FONT['7'] = new int[]{0x1F,0x01,0x02,0x04,0x08,0x08,0x08};
        FONT['8'] = new int[]{0x0E,0x11,0x11,0x0E,0x11,0x11,0x0E};
        FONT['9'] = new int[]{0x0E,0x11,0x11,0x0F,0x01,0x02,0x0C};
        FONT['A'] = new int[]{0x0E,0x11,0x11,0x1F,0x11,0x11,0x11};
        FONT['B'] = new int[]{0x1E,0x11,0x11,0x1E,0x11,0x11,0x1E};
        FONT['C'] = new int[]{0x0E,0x11,0x10,0x10,0x10,0x11,0x0E};
        FONT['D'] = new int[]{0x1C,0x12,0x11,0x11,0x11,0x12,0x1C};
        FONT['E'] = new int[]{0x1F,0x10,0x10,0x1E,0x10,0x10,0x1F};
        FONT['F'] = new int[]{0x1F,0x10,0x10,0x1E,0x10,0x10,0x10};
        FONT['G'] = new int[]{0x0E,0x11,0x10,0x17,0x11,0x11,0x0F};
        FONT['H'] = new int[]{0x11,0x11,0x11,0x1F,0x11,0x11,0x11};
        FONT['I'] = new int[]{0x0E,0x04,0x04,0x04,0x04,0x04,0x0E};
        FONT['J'] = new int[]{0x07,0x02,0x02,0x02,0x02,0x12,0x0C};
        FONT['K'] = new int[]{0x11,0x12,0x14,0x18,0x14,0x12,0x11};
        FONT['L'] = new int[]{0x10,0x10,0x10,0x10,0x10,0x10,0x1F};
        FONT['M'] = new int[]{0x11,0x1B,0x15,0x15,0x11,0x11,0x11};
        FONT['N'] = new int[]{0x11,0x19,0x19,0x15,0x13,0x13,0x11};
        FONT['O'] = new int[]{0x0E,0x11,0x11,0x11,0x11,0x11,0x0E};
        FONT['P'] = new int[]{0x1E,0x11,0x11,0x1E,0x10,0x10,0x10};
        FONT['Q'] = new int[]{0x0E,0x11,0x11,0x11,0x15,0x12,0x0D};
        FONT['R'] = new int[]{0x1E,0x11,0x11,0x1E,0x14,0x12,0x11};
        FONT['S'] = new int[]{0x0E,0x11,0x10,0x0E,0x01,0x11,0x0E};
        FONT['T'] = new int[]{0x1F,0x04,0x04,0x04,0x04,0x04,0x04};
        FONT['U'] = new int[]{0x11,0x11,0x11,0x11,0x11,0x11,0x0E};
        FONT['V'] = new int[]{0x11,0x11,0x11,0x11,0x0A,0x0A,0x04};
        FONT['W'] = new int[]{0x11,0x11,0x11,0x15,0x15,0x1B,0x11};
        FONT['X'] = new int[]{0x11,0x11,0x0A,0x04,0x0A,0x11,0x11};
        FONT['Y'] = new int[]{0x11,0x11,0x0A,0x04,0x04,0x04,0x04};
        FONT['Z'] = new int[]{0x1F,0x01,0x02,0x04,0x08,0x10,0x1F};
    }
}
