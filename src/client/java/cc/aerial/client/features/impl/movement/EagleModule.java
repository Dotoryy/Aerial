package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.RandomUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EagleModule extends Module {
    public static final EagleModule INSTANCE = new EagleModule();

    private final NumberProperty delayMin = new NumberProperty("Delay min", 2, 0, 10, 1);
    private final NumberProperty delayMax = new NumberProperty("Delay max", 3, 0, 10, 1);
    private final BooleanProperty directionCheck = new BooleanProperty("Direction Check", true);
    private final BooleanProperty jumpCheck = new BooleanProperty("Jump Check", true);
    private final BooleanProperty pitchCheck = new BooleanProperty("Pitch Check", true);
    private final BooleanProperty blocksOnly = new BooleanProperty("Blocks Only", true);
    private final BooleanProperty sneakOnly = new BooleanProperty("Sneak Only", false);

    private int sneakDelay;
    private boolean active;

    private EagleModule() {
        super("Eagle", "Automatically sneaks at edge of blocks", ModuleCategory.MOVEMENT);
        addProperties(delayMin, delayMax, directionCheck, jumpCheck, pitchCheck, blocksOnly, sneakOnly);
    }

    public boolean isActive() {
        return active;
    }

    @Override
    protected void onDisable() {
        sneakDelay = 0;
        active = false;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        if (sneakDelay > 0) {
            sneakDelay--;
        }

        if (sneakDelay == 0 && canFallOffEdge()) {
            int min = delayMin.getValue().intValue();
            int max = delayMax.getValue().intValue() + 1;
            sneakDelay = RandomUtility.getRandomInt(min, Math.max(min + 1, max));
        }
    }

    @Subscribe(priority = -1000)
    public void onMoveInput(MoveInputEvent event) {
        active = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            return;
        }

        boolean realSneakHeld = mc.options.keyShift.isDown();

        if (sneakOnly.getValue() && realSneakHeld && shouldSneak()) {
            event.setSneak(false);
            event.setForward(event.getForward() / 0.3f);
            event.setSideways(event.getSideways() / 0.3f);
        }

        if (!event.isSneak() && shouldSneak() && (sneakDelay > 0 || canFallOffEdge())) {
            event.setSneak(true);
            event.setForward(event.getForward() * 0.3f);
            event.setSideways(event.getSideways() * 0.3f);
            active = true;
        }
    }

    private boolean shouldSneak() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (directionCheck.getValue() && mc.options.keyUp.isDown()) {
            return false;
        } else if (jumpCheck.getValue() && mc.options.keyJump.isDown()) {
            return false;
        } else if (pitchCheck.getValue() && player.getXRot() < 69.0f) {
            return false;
        } else if (sneakOnly.getValue() && !mc.options.keyShift.isDown()) {
            return false;
        } else {
            return (!blocksOnly.getValue() || player.getMainHandItem().getItem() instanceof BlockItem) && player.onGround();
        }
    }

    private boolean canFallOffEdge() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return false;
        }

        double[] offset = predictMovement(player);
        Vec3 motion = player.getDeltaMovement();
        AABB box = player.getBoundingBox().move(motion.x + offset[0], -1.0, motion.z + offset[1]);
        for (VoxelShape ignored : level.getBlockCollisions(player, box)) {
            return false;
        }
        return true;
    }

    private double[] predictMovement(LocalPlayer player) {
        Options options = Minecraft.getInstance().options;
        float strafe = calculateImpulse(options.keyLeft.isDown(), options.keyRight.isDown()) * 0.98f;
        float forward = calculateImpulse(options.keyUp.isDown(), options.keyDown.isDown()) * 0.98f;

        float magnitude = strafe * strafe + forward * forward;
        if (magnitude < 1.0E-4f) {
            return new double[]{0.0, 0.0};
        }

        magnitude = Mth.sqrt(magnitude);
        if (magnitude < 1.0f) {
            magnitude = 1.0f;
        }
        magnitude = getAllowedHorizontalDistance(player) / magnitude;

        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float sinYaw = Mth.sin((double) yaw);
        float cosYaw = Mth.cos((double) yaw);
        strafe *= magnitude;
        forward *= magnitude;
        return new double[]{strafe * cosYaw - forward * sinYaw, forward * cosYaw + strafe * sinYaw};
    }

    private static float calculateImpulse(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0f;
        }
        return positive ? 1.0f : -1.0f;
    }

    private float getAllowedHorizontalDistance(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        BlockPos pos = BlockPos.containing(player.getX(), box.minY - 1.0, player.getZ());
        Block block = player.level().getBlockState(pos).getBlock();
        float slipperiness = block.getFriction() * 0.91f;
        return player.getSpeed() * (0.16277136f / (slipperiness * slipperiness * slipperiness));
    }
}
