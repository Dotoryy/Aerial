package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.player.interaction.AttackEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.utility.GroundTickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public final class AutoPotModule extends Module {
    public static final AutoPotModule INSTANCE = new AutoPotModule();

    private final NumberProperty health = new NumberProperty("Health", 15, 1, 20, 1);
    private final NumberProperty delayMin = new NumberProperty("Delay Min (ms)", 500, 50, 5000, 50);
    private final NumberProperty delayMax = new NumberProperty("Delay Max (ms)", 1000, 50, 5000, 50);

    private int attackTicks;
    private long nextThrowMs;

    private int pendingSlot = -1;

    private boolean rotationApplied;
    private float savedYaw;
    private float savedPitch;

    private AutoPotModule() {
        super("Auto Pot", "Automatically throws a beneficial splash potion", ModuleCategory.COMBAT);
        addProperties(health, delayMin, delayMax);
    }

    @Override
    protected void onDisable() {
        attackTicks = 0;
        pendingSlot = -1;
        rotationApplied = false;
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        attackTicks = 0;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            pendingSlot = -1;
            return;
        }

        attackTicks++;
        if (mc.gui.screen() != null) {
            attackTicks = 0;
        }

        if (GroundTickTracker.getGroundTicks() <= 1) {
            return;
        }
        if (System.currentTimeMillis() < nextThrowMs) {
            return;
        }
        if (attackTicks < 10) {
            return;
        }
        if (ScaffoldModule.INSTANCE.isEnabled()) {
            return;
        }

        int slot = findEligiblePotionSlot(player);
        if (slot < 0) {
            return;
        }
        pendingSlot = slot;
    }

    public void applyRotation(LocalPlayer player) {
        if (pendingSlot < 0 || rotationApplied) {
            return;
        }
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();
        float yaw = savedYaw + (float) ((Math.random() - 0.5) * 3.0);
        float pitch = 87.0f + (float) (Math.random() * 3.0);
        RotationUtility.setRotationSilently(player, yaw, pitch);
        rotationApplied = true;

        Inventory inventory = player.getInventory();
        int previousSlot = inventory.getSelectedSlot();
        inventory.setSelectedSlot(pendingSlot);
        Minecraft.getInstance().gameMode.useItem(player, InteractionHand.MAIN_HAND);
        inventory.setSelectedSlot(previousSlot);

        double delay = delayMin.getValue().doubleValue()
                + Math.random() * (delayMax.getValue().doubleValue() - delayMin.getValue().doubleValue());
        nextThrowMs = System.currentTimeMillis() + Math.round(delay);
        pendingSlot = -1;
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

    private int findEligiblePotionSlot(LocalPlayer player) {
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isEligibleSplashPotion(player, stack)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isEligibleSplashPotion(LocalPlayer player, ItemStack stack) {
        if (!stack.is(Items.SPLASH_POTION)) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || !contents.hasEffects()) {
            return false;
        }
        MobEffectInstance first = contents.getAllEffects().iterator().next();
        Holder<MobEffect> effect = first.getEffect();
        if (effect.value().getCategory() == MobEffectCategory.HARMFUL) {
            return false;
        }
        boolean isHealBased = effect.is(MobEffects.REGENERATION) || effect.is(MobEffects.INSTANT_HEALTH);
        if (isHealBased && player.getHealth() > health.getValue().floatValue()) {
            return false;
        }
        MobEffectInstance active = player.getEffect(effect);
        return active == null || active.getDuration() == 0;
    }
}
