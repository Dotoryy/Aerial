package cc.aerial.client.features.impl.combat;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.utility.HypixelServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

public final class AntiBotModule extends Module {
    public static final AntiBotModule INSTANCE = new AntiBotModule();

    private AntiBotModule() {
        super("Anti Bot", "Removes bots from the server", ModuleCategory.COMBAT);
    }

    @Override
    public String getSuffix() {
        return HypixelServer.isCurrent() ? "Hypixel" : "Standard";
    }

    public static boolean isBot(Player player) {
        return INSTANCE.isEnabled() && isBotRaw(player);
    }

    public static boolean isBotRaw(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            return false;
        }
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return true;
        }
        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        if (info == null) {
            return true;
        }

        if (isHypixelFakePlayer(player, info)) {
            return true;
        }

        PlayerTeam team = info.getTeam();
        if (team == null) {
            return false;
        }
        if (!team.getName().isEmpty()) {
            return false;
        }
        return team.getColor().orElse(null) == TeamColor.RED;
    }

    private static boolean isHypixelFakePlayer(Player player, PlayerInfo info) {
        if (player.getUUID().version() != 2) {
            return false;
        }
        HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        boolean inLobby = location != null && location.isLobby();
        boolean fullHealthNoTeam = player.getHealth() == 20.0f && info.getTeam() == null;
        return inLobby || fullHealthNoTeam;
    }
}
