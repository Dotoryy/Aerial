package cc.aerial.client.property;

public final class StringProperty extends Property<String> {
    private int cursor;

    public StringProperty(String name, String defaultValue) {
        super(name);
        setValue(defaultValue);
        this.cursor = defaultValue.length();
    }

    public int getCursor() {
        return cursor;
    }

    public void insertChar(char c) {
        String value = getValue();
        cursor = Math.max(0, Math.min(cursor, value.length()));
        setValue(value.substring(0, cursor) + c + value.substring(cursor));
        cursor++;
    }

    public void backspace() {
        String value = getValue();
        if (cursor <= 0 || value.isEmpty()) {
            return;
        }
        setValue(value.substring(0, cursor - 1) + value.substring(cursor));
        cursor--;
    }

    public void moveCursor(int delta) {
        cursor = Math.max(0, Math.min(getValue().length(), cursor + delta));
    }

    public void cursorToEnd() {
        cursor = getValue().length();
    }

    public void insert(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= ' ' && character != 127) {
                cleaned.append(character);
            }
        }
        if (cleaned.isEmpty()) {
            return;
        }
        String value = getValue();
        int position = Math.min(getCursor(), value.length());
        setValue(value.substring(0, position) + cleaned + value.substring(position));
        moveCursor(cleaned.length());
    }

    public void delete() {
        String value = getValue();
        int position = Math.min(getCursor(), value.length());
        if (position >= value.length()) {
            return;
        }
        setValue(value.substring(0, position) + value.substring(position + 1));
    }
}
