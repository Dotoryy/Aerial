package cc.aerial.client.features.impl.visual;

record NameTagElement(NameTagIcon icon, String text, int color) {
    NameTagElement(NameTagIcon icon, int color) {
        this(icon, null, color);
    }

    NameTagElement(String text, int color) {
        this(null, text, color);
    }
}
