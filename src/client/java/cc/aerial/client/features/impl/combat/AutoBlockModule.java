package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import cc.aerial.client.event.impl.game.input.MouseHandleInputEvent;
import cc.aerial.client.event.impl.game.player.interaction.AttackEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.killaura.target.CurrentTarget;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.mixin.MultiPlayerGameModeAccessor;
import cc.aerial.client.mouse.MouseHelper;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.target.TargetFlags;
import cc.aerial.client.target.TargetProperty;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.InventoryUtility;
import cc.aerial.client.utility.PlayerUtility;
import cc.aerial.client.utility.RandomUtility;
import cc.aerial.client.scaffold.SlotSpoof;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class AutoBlockModule extends Module {
    public static final AutoBlockModule INSTANCE = new AutoBlockModule();

    private static final long TICK_MS = 50L;

    public enum Mode {
        NORMAL("Normal");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final BooleanProperty allowSwingWhileBlocking = new BooleanProperty("Blocking", false);

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.NORMAL);

    private final NumberProperty blockRange = new NumberProperty("Block range", 3f, 3f, 8f, 0.5f);

    private final NumberProperty maxHurtTime = new NumberProperty("Max hurt time (ms)", 210, 50, 500, 10).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final NumberProperty maxHoldDuration = new NumberProperty("Max hold duration (ms)", 160, 0, 500, 10).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final BooleanProperty conditionLeftClick = new BooleanProperty("Req left click", false).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final BooleanProperty conditionRightClick = new BooleanProperty("Req right click", false).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final BooleanProperty conditionDamaged = new BooleanProperty("Req damaged", false).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final BooleanProperty forceBlockAnimation = new BooleanProperty("Force block animation", false).hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final BooleanProperty forceBlockOnlyInRange = new BooleanProperty("Force block only in range", true).hideIf(() -> mode.getValue() != Mode.NORMAL || !forceBlockAnimation.getValue());

    private final TargetProperty targetProperty = new TargetProperty(true, false, false, false, false, false);

    private boolean blocking;

    private LivingEntity target;
    private boolean currentBlockIsFirstSwing;
    private long blockStartMs;
    private boolean wasAttackKeyDown;

    private final Map<LivingEntity, Boolean> firstSwingUsed = new WeakHashMap<>();

    private AutoBlockModule() {
        super("Auto Block", "Allows illegitimate actions while blocking, or automatically blocks.", ModuleCategory.COMBAT);
        addProperties(
                new GroupProperty("Allow swing while...", allowSwingWhileBlocking),
                mode,
                blockRange,
                new GroupProperty("Normal", maxHurtTime, maxHoldDuration, conditionLeftClick, conditionRightClick, conditionDamaged,
                        forceBlockAnimation, forceBlockOnlyInRange),
                targetProperty.get()
        );
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        stopBlocking();
        target = null;
        wasAttackKeyDown = false;
        firstSwingUsed.clear();
    }

    public boolean isBlocking() {
        return blocking;
    }

    public boolean isSwingAllowed() {
        return allowSwingWhileBlocking.getValue();
    }

    public boolean isForcingAnimation() {
        if (!isEnabled()) {
            return false;
        }
        if (mode.getValue() == Mode.NORMAL) {
            return forceBlockAnimation.getValue() && (!forceBlockOnlyInRange.getValue() || target != null);
        }
        return false;
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (mode.getValue() != Mode.NORMAL) {
            return;
        }
        if (Minecraft.getInstance().player == null) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity attacked)) {
            return;
        }
        if (firstSwingUsed.containsKey(attacked)) {
            return;
        }
        firstSwingUsed.put(attacked, Boolean.TRUE);

        if (!blocking) {
            startBlocking(true);
        }
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (mode.getValue() != Mode.NORMAL) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().level == null) {
            stopBlocking();
            return;
        }
        if (!player.getMainHandItem().is(ItemTags.SWORDS)) {
            stopBlocking();
            return;
        }

        target = findNearbyTarget(player);

        boolean attackKeyDown = Minecraft.getInstance().options.keyAttack.isDown();
        if (!attackKeyDown && wasAttackKeyDown) {
            firstSwingUsed.clear();
        }
        wasAttackKeyDown = attackKeyDown;

        boolean conditionsOk = newConditionsMet(player);
        boolean inRange = target != null;
        boolean targetRanged = target != null && isRangedWeapon(target);

        if (!conditionsOk || !inRange || targetRanged) {
            stopBlocking();
        } else if (!blocking) {
            int hurtTime = player.hurtTime;
            long hurtTimeMs = (long) hurtTime * TICK_MS;
            boolean predictingNextHit = hurtTime > 0 && hurtTimeMs <= maxHurtTime.getValue();
            if (predictingNextHit) {
                startBlocking(false);
            }
        }

        if (blocking) {
            long holdMs = (long) maxHoldDuration.getValue().doubleValue();
            if (System.currentTimeMillis() - blockStartMs >= holdMs) {
                stopBlocking();
            }
        }

        if (blocking) {
            MouseHelper.getRightButton().setPressed(true, RandomUtility.getRandomInt(2));
        }
    }

    public boolean isSuppressingAttack() {
        return false;
    }

    private LivingEntity findNearbyTarget(LocalPlayer player) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        int flags = targetProperty.getTargetFlags();
        Vec3 eyePos = player.getEyePosition();
        double range = blockRange.getValue();

        LivingEntity closest = null;
        double closestDist = range;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == player || living.isDeadOrDying()) {
                continue;
            }
            if (!isMatchingFlags(player, living, flags)) {
                continue;
            }
            double dist = PlayerUtility.getDistanceToEntity(eyePos, living);
            if (dist <= closestDist) {
                closestDist = dist;
                closest = living;
            }
        }
        return closest;
    }

    private boolean isRangedWeapon(LivingEntity target) {
        return target.getMainHandItem().getItem() instanceof BowItem;
    }

    private boolean newConditionsMet(LocalPlayer player) {
        if (conditionLeftClick.getValue() && !isAttackIntentActive()) {
            return false;
        }
        if (conditionRightClick.getValue() && !Minecraft.getInstance().options.keyUse.isDown()) {
            return false;
        }
        return !conditionDamaged.getValue() || player.hurtTime != 0;
    }

    private boolean isAttackIntentActive() {
        if (Minecraft.getInstance().options.keyAttack.isDown()) {
            return true;
        }
        KillauraModule killAura = KillauraModule.INSTANCE;
        return killAura.isEnabled() && !killAura.getSettings().isRequireAttackKey() && killAura.getTargeting().getTarget() != null;
    }

    private void startBlocking(boolean firstSwing) {
        blocking = true;
        blockStartMs = System.currentTimeMillis();
        currentBlockIsFirstSwing = firstSwing;
    }

    private void stopBlocking() {
        blocking = false;
    }

    private boolean isMatchingFlags(LocalPlayer self, LivingEntity entity, int flags) {
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                || entity instanceof net.minecraft.world.entity.npc.villager.Villager) {
            return false;
        }
        if (entity instanceof Player otherPlayer) {
            if (AntiBotModule.isBot(otherPlayer)) {
                return false;
            }
            if ((flags & TargetFlags.FRIENDLY) == 0 && PlayerUtility.areOnSameTeam(self, otherPlayer)) {
                return false;
            }
            return (flags & TargetFlags.PLAYERS) != 0;
        }
        if (entity instanceof Monster) {
            return (flags & TargetFlags.HOSTILE) != 0;
        }
        return (flags & TargetFlags.PASSIVE) != 0;
    }
}
