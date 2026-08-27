package cc.aerial.client.target;

import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.MultipleBooleanProperty;

public final class TargetProperty {
    private final MultipleBooleanProperty property;
    private final boolean allowLocalPlayer;

    public TargetProperty(boolean players, boolean allowLocalPlayer, boolean localPlayer, boolean hostile, boolean passive, boolean friendly) {
        this.allowLocalPlayer = allowLocalPlayer;

        BooleanProperty playersProperty = new BooleanProperty("Players", players);
        BooleanProperty localPlayerProperty = new BooleanProperty("Local player", localPlayer)
                .hideIf(() -> !playersProperty.getValue() || !allowLocalPlayer);

        this.property = new MultipleBooleanProperty("Targets",
                playersProperty,
                new BooleanProperty("Hostile", hostile),
                new BooleanProperty("Passive", passive),
                new BooleanProperty("Friendly", friendly),
                localPlayerProperty
        );
    }

    public MultipleBooleanProperty get() {
        return this.property;
    }

    public int getTargetFlags() {
        return TargetFlags.get(
                this.property.getProperty("Players").getValue(),
                this.property.getProperty("Hostile").getValue(),
                this.property.getProperty("Passive").getValue(),
                this.property.getProperty("Friendly").getValue()
        );
    }

    public boolean isLocalPlayer() {
        return this.allowLocalPlayer && this.property.getProperty("Local player").getValue();
    }
}
