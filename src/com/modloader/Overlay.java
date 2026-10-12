package com.modloader;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.*;

public class Overlay {

    static volatile boolean visible = false;
    private static boolean glReady = false;
    private static long windowHandle;
    private static int selectedCat = 0;

    private static final String[] CATS = {"Combat", "Movement", "Render"};
    private static final int[][] CAT_ICONS = {
        {0x04,0x0E,0x1F,0x0E,0x04,0x04,0x04},
        {0x04,0x0E,0x15,0x04,0x04,0x04,0x0E},
        {0x0E,0x11,0x11,0x11,0x0E,0x04,0x04}
    };
    private static final String[][] MOD_NAMES = {
        {"AutoClicker"},
        {"Fly", "Speed", "Sprint"},
        {"Fullbright"}
    };
    private static final String[][] MOD_DESCS = {
        {"Fast left-click 12 CPS"},
        {"Creative fly in survival", "Walk speed x3 boost", "Auto sprint always on"},
        {"Max gamma brightness"}
    };

    private static final int ACCENT_R = 100, ACCENT_G = 120, ACCENT_B = 255;
    private static final int BG_R = 18, BG_G = 18, BG_B = 26;
    private static final int SIDEBAR_R = 14, SIDEBAR_G = 14, SIDEBAR_B = 22;
    private static final int CARD_R = 28, CARD_G = 28, CARD_B = 40;
    private static final int BORDER_R = 35, BORDER_G = 35, BORDER_B = 50;

    private static MethodHandle hGlEnable, hGlDisable, hGlBegin, hGlEnd;
    private static MethodHandle hGlVertex2f, hGlColor4f;
    private static MethodHandle hGlPushMatrix, hGlPopMatrix;
    private static MethodHandle hGlMatrixMode, hGlLoadIdentity, hGlOrtho;
    private static MethodHandle hGlBlendFunc, hGlPushAttrib, hGlPopAttrib;
    private static MethodHandle hGlUseProgram;
    private static MethodHandle hGlBindVertexArray;

    private static int GL_QUADS, GL_BLEND, GL_DEPTH_TEST, GL_TEXTURE_2D;
    private static int GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA;
    private static int GL_PROJECTION, GL_MODELVIEW;
    private static int GL_ALL_ATTRIB_BITS;

    private static Method mGetCursorPos, mGetMouseButton, mGetFbSize, mSetInputMode, mGetWindowSize;
    private static int GLFW_CURSOR, GLFW_CURSOR_NORMAL, GLFW_CURSOR_DISABLED;
    private static int GLFW_PRESS, GLFW_MOUSE_BUTTON_LEFT, GLFW_KEY_RIGHT_SHIFT;

    private static DoubleBuffer mxBuf, myBuf;
    private static IntBuffer wBuf, hBuf, winWBuf, winHBuf;

    private static double mouseX, mouseY;
    private static int screenW = 800, screenH = 600;
    private static int windowW = 800, windowH = 600;
    private static boolean mouseDown, mouseClicked, prevMouseDown;

    private static final int GUI_W = 480, GUI_H = 320;
    private static final int SIDEBAR_W = 140, HEADER_H = 44;
    private static final int MOD_H = 52, MOD_GAP = 6;
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

            try {
                Class<?> gl20 = Class.forName("org.lwjgl.opengl.GL20", true, cl);
                hGlUseProgram = lookup.findStatic(gl20, "glUseProgram", MethodType.methodType(void.class, int.class));
            } catch (Throwable ignored) {}

            try {
                Class<?> gl30 = Class.forName("org.lwjgl.opengl.GL30", true, cl);
                hGlBindVertexArray = lookup.findStatic(gl30, "glBindVertexArray", MethodType.methodType(void.class, int.class));
            } catch (Throwable ignored) {}

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
            mGetWindowSize = glfw.getMethod("glfwGetWindowSize", long.class, IntBuffer.class, IntBuffer.class);

