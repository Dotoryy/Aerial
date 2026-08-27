package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.utility.PacketUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.util.List;

public final class ChatBypassModule extends Module {
    public static final ChatBypassModule INSTANCE = new ChatBypassModule();

    private static final List<String> FILTERED_WORDS = List.of(
            "kill", "retard", "anal", "beaner", "bestiality", "blowjob", "cameltoe", "chink", "clit",
            "cock", "coon", "cunnilingus", "cunt", "dick", "dildo", "dilf", "dyke", "ejaculate",
            "ejaculati", "fag", "foreskin", "gilf", "hentai", "jerkoff", "jizz", "kike",
            "kill yourself", "kill urself", "kys", "loli", "masturbate", "masturbati", "milf", "nazi",
            "nigga", "nigger", "orgy", "pedo", "penis", "porn", "pussy", "rape", "raping", "redtube",
            "schlong", "shemale", "sex", "swastika", "tits", "titties", "trannie", "tranny", "vagina",
            "whore", "xhamster", "xvideos", "end",
            "arse", "ass", "bastard", "bitch", "boob", "douche", "fuck", "hitler", "shit", "twat",
            "wank");

    private static final List<String> MESSAGE_COMMANDS = List.of(
            "ac", "achat", "pc", "pchat", "gc", "gchat", "shout", "msg", "message", "r", "reply",
            "t", "tell", "w", "whisper");

    private static final String VOWELS = "aeiouyAEIOUY";

    private static final String REPLACEMENTS = "áé¡óúÿÁÉ¡ÓÚÿ";

    private final BooleanProperty filterKnownWords = new BooleanProperty("Only filter known words", true);

    private ChatBypassModule() {
        super("Chat Bypass", "Slips chat past word filters", ModuleCategory.UTILITY);
        addProperties(filterKnownWords);
    }

    @Subscribe
    public void onSendPacket(InstantaneousSendPacketEvent event) {
        if (!(event.getPacket() instanceof ServerboundChatPacket chat)) {
            return;
        }
        String message = chat.message();
        String command = null;

        if (message.startsWith("/")) {
            int space = message.indexOf(' ');
            if (space == -1 || !isMessageCommand(message.substring(0, space))) {
                return;
            }
            command = message.substring(0, space);
            message = message.substring(space + 1);
        }
        if (message.isEmpty()) {
            return;
        }

        String rewritten = filterKnownWords.getValue() ? replaceListedWords(message) : replaceVowels(message);
        if (rewritten.equals(message)) {
            return;
        }
        if (command != null) {
            rewritten = command + " " + rewritten;
        }

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }

        PacketUtility.sendNoEvent(new ServerboundChatPacket(
                rewritten, chat.timeStamp(), chat.salt(), null, chat.lastSeenMessages()));
        event.setCancelled();
    }

    private static boolean isMessageCommand(String token) {
        String name = token.substring(1).toLowerCase();
        return MESSAGE_COMMANDS.contains(name);
    }

    private static String replaceListedWords(String message) {
        StringBuilder result = new StringBuilder();
        String[] words = message.split(" ", -1);
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            for (String filtered : FILTERED_WORDS) {
                int index = word.toLowerCase().indexOf(filtered);
                if (index == -1) {
                    continue;
                }
                String matched = word.substring(index, index + filtered.length());
                word = word.substring(0, index) + replaceVowels(matched) + word.substring(index + filtered.length());
            }
            result.append(word);
            if (i < words.length - 1) {
                result.append(' ');
            }
        }
        return result.toString();
    }

    private static String replaceVowels(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            int index = VOWELS.indexOf(character);
            result.append(index == -1 ? character : REPLACEMENTS.charAt(index));
        }
        return result.toString();
    }
}
