package cc.aerial.client.accountmanager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

public class Account {
    private String refreshToken;
    private String accessToken;
    private String username;
    private String uuid;
    private long unban;
    private AccountType type;

    public Account(String refreshToken, String accessToken, String username, String uuid) {
        this(refreshToken, accessToken, username, uuid, 0L, AccountType.PREMIUM);
    }

    public Account(String refreshToken, String accessToken, String username, String uuid, long unban, AccountType type) {
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.username = username;
        this.uuid = uuid;
        this.unban = unban;
        this.type = type;
    }

    public Account(String username, String accessToken, String uuid) {
        this("", accessToken, username, uuid, 0L, AccountType.TOKEN);
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public long getUnban() {
        return unban;
    }

    public AccountType getType() {
        return type;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setUnban(long unban) {
        this.unban = unban;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("refreshToken", refreshToken);
        json.addProperty("accessToken", accessToken);
        json.addProperty("username", username);
        json.addProperty("uuid", uuid);
        json.addProperty("unban", unban);
        json.addProperty("type", type.toString());
        return json;
    }

    public static Account fromJson(JsonObject json) {
        AccountType type = AccountType.PREMIUM;
        if (json.has("type")) {
            try {
                type = AccountType.valueOf(json.get("type").getAsString());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new Account(
                Optional.ofNullable(json.get("refreshToken")).map(JsonElement::getAsString).orElse(""),
                Optional.ofNullable(json.get("accessToken")).map(JsonElement::getAsString).orElse(""),
                Optional.ofNullable(json.get("username")).map(JsonElement::getAsString).orElse(""),
                Optional.ofNullable(json.get("uuid")).map(JsonElement::getAsString).orElse(""),
                Optional.ofNullable(json.get("unban")).map(JsonElement::getAsLong).orElse(0L),
                type);
    }
}
