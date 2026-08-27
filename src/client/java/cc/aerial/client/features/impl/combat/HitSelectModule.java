package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.features.impl.combat.killaura.target.CurrentTarget;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class HitSelectModule extends Module {
    public static final HitSelectModule INSTANCE = new HitSelectModule();

    private static final long SERVER_CONFIRM_COOLDOWN_MS = 500L;
    private static final long SERVER_CONFIRM_TIMEOUT_MS = 1500L;

    private static final int BLOCK_WAIT_FIRST = 1;
    private static final int BLOCK_SERVER_COOLDOWN = 1 << 3;
    private static final int BLOCK_PREDICTED_BURST = 1 << 4;
    private static final int BLOCK_CRITICALS = 1 << 5;

    private final NumberProperty pauseDuration = new NumberProperty("Pause Duration", 500, 0, 500, 10);
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.BURST);
    private final NumberProperty waitForFirstHit = new NumberProperty("Wait For First Hit", 0, 0, 500, 1);
    private final NumberProperty hitLaterInTrades = new NumberProperty("Hit Later In Trades", 0, 0, 500, 1);
    private final NumberProperty whenOnlyCombo = new NumberProperty("When Only Combo", 0, 0, 10, 1);
    private final BooleanProperty disableDuringKnockback = new BooleanProperty("Disable During Knockback", false);
    private final BooleanProperty onlyWhileDamaged = new BooleanProperty("Only While Damaged", false);
    private final BooleanProperty useServerAttackTime = new BooleanProperty("Use Server Attack Time", false);
    private final BooleanProperty fakeSwing = new BooleanProperty("Fake Swing", false);
    private final NumberProperty inCombatCancelRate = new NumberProperty("In Combat Cancel Rate", 100, 0, 100, 1);
    private final NumberProperty missedSwingsCancelRate = new NumberProperty("Missed Swings Cancel Rate", 0, 0, 100, 1);
    private final BooleanProperty resetOnDamage = new BooleanProperty("Reset On Damage", false);

    private Player currentTarget;
    private Player engagedTarget;
    private final Map<Integer, TargetState> targetStates = new HashMap<>();
    private int lastSelfHurtTime;
    private boolean takingKnockback;
    private boolean waitFirstTracking;
    private long waitFirstStartMs = -1L;
    private boolean waitFirstUnlocked;

    private HitSelectModule() {
        super("Hit Select", "Filters unnecessary clicks, which helps reduce your average click speed whilst reducing more knockback.", ModuleCategory.COMBAT);
        addProperties(pauseDuration, mode, waitForFirstHit, hitLaterInTrades, whenOnlyCombo,
                disableDuringKnockback, onlyWhileDamaged, useServerAttackTime, fakeSwing,
                inCombatCancelRate, missedSwingsCancelRate, resetOnDamage);
    }

    @Override
    protected void onEnable() {
        resetAllState();
    }

    @Override
    protected void onDisable() {
        resetAllState();
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            resetAllState();
            return;
        }

        long now = System.currentTimeMillis();
        pruneTargetStates();

        CurrentTarget killauraTarget = KillauraModule.INSTANCE.getTargeting().getTarget();
        Player nextTarget = killauraTarget != null && killauraTarget.getEntity() instanceof Player
                ? (Player) killauraTarget.getEntity() : null;
        updateCurrentTarget(nextTarget, now);
        updateSelfDamage(player);
        updateTargetDamage(now);
    }

    public boolean shouldBlockAttack(LivingEntity targetEntity) {
        if (!isEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Player clickedTarget = targetEntity instanceof Player ? asAttackedPlayer((Player) targetEntity) : null;

        boolean blocked;
        if (clickedTarget == null) {
            blocked = shouldCancel(missedSwingsCancelRate.getValue());
        } else {
            updateCurrentTarget(clickedTarget, now);
            engagedTarget = clickedTarget;
            TargetState state = getTargetState(clickedTarget);

            if (state.comboCount < whenOnlyCombo.getValue().intValue()) {
                blocked = false;
            } else {
                int blockMask = getValidHitBlockMask(state, now);
                boolean rawBlock = (blockMask & BLOCK_WAIT_FIRST) != 0
                        || (blockMask & BLOCK_PREDICTED_BURST) != 0
                        || applyPauseDuration(state, blockMask & ~BLOCK_PREDICTED_BURST, now);
                blocked = rawBlock && shouldCancel(inCombatCancelRate.getValue());
            }
        }

        if (blocked && fakeSwing.getValue()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
        return blocked;
    }

    public void confirmHit(LivingEntity targetEntity) {
        if (!isEnabled() || !(targetEntity instanceof Player)) {
            return;
        }
        Player target = asAttackedPlayer((Player) targetEntity);
        if (target == null) {
            return;
        }
        recordPassedValidHit(target, System.currentTimeMillis());
    }

    public boolean shouldCancelMissedSwing() {
        return isEnabled() && shouldCancel(missedSwingsCancelRate.getValue());
    }

    private void updateCurrentTarget(Player nextTarget, long now) {
        if (sameTarget(nextTarget)) {
            if (nextTarget != null) {
                currentTarget = nextTarget;
                getTargetState(nextTarget);
            }
            return;
        }

        currentTarget = nextTarget;

        if (nextTarget == null) {
            resetWaitFirstState();
        } else if (!waitFirstTracking) {
            waitFirstTracking = true;
            waitFirstStartMs = now;
            waitFirstUnlocked = false;
        }

        if (nextTarget != null) {
            getTargetState(nextTarget);
        }
    }

    private void updateSelfDamage(LocalPlayer player) {
        int hurtTime = player.hurtTime;
        boolean hurtAgain = hurtTime > lastSelfHurtTime;

        if (hurtAgain) {
            if (waitFirstTracking && !waitFirstUnlocked) {
                waitFirstUnlocked = true;
            }
            if (!takingKnockback) {
                takingKnockback = true;
            }
            if (engagedTarget != null) {
                TargetState state = getTargetState(engagedTarget);
                state.firstSelfHitSeen = true;
                state.comboCount = 0;
            }
            if (resetOnDamage.getValue()) {
                clearBlockWindows();
            }
        }

        if (takingKnockback && player.onGround() && !hurtAgain) {
            takingKnockback = false;
        }

        lastSelfHurtTime = hurtTime;
    }

    private void clearBlockWindows() {
        for (TargetState state : targetStates.values()) {
            state.predictedBurstWindowStartMs = -1L;
            state.lastConfirmedTargetDamageMs = -1L;
            state.pendingServerConfirmationMs = -1L;
            state.rawBlockMask = 0;
            state.rawBlockStartMs = -1L;
        }
    }

    private void updateTargetDamage(long now) {
        if (engagedTarget == null) {
            return;
        }

        TargetState state = getTargetState(engagedTarget);
        int targetHurtTime = engagedTarget.hurtTime;

        if (targetHurtTime > state.lastObservedTargetHurtTime) {
            state.comboCount++;
        }

        if (useServerAttackTime.getValue()) {
            if (state.pendingServerConfirmationMs >= 0 && now - state.pendingServerConfirmationMs > SERVER_CONFIRM_TIMEOUT_MS) {
                state.pendingServerConfirmationMs = -1;
            }

            if (state.pendingServerConfirmationMs >= 0 && targetHurtTime > state.lastObservedTargetHurtTime) {
                state.pendingServerConfirmationMs = -1;
                state.lastConfirmedTargetDamageMs = now;
                state.rawBlockMask = BLOCK_SERVER_COOLDOWN;
                state.rawBlockStartMs = now;
            }
        }

        state.lastObservedTargetHurtTime = targetHurtTime;
    }

    private int getValidHitBlockMask(TargetState state, long now) {
        if (currentTarget == null) {
            return 0;
        }
        if (disableDuringKnockback.getValue() && isTakingKnockback()) {
            return 0;
        }

        int blockMask = 0;
        if (isWaitingForFirstHit(now)) {
            blockMask |= BLOCK_WAIT_FIRST;
        }
        blockMask |= getBurstBlockMask(state, now);
        if (isCriticalsBlocked(state)) {
            blockMask |= BLOCK_CRITICALS;
        }
        return blockMask;
    }

    private int getBurstBlockMask(TargetState state, long now) {
        if (useServerAttackTime.getValue()) {
            long serverCooldownMs = SERVER_CONFIRM_COOLDOWN_MS + tradeExtensionMs(state);
            if (state.lastConfirmedTargetDamageMs >= 0 && now - state.lastConfirmedTargetDamageMs < serverCooldownMs) {
                return BLOCK_SERVER_COOLDOWN;
            }
            return 0;
        }
        return isPredictedBurstWindowActive(state, now) ? BLOCK_PREDICTED_BURST : 0;
    }

    private long tradeExtensionMs(TargetState state) {
        return state.firstSelfHitSeen ? hitLaterInTrades.getValue().longValue() : 0;
    }

    private boolean isCriticalsBlocked(TargetState state) {
        if (mode.getValue() != Mode.CRITICALS) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.onGround()) {
            return false;
        }
        if (onlyWhileDamaged.getValue() && !state.firstSelfHitSeen) {
            return false;
        }
        if (disableDuringKnockback.getValue() && isTakingKnockback()) {
            return false;
        }
        return !canCriticalHit(player);
    }

    private boolean isWaitingForFirstHit(long now) {
        if (waitForFirstHit.getValue().intValue() <= 0
                || currentTarget == null
                || !waitFirstTracking
                || waitFirstUnlocked
                || waitFirstStartMs < 0) {
            return false;
        }
        long requiredMs = waitForFirstHit.getValue().longValue();
        return now - waitFirstStartMs < requiredMs;
    }

    private boolean canCriticalHit(LocalPlayer player) {
        return player.fallDistance > 0.0
                && !player.onGround()
                && PlayerUtility.isCriticalHitAvailable(player);
    }

    private boolean isTakingKnockback() {
        LocalPlayer player = Minecraft.getInstance().player;
        return takingKnockback || (player != null && player.hurtTime > 0);
    }

    private boolean applyPauseDuration(TargetState state, int blockMask, long now) {
        if (blockMask == 0) {
            state.rawBlockMask = 0;
            state.rawBlockStartMs = -1;
            return false;
        }

        if (pauseDuration.getValue().intValue() <= 0) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartMs = now;
            return false;
        }

        if (blockMask != state.rawBlockMask) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartMs = now;
        } else if (state.rawBlockStartMs < 0) {
            state.rawBlockStartMs = now;
        }

        long requiredMs = pauseDuration.getValue().longValue();
        return now - state.rawBlockStartMs < requiredMs;
    }

    private void recordPassedValidHit(Player target, long now) {
        updateCurrentTarget(target, now);
        TargetState state = getTargetState(target);

        if (useServerAttackTime.getValue()) {
            state.pendingServerConfirmationMs = now;
            state.lastConfirmedTargetDamageMs = -1;
            return;
        }

        if (!isPredictedBurstWindowActive(state, now)) {
            state.predictedBurstWindowStartMs = now;
        }
    }

    private boolean shouldCancel(double chance) {
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 100.0) {
            return true;
        }
        return Math.random() * 100.0 < chance;
    }

    private boolean sameTarget(Player nextTarget) {
        if (currentTarget == null || nextTarget == null) {
            return currentTarget == nextTarget;
        }
        return currentTarget.getId() == nextTarget.getId();
    }

    private void resetWaitFirstState() {
        waitFirstTracking = false;
        waitFirstStartMs = -1L;
        waitFirstUnlocked = false;
    }

    private boolean isPredictedBurstWindowActive(TargetState state, long now) {
        if (state.predictedBurstWindowStartMs < 0) {
            return false;
        }
        long effectivePauseMs = pauseDuration.getValue().longValue() + tradeExtensionMs(state);
        return effectivePauseMs > 0 && now - state.predictedBurstWindowStartMs < effectivePauseMs;
    }

    private Player asAttackedPlayer(Player entity) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null || entity == self || entity.isDeadOrDying() || entity.deathTime != 0) {
            return null;
        }
        return entity;
    }

    private TargetState getTargetState(Player target) {
        TargetState state = targetStates.get(target.getId());
        if (state == null) {
            state = new TargetState();
            if (useServerAttackTime.getValue()) {
                state.lastObservedTargetHurtTime = target.hurtTime;
            }
            targetStates.put(target.getId(), state);
        }
        return state;
    }

    private void pruneTargetStates() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            targetStates.clear();
            return;
        }

        Iterator<Map.Entry<Integer, TargetState>> iterator = targetStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TargetState> entry = iterator.next();
            var entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player p) || p.isDeadOrDying() || p.deathTime != 0) {
                iterator.remove();
            }
        }
    }

    private void resetAllState() {
        currentTarget = null;
        engagedTarget = null;
        targetStates.clear();
        lastSelfHurtTime = 0;
        takingKnockback = false;
        resetWaitFirstState();
    }

    private static final class TargetState {
        boolean firstSelfHitSeen;
        long lastConfirmedTargetDamageMs = -1L;
        long pendingServerConfirmationMs = -1L;
        long predictedBurstWindowStartMs = -1L;
        int lastObservedTargetHurtTime;
        long rawBlockStartMs = -1L;
        int rawBlockMask;
        int comboCount;
    }

    public enum Mode {
        BURST("Burst"), CRITICALS("Criticals");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
