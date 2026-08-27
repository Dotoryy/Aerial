package cc.aerial.client.features.impl.visual;

import cc.aerial.client.binding.BindRepository;
import cc.aerial.client.binding.BindingService;
import cc.aerial.client.binding.InputType;
import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MouseUpdateEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RotationHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;

public final class FreeLookModule extends Module {
    public static final FreeLookModule INSTANCE = new FreeLookModule();

    public enum Mode {
        TOGGLE, HOLD
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.TOGGLE);
    private final BooleanProperty autoThirdPerson = new BooleanProperty("Auto Third Person", true);
    private final NumberProperty maxHeadYaw = new NumberProperty("Max Head Yaw", 360.0, 30.0, 360.0, 5.0);

    private float cameraYaw;
    private float cameraPitch;
    private float anchorYaw;
    private boolean hasAnchor;
    private CameraType lastPerspective;

    private FreeLookModule() {
        super("Free Look", "Look around freely without turning your character", ModuleCategory.VISUAL);
        addProperties(mode, autoThirdPerson, maxHeadYaw);
        EventDispatcher.subscribe(new HoldWatcher());
    }

    public final class HoldWatcher implements IEventSubscriber {
        private boolean holdActive;

        @Subscribe
        public void onPreTick(PreGameTickEvent event) {
            if (mode.getValue() != Mode.HOLD) {
                holdActive = false;
                return;
            }
            boolean down = isBoundKeyDown();
            if (down != holdActive) {
                holdActive = down;
                setEnabled(down);
            }
        }

        @Override
        public boolean isHandlingEvents() {
            return true;
        }
    }

    private boolean isBoundKeyDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            return false;
        }
        BindingService.BindKey key = BindRepository.INSTANCE.getBindingService().getKeyFromBindable(this);
        if (key == null) {
            return false;
        }
        long window = mc.getWindow().handle();
        if (key.type() == InputType.KEYBOARD) {
            return GLFW.glfwGetKey(window, key.code()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetMouseButton(window, key.code()) == GLFW.GLFW_PRESS;
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        anchorYaw = cameraYaw;
        hasAnchor = true;

        lastPerspective = mc.options.getCameraType();
        if (autoThirdPerson.getValue() && lastPerspective == CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @Override
    protected void onDisable() {
        hasAnchor = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            RotationHelper.getClientHandler().setRotation(new Vec2(mc.player.getYRot(), mc.player.getXRot()));
        }

        if (autoThirdPerson.getValue() && lastPerspective != null) {
            mc.options.setCameraType(lastPerspective);
        }
        lastPerspective = null;
    }

    @Subscribe(priority = Integer.MIN_VALUE)
    public void onMouseUpdate(MouseUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.isUnlockCursorRun()) {
            return;
        }
        double multiplier = event.getSensitivityMultiplier();
        double xDelta = event.getDeltaX() * multiplier;
        double yDelta = event.getDeltaY() * multiplier;
        if (mc.options.invertMouseX().get()) {
            xDelta = -xDelta;
        }
        if (mc.options.invertMouseY().get()) {
            yDelta = -yDelta;
        }
        turn(xDelta, yDelta);
    }

    private void turn(double xDelta, double yDelta) {
        float pitchDelta = (float) yDelta * 0.15f;
        float yawDelta = (float) xDelta * 0.15f;

        if (!hasAnchor) {
            anchorYaw = cameraYaw;
            hasAnchor = true;
        }

        cameraPitch = Mth.clamp(cameraPitch + pitchDelta, -90.0f, 90.0f);
        double maxYaw = maxHeadYaw.getValue();
        if (maxYaw >= 360.0) {
            cameraYaw += yawDelta;
        } else {
            cameraYaw = Mth.clamp(cameraYaw + yawDelta, anchorYaw - (float) maxYaw, anchorYaw + (float) maxYaw);
        }
    }

    public boolean isFreeLooking() {
        return isEnabled();
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }
}
