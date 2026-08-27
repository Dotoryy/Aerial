package cc.aerial.client.utility;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.Map;
import java.util.Optional;

public final class BedwarsTeams {
    private static final Map<TeamColor, String> TEAM_TAGS = Map.ofEntries(
            Map.entry(TeamColor.RED, "R"),
            Map.entry(TeamColor.DARK_RED, "R"),
            Map.entry(TeamColor.BLUE, "B"),
            Map.entry(TeamColor.DARK_BLUE, "B"),
            Map.entry(TeamColor.GREEN, "G"),
            Map.entry(TeamColor.DARK_GREEN, "G"),
            Map.entry(TeamColor.YELLOW, "Y"),
            Map.entry(TeamColor.GOLD, "Y"),
            Map.entry(TeamColor.AQUA, "A"),
            Map.entry(TeamColor.DARK_AQUA, "A"),
            Map.entry(TeamColor.WHITE, "W"),
            Map.entry(TeamColor.LIGHT_PURPLE, "P"),
            Map.entry(TeamColor.DARK_PURPLE, "P"),
            Map.entry(TeamColor.GRAY, "S"),
            Map.entry(TeamColor.DARK_GRAY, "S"),
            Map.entry(TeamColor.BLACK, "S"));

    private BedwarsTeams() {
    }

    public static boolean isTeammate(LocalPlayer self, Player player) {
        if (player == self) {
            return true;
        }

        if (player.isSpectator() || player.getAbilities().instabuild) {
            return true;
        }
        if (self.isAlliedTo(player)) {
            return true;
        }
        Integer selfArmour = armourColor(self);
        if (selfArmour != null && selfArmour.equals(armourColor(player))) {
            return true;
        }
        TeamColor selfColor = teamColor(self);
        return selfColor != null && selfColor == teamColor(player);
    }

    public static String tag(Player player) {
        TeamColor color = teamColor(player);
        if (color == null) {
            return "";
        }
        String letter = TEAM_TAGS.get(color);
        return letter == null ? "" : "[" + letter + "] ";
    }

    public static String describe(Player player) {
        return tag(player) + player.getName().getString();
    }

    private static TeamColor teamColor(Player player) {
        PlayerTeam team = player.getTeam();
        if (team == null) {
            return null;
        }
        Optional<TeamColor> color = team.getColor();
        return color.orElse(null);
    }

    private static Integer armourColor(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return null;
        }
        int color = DyedItemColor.getOrDefault(chest, -1);
        return color == -1 ? null : color;
    }
}
