package cc.aerial.client.utility;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.server.ServerConnectEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.hypixel.AerialHypixelTransport;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public final class HypixelServer implements IEventSubscriber {
    private static final HypixelServer INSTANCE = new HypixelServer();

    private boolean current;

    private HypixelServer() {
        EventDispatcher.subscribe(this);
    }

    public static boolean isCurrent() {
        return AerialHypixelTransport.INSTANCE.isConnectedToHypixel() || INSTANCE.current;
    }

    @Subscribe
    public void onServerConnect(ServerConnectEvent event) {
        String host = event.getServerAddress().getHost();
        current = host != null && host.toLowerCase().endsWith("hypixel.net");

        ModAPI.get();
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        current = false;
    }

    public static final class ModAPI {
        private static final ModAPI INSTANCE = new ModAPI();

        @Nullable
        private Location currentLocation;

        private ModAPI() {
            HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
            HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, this::onLocationReceive);
        }

        public static ModAPI get() {
            return INSTANCE;
        }

        @Nullable
        public Location getCurrentLocation() {
            return currentLocation;
        }

        private void onLocationReceive(ClientboundLocationPacket packet) {
            currentLocation = new Location(
                    packet.getServerName(),
                    packet.getServerType().orElse(null),
                    packet.getLobbyName().orElse(null),
                    packet.getMode().orElse(null),
                    packet.getMap().orElse(null)
            );
        }

        public record Location(String serverName, @Nullable ServerType serverType, @Nullable String lobbyName,
                                @Nullable String mode, @Nullable String map) {
            public boolean isLobby() {
                return lobbyName != null;
            }
        }
    }

    public static final Pattern KILL_MESSAGE_PATTERN = Pattern.compile(
            "(?<username>\\w{1,16}) ?.+(by|of|to|for|with|the|from|was|fighting|against|meet) (?<killer>\\w{1,16})",
            Pattern.CASE_INSENSITIVE);

    public static final List<Pattern> KARMA_PATTERNS = List.of(
            Pattern.compile("^ +1st Killer - ?\\[?\\w*\\+*\\]? \\w+ - \\d+(?: Kills?)?$"),
            Pattern.compile("^ *1st (?:Place ?)?(?:-|:)? ?\\[?\\w*\\+*\\]? \\w+(?: : \\d+| - \\d+(?: Points?)?| - \\d+(?: x .)?| \\(\\w+ .{1,6}\\) - \\d+ Kills?|: \\d+:\\d+| - \\d+ (?:Zombie )?(?:Kills?|Blocks? Destroyed)| - \\[LINK\\])?$"),
            Pattern.compile("^ +Winn(?:er #1 \\(\\d+ Kills\\): \\w+ \\(\\w+\\)|er(?::| - )(?:Hiders|Seekers|Defenders|Attackers|PLAYERS?|MURDERERS?|Red|Blue|RED|BLU|\\w+)(?: Team)?|ers?: ?\\[?\\w*\\+*\\]? \\w+(?:, ?\\[?\\w*\\+*\\]? \\w+)?|ing Team ?[\\:-] (?:Animals|Hunters|Red|Green|Blue|Yellow|RED|BLU|Survivors|Vampires))$"),
            Pattern.compile("^ +Alpha Infected: \\w+ \\(\\d+ infections?\\)$"),
            Pattern.compile("^ +Murderer: \\w+ \\(\\d+ Kills?\\)$"),
            Pattern.compile("^ +You survived \\d+ rounds!$"),
            Pattern.compile("^ +(?:UHC|SkyWars|Bridge|Sumo|Classic|OP|MegaWalls|Bow|NoDebuff|Blitz|Combo|Bow Spleef) (?:Duel|Doubles|3v3|4v4|Teams|Deathmatch|2v2v2v2|3v3v3v3)? ?- \\d+:\\d+$"),
            Pattern.compile("^ +They captured all wools!$"),
            Pattern.compile("^ +Game over!$"),
            Pattern.compile("^ +[\\d\\.]+k?/[\\d\\.]+k? \\w+$"),
            Pattern.compile("^ +(?:Criminal|Cop)s won the game!$"),
            Pattern.compile("^ +\\[?\\w*\\+*\\]? \\w+ - \\d+ Final Kills$"),
            Pattern.compile("^ +Zombies - \\d*:?\\d+:\\d+ \\(Round \\d+\\)$"),
            Pattern.compile("^ +. YOUR STATISTICS .$"),
            Pattern.compile("^ {36}Winner(s?)$"),
            Pattern.compile("^ {21}Bridge CTF [a-zA-Z]+ - \\d\\d:\\d\\d$")
    );
}
