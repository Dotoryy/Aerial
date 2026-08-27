package cc.aerial.client.accountmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final List<Account> accounts = new ArrayList<>();

    private AccountManager() {
    }

    private static File file() {
        return new File(Minecraft.getInstance().gameDirectory, "aerial" + File.separator + "accounts.json");
    }

    public static void load() {
        accounts.clear();
        File file = file();
        if (!file.exists()) {
            return;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file.toPath()));
            if (json.isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray()) {
                    accounts.add(Account.fromJson(element.getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            System.err.println("[AccountManager] Couldn't load accounts.json: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            File file = file();
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                return;
            }
            JsonArray array = new JsonArray();
            for (Account account : accounts) {
                array.add(account.toJson());
            }
            Files.writeString(file.toPath(), GSON.toJson(array));
        } catch (IOException e) {
            System.err.println("[AccountManager] Couldn't save accounts.json: " + e.getMessage());
        }
    }

    public static void addCrackedAccount(String username) {
        Optional<Account> existing = accounts.stream()
                .filter(acc -> acc.getUsername().equalsIgnoreCase(username) && acc.getType() == AccountType.CRACKED)
                .findFirst();
        if (existing.isPresent()) {
            return;
        }
        accounts.add(new Account("", "accessToken", username, "", 0L, AccountType.CRACKED));
        save();
    }
}