            GLFW_CURSOR = glfw.getField("GLFW_CURSOR").getInt(null);
            GLFW_CURSOR_NORMAL = glfw.getField("GLFW_CURSOR_NORMAL").getInt(null);
            GLFW_CURSOR_DISABLED = glfw.getField("GLFW_CURSOR_DISABLED").getInt(null);
            GLFW_PRESS = glfw.getField("GLFW_PRESS").getInt(null);
            GLFW_MOUSE_BUTTON_LEFT = glfw.getField("GLFW_MOUSE_BUTTON_LEFT").getInt(null);
            GLFW_KEY_RIGHT_SHIFT = glfw.getField("GLFW_KEY_RIGHT_SHIFT").getInt(null);

            ByteBuffer bb;
            bb = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()); mxBuf = bb.asDoubleBuffer();
            bb = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()); myBuf = bb.asDoubleBuffer();
            bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()); wBuf = bb.asIntBuffer();
            bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()); hBuf = bb.asIntBuffer();
            bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()); winWBuf = bb.asIntBuffer();
            bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()); winHBuf = bb.asIntBuffer();

            glReady = true;
            Agent.writeLog("Overlay GL init OK (shader unbind: " + (hGlUseProgram != null) + ")");
            return true;
        } catch (Throwable e) {
            Agent.writeLog("Overlay GL init FAILED: " + e);
            return false;
        }
    }

    public static void onFrame() {
        if (!glReady) return;
        try {
            updateScreen();

            hGlPushAttrib.invokeExact(GL_ALL_ATTRIB_BITS);

            if (hGlUseProgram != null) hGlUseProgram.invokeExact(0);
            if (hGlBindVertexArray != null) hGlBindVertexArray.invokeExact(0);

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

            if (visible) {
                updateMouse();
                guiX = (screenW - GUI_W) / 2;
                guiY = (screenH - GUI_H) / 2;
                drawRect(0, 0, screenW, screenH, 0, 0, 0, 120);
                drawGui();
            } else {
                drawWatermark();
                drawArrayList();
            }

            hGlMatrixMode.invokeExact(GL_PROJECTION);
            hGlPopMatrix.invokeExact();
            hGlMatrixMode.invokeExact(GL_MODELVIEW);
            hGlPopMatrix.invokeExact();
            hGlPopAttrib.invokeExact();

            mouseClicked = false;
        } catch (Throwable t) {}
    }

    public static void onKey(int key, int action) {
        if (key == GLFW_KEY_RIGHT_SHIFT && action == GLFW_PRESS) {
            visible = !visible;
            try {
                mSetInputMode.invoke(null, windowHandle, GLFW_CURSOR,
                    visible ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
            } catch (Exception ignored) {}
        }
    }

    static boolean isModEnabled(int cat, int mod) {
        if (cat == 0) { if (mod == 0) return AutoClicker.enabled; }
        else if (cat == 1) {
            if (mod == 0) return Fly.enabled;
            if (mod == 1) return Speed.enabled;
            if (mod == 2) return Sprint.enabled;
        }
        else if (cat == 2) { if (mod == 0) return Fullbright.enabled; }
        return false;
    }

    static void toggleMod(int cat, int mod) {
        if (cat == 0 && mod == 0) Agent.onModuleToggle(1, 0);
        else if (cat == 1 && mod == 0) Agent.onModuleToggle(0, 0);
        else if (cat == 1 && mod == 1) Agent.onModuleToggle(0, 1);
        else if (cat == 1 && mod == 2) Agent.onModuleToggle(0, 2);
        else if (cat == 2 && mod == 0) Agent.onModuleToggle(2, 0);
    }

    private static void drawGui() throws Throwable {
        int x = guiX, y = guiY;

        drawRect(x - 2, y - 2, GUI_W + 4, GUI_H + 4, 0, 0, 0, 80);
        drawRect(x, y, GUI_W, GUI_H, BG_R, BG_G, BG_B, 245);
        drawRect(x, y, GUI_W, 3, ACCENT_R, ACCENT_G, ACCENT_B, 255);
        drawRect(x, y + 3, GUI_W, HEADER_H - 3, 22, 22, 32, 255);

        drawString(x + 16, y + 14, "SYSTEM", ACCENT_R, ACCENT_G, ACCENT_B, 2.5f);
        drawString(x + 112, y + 18, "CLIENT", 180, 180, 200, 1.5f);
        drawString(x + GUI_W - 110, y + 18, "RSHIFT", 50, 50, 70, 1.2f);

        drawRect(x, y + HEADER_H, GUI_W, 1, BORDER_R, BORDER_G, BORDER_B, 255);
        drawRect(x, y + HEADER_H + 1, SIDEBAR_W, GUI_H - HEADER_H - 1, SIDEBAR_R, SIDEBAR_G, SIDEBAR_B, 255);

        int catY = y + HEADER_H + 12;
        for (int i = 0; i < CATS.length; i++) {
            int cy = catY + i * 48;
            boolean sel = (i == selectedCat);
            boolean hov = inRect(mouseX, mouseY, x, cy - 4, SIDEBAR_W, 40);

            if (mouseClicked && hov) selectedCat = i;

            if (sel) {
                drawRect(x, cy - 4, SIDEBAR_W, 40, ACCENT_R, ACCENT_G, ACCENT_B, 20);
                drawRect(x, cy - 4, 3, 40, ACCENT_R, ACCENT_G, ACCENT_B, 255);
            } else if (hov) {
                drawRect(x, cy - 4, SIDEBAR_W, 40, 255, 255, 255, 8);
            }

            drawIcon(x + 16, cy + 4, CAT_ICONS[i],
                sel ? ACCENT_R : 90, sel ? ACCENT_G : 90, sel ? ACCENT_B : 110, 1.8f);

            int tr = sel ? 240 : hov ? 200 : 130;
            int tb = sel ? 255 : hov ? 210 : 150;
            drawString(x + 38, cy + 8, CATS[i].toUpperCase(), tr, tr, tb, 1.5f);
            drawString(x + SIDEBAR_W - 20, cy + 10, "" + MOD_NAMES[i].length, 60, 60, 80, 1.0f);
        }

        drawRect(x + SIDEBAR_W, y + HEADER_H + 1, 1, GUI_H - HEADER_H - 1, BORDER_R, BORDER_G, BORDER_B, 255);

        int contentX = x + SIDEBAR_W + 12;
        int contentW = GUI_W - SIDEBAR_W - 24;
        int modY = y + HEADER_H + 14;

        drawString(contentX, modY, CATS[selectedCat].toUpperCase(), 80, 80, 110, 1.2f);
        modY += 20;
        drawRect(contentX, modY, contentW, 1, BORDER_R, BORDER_G, BORDER_B, 255);
        modY += 8;

        String[] mods = MOD_NAMES[selectedCat];
        String[] descs = MOD_DESCS[selectedCat];

        for (int i = 0; i < mods.length; i++) {
            int my = modY + i * (MOD_H + MOD_GAP);
            boolean enabled = isModEnabled(selectedCat, i);
            boolean hov = inRect(mouseX, mouseY, contentX, my, contentW, MOD_H);

            if (mouseClicked && hov) toggleMod(selectedCat, i);

            drawRect(contentX, my, contentW, MOD_H, CARD_R, CARD_G, CARD_B, 200);
            if (hov) drawRect(contentX, my, contentW, MOD_H, 255, 255, 255, 12);
            if (enabled) drawRect(contentX, my, 3, MOD_H, ACCENT_R, ACCENT_G, ACCENT_B, 255);

            int nr = enabled ? 240 : 170;
            int nb = enabled ? 255 : 190;
            drawString(contentX + 14, my + 12, mods[i], nr, nr, nb, 1.8f);
            drawString(contentX + 14, my + 32, descs[i], 70, 70, 95, 1.0f);

            int toggleX = contentX + contentW - 48;
            int toggleY = my + 16;
            if (enabled) {
                drawRect(toggleX, toggleY, 36, 18, ACCENT_R, ACCENT_G, ACCENT_B, 255);
                drawRect(toggleX + 20, toggleY + 2, 14, 14, 255, 255, 255, 255);
            } else {
                drawRect(toggleX, toggleY, 36, 18, 45, 45, 60, 255);
                drawRect(toggleX + 2, toggleY + 2, 14, 14, 70, 70, 90, 255);
            }

            String st = enabled ? "ON" : "OFF";
            drawString(toggleX - 28, toggleY + 4, st,
                enabled ? ACCENT_R : 70, enabled ? ACCENT_G : 70, enabled ? ACCENT_B : 90, 1.0f);
        }

        drawRect(x, y + GUI_H - 24, GUI_W, 1, BORDER_R, BORDER_G, BORDER_B, 255);
        drawString(x + 12, y + GUI_H - 16, "v3.0", 45, 45, 65, 1.0f);
        int cnt = 0;
        for (int c = 0; c < MOD_NAMES.length; c++)
            for (int m = 0; m < MOD_NAMES[c].length; m++)
                if (isModEnabled(c, m)) cnt++;
        drawString(x + 60, y + GUI_H - 16, cnt + " ACTIVE", 45, 45, 65, 1.0f);
    }

    private static void drawWatermark() throws Throwable {
        drawRect(4, 4, 72, 18, 0, 0, 0, 100);
        drawString(8, 7, "SYSTEM", ACCENT_R, ACCENT_G, ACCENT_B, 1.6f);
    }

    private static void drawArrayList() throws Throwable {
        int ay = 4;
        for (int c = 0; c < MOD_NAMES.length; c++) {
            for (int m = 0; m < MOD_NAMES[c].length; m++) {
                if (isModEnabled(c, m)) {
                    String name = MOD_NAMES[c][m];
                    int tw = name.length() * 9;
                    int bw = tw + 12;
                    int bx = screenW - bw;
                    drawRect(bx, ay, bw, 16, 0, 0, 0, 130);
                    drawRect(screenW - 2, ay, 2, 16, ACCENT_R, ACCENT_G, ACCENT_B, 255);
                    drawString(bx + 4, ay + 3, name, 230, 230, 255, 1.5f);
                    ay += 18;
                }
            }
        }
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

    private static void drawIcon(int x, int y, int[] icon, int r, int g, int b, float scale) throws Throwable {
        hGlColor4f.invokeExact(r / 255f, g / 255f, b / 255f, 1.0f);
        hGlBegin.invokeExact(GL_QUADS);
        for (int row = 0; row < icon.length; row++) {
            for (int col = 0; col < 5; col++) {
                if ((icon[row] & (1 << (4 - col))) != 0) {
                    float px = x + col * scale;
                    float py = y + row * scale;
                    hGlVertex2f.invokeExact(px, py);
                    hGlVertex2f.invokeExact(px + scale, py);
                    hGlVertex2f.invokeExact(px + scale, py + scale);
                    hGlVertex2f.invokeExact(px, py + scale);
                }
            }
        }
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
            wBuf.clear(); hBuf.clear();
            mGetFbSize.invoke(null, windowHandle, wBuf, hBuf);
            screenW = wBuf.get(0);
            screenH = hBuf.get(0);
            winWBuf.clear(); winHBuf.clear();
            mGetWindowSize.invoke(null, windowHandle, winWBuf, winHBuf);
            windowW = winWBuf.get(0);
            windowH = winHBuf.get(0);
        } catch (Exception ignored) {}
    }

    private static void updateMouse() {
        try {
            mxBuf.clear(); myBuf.clear();
            mGetCursorPos.invoke(null, windowHandle, mxBuf, myBuf);
            double rawMx = mxBuf.get(0);
            double rawMy = myBuf.get(0);
            if (windowW > 0 && windowH > 0) {
                mouseX = rawMx * screenW / windowW;
                mouseY = rawMy * screenH / windowH;
            } else {
                mouseX = rawMx;
                mouseY = rawMy;
            }
            int btn = (int) mGetMouseButton.invoke(null, windowHandle, GLFW_MOUSE_BUTTON_LEFT);
            mouseDown = (btn == GLFW_PRESS);
            mouseClicked = mouseDown && !prevMouseDown;
            prevMouseDown = mouseDown;
        } catch (Exception ignored) {}
    }

    private static final int[][] FONT = new int[128][];
    static {
        FONT[' '] = new int[]{0,0,0,0,0,0,0};
        FONT['!'] = new int[]{0x04,0x04,0x04,0x04,0x04,0x00,0x04};
        FONT['-'] = new int[]{0,0,0,0x0E,0,0,0};
        FONT[':'] = new int[]{0,0x04,0x04,0,0x04,0x04,0};
        FONT['.'] = new int[]{0,0,0,0,0,0,0x04};
        FONT['='] = new int[]{0,0,0x1F,0,0x1F,0,0};
        FONT['('] = new int[]{0x02,0x04,0x08,0x08,0x08,0x04,0x02};
        FONT[')'] = new int[]{0x08,0x04,0x02,0x02,0x02,0x04,0x08};
        FONT['/'] = new int[]{0x01,0x01,0x02,0x04,0x08,0x10,0x10};
        FONT['+'] = new int[]{0x00,0x04,0x04,0x1F,0x04,0x04,0x00};
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
        FONT['a'] = new int[]{0x00,0x00,0x0E,0x01,0x0F,0x11,0x0F};
        FONT['b'] = new int[]{0x10,0x10,0x1E,0x11,0x11,0x11,0x1E};
        FONT['c'] = new int[]{0x00,0x00,0x0E,0x11,0x10,0x11,0x0E};
        FONT['d'] = new int[]{0x01,0x01,0x0F,0x11,0x11,0x11,0x0F};
        FONT['e'] = new int[]{0x00,0x00,0x0E,0x11,0x1F,0x10,0x0E};
        FONT['f'] = new int[]{0x06,0x08,0x08,0x1E,0x08,0x08,0x08};
        FONT['g'] = new int[]{0x00,0x00,0x0F,0x11,0x0F,0x01,0x0E};
        FONT['h'] = new int[]{0x10,0x10,0x1E,0x11,0x11,0x11,0x11};
        FONT['i'] = new int[]{0x04,0x00,0x0C,0x04,0x04,0x04,0x0E};
        FONT['j'] = new int[]{0x02,0x00,0x06,0x02,0x02,0x12,0x0C};
        FONT['k'] = new int[]{0x10,0x10,0x12,0x14,0x18,0x14,0x12};
        FONT['l'] = new int[]{0x0C,0x04,0x04,0x04,0x04,0x04,0x0E};
        FONT['m'] = new int[]{0x00,0x00,0x1A,0x15,0x15,0x11,0x11};
        FONT['n'] = new int[]{0x00,0x00,0x1E,0x11,0x11,0x11,0x11};
        FONT['o'] = new int[]{0x00,0x00,0x0E,0x11,0x11,0x11,0x0E};
        FONT['p'] = new int[]{0x00,0x00,0x1E,0x11,0x1E,0x10,0x10};
        FONT['q'] = new int[]{0x00,0x00,0x0F,0x11,0x0F,0x01,0x01};
        FONT['r'] = new int[]{0x00,0x00,0x16,0x19,0x10,0x10,0x10};
        FONT['s'] = new int[]{0x00,0x00,0x0E,0x10,0x0E,0x01,0x1E};
        FONT['t'] = new int[]{0x08,0x08,0x1E,0x08,0x08,0x09,0x06};
        FONT['u'] = new int[]{0x00,0x00,0x11,0x11,0x11,0x13,0x0D};
        FONT['v'] = new int[]{0x00,0x00,0x11,0x11,0x11,0x0A,0x04};
        FONT['w'] = new int[]{0x00,0x00,0x11,0x11,0x15,0x15,0x0A};
        FONT['x'] = new int[]{0x00,0x00,0x11,0x0A,0x04,0x0A,0x11};
        FONT['y'] = new int[]{0x00,0x00,0x11,0x11,0x0F,0x01,0x0E};
        FONT['z'] = new int[]{0x00,0x00,0x1F,0x02,0x04,0x08,0x1F};
    }
}
