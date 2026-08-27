package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.rotation.RotationUtility;
import net.minecraft.world.phys.Vec2;
import cc.aerial.client.utility.InventoryUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class DisplaceModule extends Module {
    public static final DisplaceModule INSTANCE = new DisplaceModule();

    private static final int DISPLACE_WINDOW_TICKS = 10;

    private static final double TARGET_RANGE = 9.0;

    private final NumberProperty yawOffset = new NumberProperty("Yaw offset", 90, 0, 180, 1);
    private final NumberProperty delay = new NumberProperty("Delay", 0, 0, 500, 5);
    private final ModeProperty<Direction> direction = new ModeProperty<>("Direction", Direction.LEFT);
    private final BooleanProperty findVoid = new BooleanProperty("Find void", false);
    private final BooleanProperty blink = new BooleanProperty("Blink", false);
    private final BooleanProperty requireKnockback = new BooleanProperty("Has knockback", false);

    private boolean displaceThisTick;
    private boolean active;
    private boolean hasKnockbackEnchant;
    private boolean compensateNextTick;
    private boolean displaceLeft;
    private boolean wasDisplacingLastTick;
    private boolean releaseBlinkNextTick;
    private int tickCounter;

    private float displacedYaw;
    private float displacedPitch;

    private boolean rotationApplied;
    private float savedYaw;
    private float savedPitch;

    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<>();

    private final BlockHolder blinkHolder = new BlockHolder(NetworkDirection.OUTBOUND);

    private DisplaceModule() {
        super("Displace", "Angles your attacks so knockback lands sideways", ModuleCategory.COMBAT);
        addProperties(yawOffset, delay, direction, findVoid, blink, requireKnockback);
    }

    @Override
    public String getSuffix() {
        return Math.round(delay.getValue().doubleValue()) + "ms";
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    private void resetState() {
        displaceThisTick = false;
        active = false;
        hasKnockbackEnchant = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        releaseBlinkNextTick = false;
        tickCounter = 0;
        targetWindowStartTicks.clear();
        rotationApplied = false;
        releaseBlink();
    }

    private void releaseBlink() {
        if (blinkHolder.isBlocking()) {
            blinkHolder.release();
        }
    }

    @Subscribe(priority = 10)
    public void onPreGameTickBlink(PreGameTickEvent event) {
        if (releaseBlinkNextTick) {
            releaseBlink();
            releaseBlinkNextTick = false;
        }
    }

    public void applyRotation(LocalPlayer player) {
        if (!active || !displaceThisTick || rotationApplied) {
            return;
        }
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();

        Vec2 sent = RotationUtility.getQuantizedRotation(new Vec2(displacedYaw, displacedPitch));
        RotationUtility.setRotationSilently(player, sent.x, sent.y);
        rotationApplied = true;
    }

    public void restoreRotation(LocalPlayer player) {
        if (!rotationApplied) {
            return;
        }
        rotationApplied = false;
        RotationUtility.setRotationSilently(player, savedYaw, savedPitch);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    @Subscribe(priority = -10)
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            active = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        tickCounter++;
        pruneTargetWindows(mc.level);

        if (requireKnockback.getValue() && knockbackLevel(player) <= 0) {
            active = false;
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        boolean attacking = mc.options.keyAttack.isDown() || isKillauraEngaged();
        Player target = attacking ? findClosestTarget(mc.level, player) : null;

        hasKnockbackEnchant = knockbackLevel(player) > 0;

        active = target != null && (hasKnockbackEnchant || anyMovementKey(mc.options));
        if (!active) {
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        if (!findVoid.getValue() || !tryFindVoidDirection(mc.level, player, target)) {
            displaceLeft = direction.getValue() == Direction.LEFT;
        }

        displaceThisTick = !displaceThisTick;
        if (displaceThisTick && !isWindowOpen(target, tickCounter)) {
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        wasDisplacingLastTick = displaceThisTick;

        if (!displaceThisTick) {
            return;
        }

        float offset = (float) yawOffset.getValue().doubleValue();
        float baseYaw = RotationHelper.getClientHandler().getYawOr(player.getYRot());
        float basePitch = RotationHelper.getClientHandler().getPitchOr(player.getXRot());
        displacedYaw = baseYaw + (displaceLeft ? -offset : offset);
        displacedPitch = basePitch;

        displacedPitch = basePitch;

        RotationHelper.getHandler().setTickRotation(new Vec2(displacedYaw, basePitch));
    }

    @Subscribe(priority = -5)
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        if (!active || !displaceThisTick) {
            return;
        }

        event.setYaw(displacedYaw);
        event.setPitch(displacedPitch);
        if (blink.getValue() && !blinkHolder.isBlocking()) {
            blinkHolder.block();
            releaseBlinkNextTick = true;
        }
    }

    @Subscribe(priority = -10)
    public void onMoveInput(MoveInputEvent event) {
        if (!active) {
            compensateNextTick = false;
            return;
        }

        if (compensateNextTick && !displaceThisTick) {
            compensateNextTick = false;
            event.setSideways(displaceLeft ? -1.0f : 1.0f);
            return;
        }

        if (!displaceThisTick || hasKnockbackEnchant) {
            return;
        }
        if (!anyMovementKey(Minecraft.getInstance().options)) {
            return;
        }
        event.setForward(1.0f);
        compensateNextTick = true;
    }

    private static boolean anyMovementKey(Options options) {
        return options.keyUp.isDown() || options.keyDown.isDown()
                || options.keyLeft.isDown() || options.keyRight.isDown();
    }

    private static int knockbackLevel(LocalPlayer player) {
        return InventoryUtility.calculateEnchantmentLevel(player.getMainHandItem(), Enchantments.KNOCKBACK);
    }

    private static boolean isKillauraEngaged() {
        KillauraModule killaura = KillauraModule.INSTANCE;
        return killaura.isEnabled() && killaura.getTargeting().getTarget() != null;
    }

    private static Player findClosestTarget(Level level, LocalPlayer self) {
        Player closest = null;
        double closestDistance = TARGET_RANGE;
        for (Player player : level.players()) {
            if (player == self || !player.isAlive() || player.deathTime != 0) {
                continue;
            }
            double distance = self.distanceTo(player);
            if (distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean tryFindVoidDirection(Level level, LocalPlayer self, Player target) {
        double dx = target.getX() - self.getX();
        double dz = target.getZ() - self.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001) {
            return false;
        }
        dx /= distance;
        dz /= distance;

        double rightX = -dz;
        double rightZ = dx;
        double eyeY = target.getY() + target.getEyeHeight();

        int leftVoid = 0;
        int rightVoid = 0;
        for (int i = 1; i <= 12; i++) {
            double offset = i * 0.5;
            if (isVoidBelow(level, self, target.getX() + rightX * offset, eyeY, target.getZ() + rightZ * offset)) {
                rightVoid++;
            }
            if (isVoidBelow(level, self, target.getX() - rightX * offset, eyeY, target.getZ() - rightZ * offset)) {
                leftVoid++;
            }
        }

        if (leftVoid == 0 && rightVoid == 0) {
            return false;
        }
        if (leftVoid != rightVoid) {
            displaceLeft = leftVoid > rightVoid;
        }
        return true;
    }

    private static boolean isVoidBelow(Level level, LocalPlayer self, double x, double y, double z) {
        Vec3 from = new Vec3(x, y, z);
        Vec3 to = new Vec3(x, y - 10.0, z);
        HitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
        return hit.getType() == HitResult.Type.MISS;
    }

    private boolean isWindowOpen(Player target, int currentTick) {
        if (target == null) {
            return true;
        }
        Integer windowStart = targetWindowStartTicks.get(target.getId());
        if (windowStart == null || currentTick - windowStart >= DISPLACE_WINDOW_TICKS) {
            targetWindowStartTicks.put(target.getId(), currentTick);
            return true;
        }
        int delayTicks = msToTicks(delay.getValue().doubleValue());
        return delayTicks <= 0 || currentTick - windowStart >= delayTicks;
    }

    private void pruneTargetWindows(Level level) {
        Iterator<Map.Entry<Integer, Integer>> iterator = targetWindowStartTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Entity entity = level.getEntity(iterator.next().getKey());
            if (!(entity instanceof Player player) || !player.isAlive() || player.deathTime != 0) {
                iterator.remove();
            }
        }
    }

    private static int msToTicks(double ms) {
        return ms <= 0.0 ? 0 : (int) Math.ceil(ms / 50.0);
    }

    public enum Direction {
        LEFT("Left"),
        RIGHT("Right");

        private final String label;

        Direction(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
