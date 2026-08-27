package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

public final class AnimationsModule extends Module {
    public static final AnimationsModule INSTANCE = new AnimationsModule();

    private final BooleanProperty swordBlocking = new BooleanProperty("Enabled", true);
    private final ModeProperty<BlockMode> blockAnimationMode =
            new ModeProperty<>("Block animation", BlockMode.V1_7);

    private final BooleanProperty alwaysHideShield = new BooleanProperty("Always hidden", true);
    private final BooleanProperty hideShieldSlotInHotbar = new BooleanProperty("Hide offhand slot", true);

    private final BooleanProperty oldBackwardsWalking = new BooleanProperty("Old backwards walking", true);
    private final BooleanProperty oldArmorDamageTint = new BooleanProperty("Old armor damage tint", true);
    private final BooleanProperty oldSneaking = new BooleanProperty("Old sneaking", false);
    private final BooleanProperty fixPoseRepeat = new BooleanProperty("Fix pose repeat", true);

    private final NumberProperty mainHandScale = new NumberProperty("Scale", 0f, -2f, 2f, 0.1f);
    private final NumberProperty mainHandX = new NumberProperty("Offset X", 0f, -2f, 2f, 0.1f);
    private final NumberProperty mainHandY = new NumberProperty("Offset Y", 0f, -2f, 2f, 0.1f);
    private final NumberProperty swingSlowdown = new NumberProperty("Swing slowdown", 0f, 0f, 5f, 0.25f);
    private final BooleanProperty oldCooldownAnimation = new BooleanProperty("Old cooldown animation", true);
    private final BooleanProperty swingWhileUsing = new BooleanProperty("Visual swing on use", true);
    private final BooleanProperty hideDropSwing = new BooleanProperty("Hide drop swing", false);
    private final BooleanProperty equipOffset = new BooleanProperty("Equip offset", false);

    private final GroupProperty swordBlockingGroup = new GroupProperty("Sword blocking", swordBlocking, blockAnimationMode);
    private final GroupProperty shieldsGroup = new GroupProperty("Shields", alwaysHideShield, hideShieldSlotInHotbar);
    private final GroupProperty playerGroup = new GroupProperty("Player", oldBackwardsWalking, oldArmorDamageTint, oldSneaking, fixPoseRepeat);
    private final GroupProperty itemGroup = new GroupProperty(
            "Item",
            mainHandScale, mainHandX, mainHandY, swingSlowdown,
            oldCooldownAnimation, swingWhileUsing, hideDropSwing, equipOffset
    );

    private final GroupProperty[] groups = {swordBlockingGroup, shieldsGroup, playerGroup, itemGroup};

    private AnimationsModule() {
        super("Animations", "", ModuleCategory.VISUAL);

        blockAnimationMode.hideIf(() -> !swordBlocking.getValue());
        addProperties(swordBlockingGroup, shieldsGroup, playerGroup, itemGroup);
        setEnabled(true);
    }

    @Override
    public String getSuffix() {
        return this.blockAnimationMode.getValue().toString();
    }

    public GroupProperty[] getGroups() {
        return groups;
    }

    public boolean isHideDropSwing() {
        return hideDropSwing.getValue();
    }

    public boolean isOldSneaking() {
        return oldSneaking.getValue();
    }

    public boolean isFixPoseRepeat() {
        return fixPoseRepeat.getValue();
    }

    public float getSwingSlowdown() {
        return swingSlowdown.getValue().floatValue() + 1.0f;
    }

    public boolean isSwordBlocking() {
        return swordBlocking.getValue();
    }

    public boolean isEquipOffset() {
        return equipOffset.getValue();
    }

    public boolean isOldCooldownAnimation() {
        return oldCooldownAnimation.getValue();
    }

    public boolean isOldBackwardsWalking() {
        return oldBackwardsWalking.getValue();
    }

    public boolean isOldArmorDamageTint() {
        return oldArmorDamageTint.getValue();
    }

    public boolean isHideShield() {
        return alwaysHideShield.getValue();
    }

    public boolean isHideShieldSlotInHotbar() {
        return hideShieldSlotInHotbar.getValue();
    }

    public float getMainHandScale() {
        return mainHandScale.getValue().floatValue();
    }

    public float getMainHandX() {
        return mainHandX.getValue().floatValue();
    }

    public float getMainHandY() {
        return mainHandY.getValue().floatValue();
    }

    public boolean isSwingWhileUsing() {
        return swingWhileUsing.getValue();
    }

    public void applyTransformations(PoseStack matrices, float swingProgress) {
        float convertedProgress = Mth.sin((double) (Mth.sqrt(swingProgress) * (float) Math.PI));
        float f = Mth.sin((double) (swingProgress * swingProgress * (float) Math.PI));

        switch (blockAnimationMode.getValue()) {
            case V1_7 -> {
                BlockUtility.applySwingTransformation(matrices, swingProgress, convertedProgress);
                BlockUtility.applyBlockTransformation(matrices);
            }
            case V1_8 -> BlockUtility.applyBlockTransformation(matrices);
            case RUB -> {
                BlockUtility.applyBlockTransformation(matrices);
                matrices.mulPose(Axis.YP.rotationDegrees(f * -30.0f));
                matrices.mulPose(Axis.ZP.rotationDegrees(convertedProgress * -30.0f));
            }
            case STELLA -> {
                BlockUtility.applySwingTransformation(matrices, swingProgress, convertedProgress);
                matrices.translate(-0.15f, 0.16f, 0.15f);
                matrices.mulPose(Axis.YP.rotationDegrees(-24.0f));
                matrices.mulPose(Axis.ZP.rotationDegrees(75.0f));
                matrices.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
            case BOUNCE -> {
                BlockUtility.applyBlockTransformation(matrices);
                matrices.mulPose(Axis.XP.rotationDegrees(0.0f));
                matrices.mulPose(Axis.YP.rotationDegrees(convertedProgress * 42.0f));
                matrices.mulPose(Axis.ZP.rotationDegrees(-convertedProgress * 22.0f));
            }
            case DIAGONAL -> {
                BlockUtility.applyBlockTransformation(matrices);
                matrices.mulPose(Axis.XP.rotationDegrees(5.0f - (convertedProgress * 32.0f)));
                matrices.mulPose(Axis.YP.rotationDegrees(0.0f));
                matrices.mulPose(Axis.ZP.rotationDegrees(0.0f));
            }
            case SWANK -> {
                matrices.mulPose(Axis.YP.rotationDegrees(45.0f + f * -5.0f));
                matrices.mulPose(Axis.ZP.rotationDegrees(convertedProgress * -20.0f));
                matrices.mulPose(Axis.XP.rotationDegrees(convertedProgress * -40.0f));
                matrices.mulPose(Axis.YP.rotationDegrees(-45.0f));
                BlockUtility.applyBlockTransformation(matrices);
            }
        }
    }

    public enum BlockMode {
        V1_7("1.7"),
        V1_8("1.8"),
        RUB("Rub"),
        STELLA("Stella"),
        BOUNCE("Bounce"),
        DIAGONAL("Diagonal"),
        SWANK("Swank");

        private final String label;

        BlockMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
