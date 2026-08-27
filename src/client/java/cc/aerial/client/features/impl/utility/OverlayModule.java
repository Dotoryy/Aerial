package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.chat.ChatReceivedEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.notification.NotificationManager;
import cc.aerial.client.notification.NotificationType;
import cc.aerial.client.overlay.BedwarsStats;
import cc.aerial.client.overlay.OverlayApi;
import cc.aerial.client.overlay.OverlayBordic;
import cc.aerial.client.overlay.OverlayColumn;
import cc.aerial.client.overlay.OverlayColumnsScreen;
import cc.aerial.client.overlay.OverlayKeysScreen;
import cc.aerial.client.overlay.OverlayTags;
import cc.aerial.client.overlay.PlayerTag;
import cc.aerial.client.overlay.SkinDenick;
import cc.aerial.client.property.ActionProperty;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.KeyProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.StringProperty;
import cc.aerial.client.utility.ChatUtility;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.Multithreading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OverlayModule extends Module {
    public static final OverlayModule INSTANCE = new OverlayModule();

    private static final Pattern GAME_START = Pattern.compile("^\\s*(Bed Wars|BED WARS)\\s*$");
    private static final Pattern WHO_LINE = Pattern.compile("^ONLINE: (.+)$");
    private static final Pattern PARTY_LINE =
            Pattern.compile("^Party (?:Members|Leader|Moderators)?.*?:?\\s*(.*)$");

    public enum SortBy {
        STAR("Star"), FKDR("FKDR"), WLR("WLR"), FINALS("Finals"), WINS("Wins"), NAME("Name"),
        TAG("Tag");

        private final String label;

        SortBy(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final StringProperty apiKey = new StringProperty("API Key", "").hideIf(() -> true);

    private final BooleanProperty apiLess = new BooleanProperty("API-less", false);

    private final StringProperty urchinKey = new StringProperty("Urchin Key", "").hideIf(() -> true);
    private final StringProperty seraphKey = new StringProperty("Seraph Key", "").hideIf(() -> true);
    private final StringProperty bordicKey = new StringProperty("Bordic Key", "").hideIf(() -> true);
    private final BooleanProperty announceTags = new BooleanProperty("Announce Tags", true);
    private final ModeProperty<SortBy> sortBy = new ModeProperty<>("Sort By", SortBy.STAR);
    private final BooleanProperty holdMode = new BooleanProperty("Hold Mode", false);
    private final BooleanProperty showOnTab = new BooleanProperty("Show On Tab", true);
    private final BooleanProperty skinDenick = new BooleanProperty("Skin Denick", true);
    private final BooleanProperty sendNicked = new BooleanProperty("Send Nicked", false);
    private final BooleanProperty autoWho = new BooleanProperty("Auto Who", true);
    private final BooleanProperty autoPl = new BooleanProperty("Auto PL", false);
    private final BooleanProperty partyDetector = new BooleanProperty("Party Detector", true);
    private final ActionProperty columns = new ActionProperty("Columns",
            () -> Minecraft.getInstance().setScreenAndShow(new OverlayColumnsScreen(Minecraft.getInstance().gui.screen())));
    private final ActionProperty keys = new ActionProperty("API Keys", () -> {
        Minecraft mc = Minecraft.getInstance();
        List<StringProperty> fields = new ArrayList<>();
        List<String> hints = new ArrayList<>();
        if (!INSTANCE.apiLess.getValue()) {
            fields.add(INSTANCE.apiKey);
            hints.add("Hypixel - required");
        }
        fields.add(INSTANCE.urchinKey);
        hints.add("Optional - blacklist tags");
        fields.add(INSTANCE.seraphKey);
        hints.add("Optional - blacklist tags");
        fields.add(INSTANCE.bordicKey);
        hints.add("Optional - daily sessions");
        mc.setScreenAndShow(new OverlayKeysScreen(mc.gui.screen(),
                fields.toArray(new StringProperty[0]), hints.toArray(new String[0])));
    });

    private final KeyProperty overlayKey = new KeyProperty("Overlay Key");

    private final List<BedwarsStats> entries = new ArrayList<>();

    private final Set<UUID> partyMembers = new HashSet<>();

    private final Set<String> partyNames = new HashSet<>();

    private final Set<UUID> announcedNicks = new HashSet<>();

    private final Set<UUID> announcedTags = new HashSet<>();

    private final Set<String> gameRoster = new HashSet<>();

    private boolean warnedMissingKey;

    private boolean toggledVisible;

    private OverlayModule() {
        super("Overlay", "Bedwars stats overlay", ModuleCategory.UTILITY);
        addProperties(apiKey, apiLess, urchinKey, seraphKey, bordicKey, keys, columns, overlayKey, sortBy, holdMode,
                showOnTab, skinDenick, sendNicked, announceTags, autoWho, autoPl, partyDetector);
        EventDispatcher.subscribe(new HoldWatcher());
    }

    public List<BedwarsStats> getEntries() {
        return entries;
    }

    public boolean isPartyMember(UUID uuid) {
        return partyMembers.contains(uuid);
    }

    public boolean isOverlayVisible() {
        if (!isEnabled()) {
            return false;
        }
        if (overlayKey.isBound()) {
            if (holdMode.getValue() ? overlayKey.isDown() : toggledVisible) {
                return true;
            }
        }
        if (showOnTab.getValue()) {
            return Minecraft.getInstance().options.keyPlayerList.isDown();
        }

        return !overlayKey.isBound();
    }

    public boolean hasApiKey() {
        return apiLess.getValue() || !apiKey.getValue().trim().isEmpty();
    }

    public String getUrchinKey() {
        return urchinKey.getValue().trim();
    }

    public String getSeraphKey() {
        return seraphKey.getValue().trim();
    }

    public String getBordicKey() {
        return bordicKey.getValue().trim();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            entries.clear();
            return;
        }
        if (!hasApiKey()) {
            entries.clear();
            warnMissingKeyOnce();
            return;
        }

        if (isInHypixelLobby()) {
            entries.clear();
            return;
        }
        rebuildEntries(mc);
    }

    private boolean isInHypixelLobby() {
        if (!HypixelServer.isCurrent()) {
            return false;
        }
        HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        return location != null && location.isLobby();
    }

    private void rebuildEntries(Minecraft mc) {
        entries.clear();
        String key = apiKey.getValue().trim();

        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (!isRealPlayer(info)) {
                continue;
            }
            UUID uuid = info.getProfile().id();
            String name = info.getProfile().name();

            if (!gameRoster.isEmpty() && !gameRoster.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }

            if (skinDenick.getValue()) {
                String real = SkinDenick.resolve(info);
                if (real != null && !real.equalsIgnoreCase(name)) {
                    name = real;
                }
            }

            BedwarsStats stats = OverlayApi.get(name, uuid, key, apiLess.getValue());
            entries.add(stats);

            if (partyNames.remove(name.toLowerCase(Locale.ROOT))) {
                partyMembers.add(uuid);
            }
            if (stats.isNicked() && sendNicked.getValue() && announcedNicks.add(uuid)) {
                announceNick(name);
            }

            PlayerTag tag = OverlayTags.get(name, uuid);
            if (tag.exists() && announceTags.getValue() && announcedTags.add(uuid)) {
                announceTag(name, tag);
            }
        }

        OverlayBordic.request(entries);
        sortEntries();
    }

    private static boolean isRealPlayer(PlayerInfo info) {
        String name = info.getProfile().name();
        if (name == null || name.isEmpty() || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        int version = info.getProfile().id().version();
        if (version != 3 && version != 4) {
            return false;
        }
        return info.getProfile().properties().containsKey("textures");
    }

    private void sortEntries() {
        Comparator<BedwarsStats> comparator = switch (sortBy.getValue()) {
            case STAR -> Comparator.comparingInt(BedwarsStats::getStar);
            case FKDR -> Comparator.comparingDouble(BedwarsStats::getFkdr);
            case WLR -> Comparator.comparingDouble(BedwarsStats::getWlr);
            case FINALS -> Comparator.comparingInt(BedwarsStats::getFinalKills);
            case WINS -> Comparator.comparingInt(BedwarsStats::getWins);
            case NAME -> Comparator.comparing(stats -> stats.getName().toLowerCase(Locale.ROOT));

            case TAG -> Comparator.<BedwarsStats>comparingDouble(stats -> OverlayColumn.tagOf(stats).threat())
                    .thenComparingDouble(BedwarsStats::getFkdr);
        };

        if (sortBy.getValue() != SortBy.NAME) {
            comparator = comparator.reversed();
        }
        entries.sort(comparator);
    }

    @Subscribe
    public void onChatReceived(ChatReceivedEvent event) {
        if (!isEnabled() || !HypixelServer.isCurrent()) {
            return;
        }
        String message = event.getText().getString();

        Matcher who = WHO_LINE.matcher(message.trim());
        if (who.matches()) {
            gameRoster.clear();
            for (String raw : who.group(1).split("[,\s]+")) {
                String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "");
                if (!cleaned.isEmpty() && cleaned.length() <= 16) {
                    gameRoster.add(cleaned.toLowerCase(Locale.ROOT));
                }
            }
            return;
        }

        if (autoWho.getValue() && GAME_START.matcher(message.trim()).matches()) {
            gameRoster.clear();

            Multithreading.schedule(() -> ChatUtility.sendCommand("who"), 1, TimeUnit.SECONDS);
            if (autoPl.getValue()) {
                Multithreading.schedule(() -> ChatUtility.sendCommand("pl"), 2, TimeUnit.SECONDS);
            }
        }

        if (!partyDetector.getValue()) {
            return;
        }
        Matcher party = PARTY_LINE.matcher(message);
        if (party.matches()) {
            recordPartyNames(party.group(1));
        }
    }

    private void recordPartyNames(String listing) {
        for (String raw : listing.split("[,\\s]+")) {
            String cleaned = raw.replaceAll("\\[[^\\]]*\\]", "").replaceAll("[^A-Za-z0-9_]", "");
            if (cleaned.length() >= 3 && cleaned.length() <= 16) {
                partyNames.add(cleaned.toLowerCase(Locale.ROOT));
            }
        }
    }

    private void announceNick(String name) {
        NotificationManager.INSTANCE.builder(NotificationType.WARNING)
                .title("Nicked player")
                .description(name)
                .duration(4000)
                .buildAndPublish();
    }

    private void announceTag(String name, PlayerTag tag) {
        String reason = tag.reason().isEmpty() ? tag.shortLabel()
                : tag.shortLabel() + " - " + tag.reason();
        NotificationManager.INSTANCE.builder(NotificationType.WARNING)
                .title(name)
                .description(reason)
                .duration(5000)
                .buildAndPublish();
    }

    private void warnMissingKeyOnce() {
        if (warnedMissingKey) {
            return;
        }
        warnedMissingKey = true;
        NotificationManager.INSTANCE.builder(NotificationType.ERROR)
                .title("Overlay")
                .description("Set your Hypixel API key")
                .duration(4000)
                .buildAndPublish();
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        resetRound();
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        resetRound();

        OverlayApi.clear();
        OverlayTags.clear();
        OverlayBordic.clear();

        SkinDenick.clear();
    }

    @Override
    protected void onEnable() {
        warnedMissingKey = false;
    }

    @Override
    protected void onDisable() {
        resetRound();
    }

    private void resetRound() {
        entries.clear();
        partyMembers.clear();
        partyNames.clear();
        announcedNicks.clear();
        announcedTags.clear();
        gameRoster.clear();
    }

    public final class HoldWatcher implements IEventSubscriber {
        private boolean wasDown;

        @Subscribe
        public void onPreTick(PreGameTickEvent event) {
            boolean down = Minecraft.getInstance().gui.screen() == null && overlayKey.isDown();
            if (down && !wasDown && !holdMode.getValue()) {
                toggledVisible = !toggledVisible;
            }
            wasDown = down;
        }

        @Override
        public boolean isHandlingEvents() {
            return true;
        }
    }

    public static List<OverlayColumn> activeColumns() {
        List<OverlayColumn> active = new ArrayList<>();
        for (OverlayColumn column : OverlayColumn.VALUES) {
            if (column.isEnabled()) {
                active.add(column);
            }
        }
        return active;
    }
}
