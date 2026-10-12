package com.modloader;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}
