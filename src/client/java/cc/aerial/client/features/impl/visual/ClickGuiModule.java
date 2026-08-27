package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.screen.ClickGuiState;

public final class ClickGuiModule extends Module {
    public static final ClickGuiModule INSTANCE = new ClickGuiModule();

    private final ModeProperty<Layout> layout = new ModeProperty<>("Layout", Layout.RAIL);

    private final BooleanProperty allowMovement = new BooleanProperty("Allow Movement", true);

    private ClickGuiModule() {
        super("Click GUI", "Opens the interface, and holds its own settings", ModuleCategory.VISUAL);
        addProperties(layout, allowMovement);
    }

    @Override
    protected void onEnable() {
        ClickGuiState.toggleScreen();

        setEnabled(false);
    }

    public boolean isAllowMovement() {
        return allowMovement.getValue();
    }

    public boolean isRailLayout() {
        return layout.getValue() == Layout.RAIL;
    }

    public enum Layout {
        RAIL("Rail"),
        CLASSIC("Classic");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
