package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;

public final class AutoClickerModule extends Module {
    public static final AutoClickerModule INSTANCE = new AutoClickerModule();

    public enum Mode {
        SIMPLE("Simple"),

        NORMAL("Normal"),

        DRAG_CLICK("Drag Click");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.SIMPLE);

    private final BooleanProperty left = new BooleanProperty("Left", true);
    private final BooleanProperty right = new BooleanProperty("Right", false);
    private final MultipleBooleanProperty mouseButtons = new MultipleBooleanProperty("Mouse buttons", left, right);

    private final BooleanProperty modernDelay = new BooleanProperty("Modern delay", false).hideIf(() -> mode.getValue() != Mode.SIMPLE);
    private final NumberProperty cps = new NumberProperty("CPS", 10, 1, 20, 1).hideIf(() -> mode.getValue() != Mode.SIMPLE);
    private final GroupProperty cpsGroup = new GroupProperty("CPS", modernDelay, cps);

    private final BooleanProperty requirePressed = new BooleanProperty("Require pressed", true);

    private final NumberProperty normalCpsMin = new NumberProperty("CPS Min", 8, 1, 20, 1).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final NumberProperty normalCpsMax = new NumberProperty("CPS Max", 14, 1, 20, 1).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final BooleanProperty normalButterfly = new BooleanProperty("Butterfly", true).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final BooleanProperty normalHitSelect = new BooleanProperty("Hit Select", false).hideIf(() -> mode.getValue() != Mode.NORMAL);

    private final NumberProperty dragLength = new NumberProperty("Drag Click Length", 18, 1, 50, 1).hideIf(() -> mode.getValue() != Mode.DRAG_CLICK);
    private final NumberProperty dragDelay = new NumberProperty("Delay Between Dragging", 6, 1, 20, 1).hideIf(() -> mode.getValue() != Mode.DRAG_CLICK);

    private long nextClickTime;

    private int normalAttackTicks;
    private long normalNextSwingMs = 1;

    private int dragLengthTicks = -1;
    private int dragDelayTicks;
    private boolean dragClickThisTick;

    private AutoClickerModule() {
        super("Auto Clicker", "", ModuleCategory.COMBAT);
        cps.hideIf(() -> mode.getValue() != Mode.SIMPLE || modernDelay.getValue());
        addProperties(mode, mouseButtons, cpsGroup, requirePressed,
                normalCpsMin, normalCpsMax, normalButterfly, normalHitSelect, dragLength, dragDelay);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        normalAttackTicks = 0;
        normalNextSwingMs = 1;
        dragLengthTicks = -1;
        dragDelayTicks = 0;
        dragClickThisTick = false;
    }

    public boolean isLeftEnabled() {
        return left.getValue();
    }

    public boolean isRightEnabled() {
        return right.getValue();
    }

    public boolean isRequirePressed() {
        return requirePressed.getValue();
    }

    public boolean canClick() {
        return switch (mode.getValue()) {
            case SIMPLE -> canClickSimple();
            case NORMAL -> canClickNormal();
            case DRAG_CLICK -> dragClickThisTick;
        };
    }

    private boolean canClickSimple() {
        if (modernDelay.getValue()) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null && player.getAttackStrengthScale(0.5f) >= 1.0f;
        }
        long now = System.currentTimeMillis();
        if (now >= nextClickTime) {
            long delay = 1000L / Math.max(1, cps.getValue().intValue());
            long base = Math.max(nextClickTime, now - delay);
            nextClickTime = base + delay;
            return true;
        }
        return false;
    }

    private boolean canClickNormal() {
        long now = System.currentTimeMillis();
        if (now < nextClickTime) {
            return false;
        }
        normalAttackTicks++;
        LocalPlayer player = Minecraft.getInstance().player;
        boolean hitSelectOk = !normalHitSelect.getValue() || normalAttackTicks >= 10 || isHurtTimeTargetActive();
        if (player == null || !hitSelectOk) {
            return false;
        }
        long interval = normalNextSwingMs;
        if (interval >= 100 && normalButterfly.getValue()) {
            interval = (long) (Math.random() * 100.0);
        } else {
            double min = Math.min(normalCpsMin.getValue().doubleValue(), normalCpsMax.getValue().doubleValue());
            double max = Math.max(normalCpsMin.getValue().doubleValue(), normalCpsMax.getValue().doubleValue());
            double drawnCps = min + (max - min) * Math.random() * Math.random();
            interval = (long) (1000.0 / Math.max(0.1, drawnCps * 1.5));
        }
        normalNextSwingMs = interval;
        nextClickTime = now + interval;
        normalAttackTicks = 0;
        return true;
    }

    private static boolean isHurtTimeTargetActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
            return living.hurtTime > 0;
        }
        return false;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        dragClickThisTick = false;
        if (mode.getValue() != Mode.DRAG_CLICK || !Minecraft.getInstance().options.keyAttack.isDown()) {
            return;
        }
        if (dragLengthTicks < 0) {
            dragDelayTicks--;
            if (dragDelayTicks < 0) {
                dragDelayTicks = dragDelay.getValue().intValue();
                dragLengthTicks = dragLength.getValue().intValue();
            }
        } else if (Math.random() < 0.95) {
            dragLengthTicks--;
            dragClickThisTick = true;
        }
    }
}
