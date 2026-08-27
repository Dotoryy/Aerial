package cc.aerial.client.rotation;

import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.rotation.handler.ClientRotationHandler;
import cc.aerial.client.rotation.handler.RotationMouseHandler;

public final class RotationHelper {
    private RotationHelper() {
    }

    private static final ClientRotationHandler clientHandler = new ClientRotationHandler();
    private static final RotationMouseHandler mouseHandler = new RotationMouseHandler();

    public static RotationMouseHandler getHandler() {
        return mouseHandler;
    }

    public static ClientRotationHandler getClientHandler() {
        return clientHandler;
    }

    public static float getScreenYaw(float realYaw) {
        if (isAimAssistActive()) {
            return realYaw;
        }
        return clientHandler.getYawOr(realYaw);
    }

    public static float getScreenPitch(float realPitch) {
        if (isAimAssistActive()) {
            return realPitch;
        }
        return clientHandler.getPitchOr(realPitch);
    }

    private static boolean isAimAssistActive() {
        KillauraModule module = KillauraModule.INSTANCE;
        return module.isEnabled() && !module.getSettings().isSilent()
                && mouseHandler.isActive() && mouseHandler.getOwner() == module;
    }
}
