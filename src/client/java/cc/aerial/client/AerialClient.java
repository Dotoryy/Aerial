package cc.aerial.client;

import cc.aerial.client.binding.BindRepository;
import cc.aerial.client.config.ConfigUtility;
import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.features.impl.visual.ClickGuiModule;
import cc.aerial.client.features.impl.utility.JoinClaimModule;
import cc.aerial.client.features.impl.utility.PingSpoofModule;
import cc.aerial.client.features.impl.combat.TeleportAuraModule;
import cc.aerial.client.features.impl.combat.RegenModule;
import cc.aerial.client.features.impl.combat.AntiBotModule;
import cc.aerial.client.features.impl.combat.BacktrackModule;
import cc.aerial.client.features.impl.combat.LagRangeModule;
import cc.aerial.client.features.impl.combat.RodAimbotModule;
import cc.aerial.client.features.impl.combat.AntiFireballModule;
import cc.aerial.client.features.impl.combat.AttackDelayModule;
import cc.aerial.client.features.impl.combat.AutoBlockModule;
import cc.aerial.client.features.impl.combat.AutoPotModule;
import cc.aerial.client.features.impl.combat.CriticalsModule;
import cc.aerial.client.features.impl.combat.AutoClickerModule;
import cc.aerial.client.features.impl.combat.HitSelectModule;
import cc.aerial.client.features.impl.combat.KeepSprintModule;
import cc.aerial.client.features.impl.combat.PiercingModule;
import cc.aerial.client.features.impl.combat.ReachModule;
import cc.aerial.client.features.impl.combat.VelocityModule;
import cc.aerial.client.features.impl.combat.WTapModule;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.features.impl.hud.DynamicIsland;
import cc.aerial.client.features.impl.hud.RiseCapsuleModule;
import cc.aerial.client.features.impl.hud.ScaffoldBlockCounter;
import cc.aerial.client.features.impl.hud.VanillaMiningIsland;
import cc.aerial.client.features.impl.movement.EagleModule;
import cc.aerial.client.features.impl.movement.LadderClutchModule;
import cc.aerial.client.features.impl.movement.FlightModule;
import cc.aerial.client.features.impl.movement.LongJumpModule;
import cc.aerial.client.features.impl.movement.MovementFixModule;
import cc.aerial.client.features.impl.movement.NoJumpDelayModule;
import cc.aerial.client.features.impl.movement.NoSlowModule;
import cc.aerial.client.features.impl.movement.PhaseModule;
import cc.aerial.client.features.impl.movement.NullMoveModule;
import cc.aerial.client.features.impl.movement.StasisModule;
import cc.aerial.client.features.impl.movement.VClipModule;
import cc.aerial.client.features.impl.movement.SpeedModule;
import cc.aerial.client.features.impl.movement.SpiderModule;
import cc.aerial.client.features.impl.movement.JesusModule;
import cc.aerial.client.features.impl.movement.StepModule;
import cc.aerial.client.features.impl.movement.SprintModule;
import cc.aerial.client.features.impl.utility.ResourcePackSpoofModule;
import cc.aerial.client.features.impl.utility.ChatBypassModule;
import cc.aerial.client.features.impl.other.SpotifyModule;
import cc.aerial.client.features.impl.utility.OverlayModule;
import cc.aerial.client.overlay.OverlayRenderer;
import cc.aerial.client.features.impl.utility.AntiVoidModule;
import cc.aerial.client.features.impl.utility.AutoArmorModule;
import cc.aerial.client.features.impl.utility.AutoChestModule;
import cc.aerial.client.features.impl.utility.AutoHypixelModule;
import cc.aerial.client.features.impl.utility.AutoToolModule;
import cc.aerial.client.features.impl.utility.BlinkModule;
import cc.aerial.client.features.impl.utility.ChestStealerModule;
import cc.aerial.client.features.impl.utility.InventoryManagerModule;
import cc.aerial.client.features.impl.utility.DisablerModule;
import cc.aerial.client.features.impl.utility.FastBreakModule;
import cc.aerial.client.features.impl.utility.FastPlaceModule;
import cc.aerial.client.features.impl.utility.InvMoveModule;
import cc.aerial.client.features.impl.utility.NoFallModule;
import cc.aerial.client.features.impl.utility.NoRotateModule;
import cc.aerial.client.features.impl.utility.BedwarsUtilModule;
import cc.aerial.client.features.impl.visual.BedPlatesModule;
import cc.aerial.client.features.impl.visual.KillEffectModule;
import cc.aerial.client.features.impl.visual.MurderMysteryModule;
import cc.aerial.client.features.impl.visual.ViewClipModule;
import cc.aerial.client.features.impl.visual.AmbienceModule;
import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.features.impl.visual.ArraylistModule;
import cc.aerial.client.features.impl.visual.AttackEffectsModule;
import cc.aerial.client.features.impl.visual.CapeModule;
import cc.aerial.client.features.impl.visual.ESPModule;
import cc.aerial.client.features.impl.visual.FreeLookModule;
import cc.aerial.client.features.impl.visual.FullBrightModule;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.features.impl.visual.NoHurtCameraModule;
import cc.aerial.client.features.impl.visual.PostProcessingModule;
import cc.aerial.client.features.impl.visual.ChatModule;
import cc.aerial.client.features.impl.visual.PotionEffectsModule;
import cc.aerial.client.features.impl.visual.ScoreboardModule;
import cc.aerial.client.features.impl.visual.StreamerModule;
import cc.aerial.client.features.impl.visual.TargetHudModule;
import cc.aerial.client.features.impl.visual.VanillaFixModule;
import cc.aerial.client.features.impl.combat.DisplaceModule;
import cc.aerial.client.features.impl.world.AntiAfkModule;
import cc.aerial.client.features.impl.world.AntiDebuffModule;
import cc.aerial.client.features.impl.world.BreakerModule;
import cc.aerial.client.features.impl.world.ChestAuraModule;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.features.impl.world.TimerModule;
import cc.aerial.client.features.repository.ModuleRepository;
import cc.aerial.client.hypixel.AerialHypixelTransport;
import cc.aerial.client.packet.LagManager;
import cc.aerial.client.packet.delay.DelayManager;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.TeleportTickTracker;
import cc.aerial.client.screen.ClickGuiKeybind;
import cc.aerial.client.screen.MovementPassthrough;
import net.fabricmc.api.ClientModInitializer;

