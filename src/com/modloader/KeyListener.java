package com.modloader;

import org.lwjgl.input.Keyboard;

public class KeyListener {

    private boolean guiOpen = false;
    private ClickGui clickGui = null;

    public void start() {
        System.out.println("[ModLoader] Слушатель клавиш запущен (Right Shift для ClickGui)");

        while (true) {
            try {
                if (Keyboard.isCreated()) {
                    if (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                        if (!guiOpen) {
                            guiOpen = true;
                            openGui();
                        }
                        while (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                            Thread.sleep(10);
                        }
                    } else if (guiOpen && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                        // ждём следующего нажатия
                    }
                }
                Thread.sleep(50);
            } catch (Exception e) {
                // LWJGL может быть недоступен, пробуем через reflection
                try {
                    handleKeyWithReflection();
                } catch (Exception ignored) {}
            }
        }
    }

    private void openGui() {
        if (clickGui == null) {
            clickGui = new ClickGui();
        }
        clickGui.toggle();
        guiOpen = false;
    }

    private void handleKeyWithReflection() throws Exception {
        Class<?> keyboardClass = Class.forName("org.lwjgl.input.Keyboard");
        java.lang.reflect.Method isKeyDown = keyboardClass.getMethod("isKeyDown", int.class);
        int KEY_RSHIFT = keyboardClass.getField("KEY_RSHIFT").getInt(null);

        boolean pressed = (boolean) isKeyDown.invoke(null, KEY_RSHIFT);
        if (pressed && !guiOpen) {
            guiOpen = true;
            openGui();
            Thread.sleep(200);
            guiOpen = false;
        }
        Thread.sleep(50);
    }
}
