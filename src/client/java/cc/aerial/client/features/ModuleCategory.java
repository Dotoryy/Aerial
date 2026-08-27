package cc.aerial.client.features;

public enum ModuleCategory {
    COMBAT("Combat", (char) 0xE9E0),
    MOVEMENT("Movement", (char) 0xE566),
    VISUAL("Visual", (char) 0xE8F4),
    WORLD("World", (char) 0xE80B),
    UTILITY("Utility", (char) 0xEA3C),

    SCRIPTS("Scripts", (char) 0xE86F);

    private final String name;
    private final char icon;

    ModuleCategory(String name, char icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public char getIcon() {
        return icon;
    }

    public static final ModuleCategory[] VALUES = values();
}