public class AerialClient implements ClientModInitializer {
	private static ModuleRepository moduleRepository;

	public static ModuleRepository getModuleRepository() {
		return moduleRepository;
	}

	@Override
	public void onInitializeClient() {
		cc.aerial.client.screen.title.SplashCard.requestFont();

		moduleRepository = ModuleRepository.fromModules(
				AttackDelayModule.INSTANCE,
				AutoClickerModule.INSTANCE,
				WTapModule.INSTANCE,
				HitSelectModule.INSTANCE,
				KeepSprintModule.INSTANCE,
				VelocityModule.INSTANCE,
				AntiBotModule.INSTANCE,
				AntiFireballModule.INSTANCE,
				ReachModule.INSTANCE,
				CriticalsModule.INSTANCE,
				KillauraModule.INSTANCE,
				PiercingModule.INSTANCE,
				AutoBlockModule.INSTANCE,
				AutoPotModule.INSTANCE,
				SprintModule.INSTANCE,
				SpiderModule.INSTANCE,
				JesusModule.INSTANCE,
				StepModule.INSTANCE,
				PhaseModule.INSTANCE,
				SpeedModule.INSTANCE,
				NullMoveModule.INSTANCE,
				StasisModule.INSTANCE,
				VClipModule.INSTANCE,
				MovementFixModule.INSTANCE,
				NoJumpDelayModule.INSTANCE,
				NoSlowModule.INSTANCE,
				ClickGuiModule.INSTANCE,
				JoinClaimModule.INSTANCE,
				PingSpoofModule.INSTANCE,
				TeleportAuraModule.INSTANCE,
				RegenModule.INSTANCE,
				ResourcePackSpoofModule.INSTANCE,
				LadderClutchModule.INSTANCE,
		EagleModule.INSTANCE,
				FlightModule.INSTANCE,
				LongJumpModule.INSTANCE,
				AutoToolModule.INSTANCE,
				FastPlaceModule.INSTANCE,
				FastBreakModule.INSTANCE,
				InvMoveModule.INSTANCE,
				NoFallModule.INSTANCE,
				ChatBypassModule.INSTANCE,
				OverlayModule.INSTANCE,
				NoRotateModule.INSTANCE,
				AntiVoidModule.INSTANCE,
				DisablerModule.INSTANCE,
				AutoHypixelModule.INSTANCE,
				AutoArmorModule.INSTANCE,
				InventoryManagerModule.INSTANCE,
				ChestStealerModule.INSTANCE,
				AutoChestModule.INSTANCE,
				BlinkModule.INSTANCE,
				AnimationsModule.INSTANCE,
				CapeModule.INSTANCE,
				AttackEffectsModule.INSTANCE,
				NoHurtCameraModule.INSTANCE,
				FullBrightModule.INSTANCE,
				AmbienceModule.INSTANCE,
				ESPModule.INSTANCE,
				InterfaceModule.INSTANCE,
				TargetHudModule.INSTANCE,
				VanillaFixModule.INSTANCE,
				TimerModule.INSTANCE,
				ScaffoldModule.INSTANCE,
				BreakerModule.INSTANCE,
				AntiAfkModule.INSTANCE,
				AntiDebuffModule.INSTANCE,
				ViewClipModule.INSTANCE,
				MurderMysteryModule.INSTANCE,
				KillEffectModule.INSTANCE,
				BedPlatesModule.INSTANCE,
				BedwarsUtilModule.INSTANCE,
				ChestAuraModule.INSTANCE,
				DisplaceModule.INSTANCE,
				BacktrackModule.INSTANCE,
				LagRangeModule.INSTANCE,
				RodAimbotModule.INSTANCE,
				ScoreboardModule.INSTANCE,
				ChatModule.INSTANCE,
				PotionEffectsModule.INSTANCE,
				StreamerModule.INSTANCE,
				FreeLookModule.INSTANCE,
				PostProcessingModule.INSTANCE,
				SpotifyModule.INSTANCE
		);

		AerialHypixelTransport.INSTANCE.register();

		BindRepository.INSTANCE.getBindingService();

		ConfigUtility.load();

		Runtime.getRuntime().addShutdownHook(new Thread(ConfigUtility::save));

		LagManager.INSTANCE.setDelay(0);

		OverlayRenderer.INSTANCE.hashCode();

		EventDispatcher.subscribe(GroundTickTracker.INSTANCE);
		EventDispatcher.subscribe(TeleportTickTracker.INSTANCE);
		EventDispatcher.subscribe(cc.aerial.client.packet.PacketRateDebug.INSTANCE);

		DelayManager.INSTANCE.getDelayModule();

		EventDispatcher.subscribe(new ClickGuiKeybind());
		EventDispatcher.subscribe(new MovementPassthrough());
		EventDispatcher.subscribe(VanillaMiningIsland.INSTANCE);

		EventDispatcher.subscribe(ScaffoldBlockCounter.INSTANCE);
	}
}
