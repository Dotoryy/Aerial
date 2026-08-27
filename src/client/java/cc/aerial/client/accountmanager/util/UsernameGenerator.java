package cc.aerial.client.accountmanager.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public final class UsernameGenerator {
    private static final Random RANDOM = new Random();

    private UsernameGenerator() {
    }

    public static String[] retrieve() {
        try (InputStream stream = UsernameGenerator.class.getResourceAsStream("/usernames.txt")) {
            if (stream == null) {
                System.err.println("[UsernameGenerator] /usernames.txt not found in resources.");
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    builder.append(line.trim()).append(System.lineSeparator());
                }
            }
            if (builder.isEmpty()) {
                return null;
            }
            return builder.toString().split(System.lineSeparator());
        } catch (IOException e) {
            System.err.println("[UsernameGenerator] Error reading usernames.txt: " + e.getMessage());
            return null;
        }
    }

    public static String generate() {
        String[] generated = generate(1);
        return generated != null && generated.length != 0 ? generated[0] : null;
    }

    public static String[] generate(int amount) {
        String[] usernames = retrieve();
        if (usernames == null || usernames.length == 0) {
            return null;
        }
        List<String> acceptable = Arrays.stream(usernames)
                .filter(username -> username.length() >= 3 && username.length() <= 6)
                .collect(Collectors.toList());
        if (acceptable.isEmpty()) {
            return null;
        }
        String[] generated = new String[amount];
        for (int i = 0; i < amount; i++) {
            String prefix = acceptable.get(RANDOM.nextInt(acceptable.size()));
            String suffix = acceptable.get(RANDOM.nextInt(acceptable.size()));
            String username = applyPattern(applyPattern(prefix, suffix));
            if (username.length() > 16) {
                username = username.substring(0, 16);
            }
            generated[i] = username;
        }
        return generated;
    }

    private static String applyPattern(String prefix, String suffix) {
        switch (RANDOM.nextInt(4)) {
            case 0:
                return prefix + "_" + suffix;
            case 1:
                String sfxPart = suffix.length() >= 2 ? suffix.substring(0, 2) : suffix;
                return prefix + sfxPart + RANDOM.nextInt(100);
            case 2:
                int index = RANDOM.nextInt(Math.min(prefix.length(), suffix.length()) + 1);
                return prefix.substring(0, index) + "_" + suffix.substring(index);
            case 3:
                StringBuilder merge = new StringBuilder(prefix).append(suffix);
                if (merge.isEmpty()) {
                    return "";
                }
                int uIndex = RANDOM.nextInt(merge.length() + 1);
                int nIndex = RANDOM.nextInt(merge.length() + 1);
                if (uIndex < nIndex) {
                    merge.insert(nIndex, RANDOM.nextInt(100));
                    merge.insert(uIndex, "_");
                } else {
                    merge.insert(uIndex, "_");
                    merge.insert(nIndex, RANDOM.nextInt(100));
                }
                return merge.toString();
            default:
                return prefix + suffix;
        }
    }

    private static String applyPattern(String username) {
        if (username == null || username.isEmpty()) {
            return username;
        }
        double numberChance = 0.125;
        double upperChance = 0.25;
        char[] chars = username.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isLetter(c) && (i == 0 || chars[i - 1] == '_' || Character.isDigit(chars[i - 1])) && RANDOM.nextDouble() < upperChance) {
                chars[i] = Character.toUpperCase(c);
            } else {
                char lower = Character.toLowerCase(c);
                char replacement = getReplacement(lower);
                if (replacement != lower && RANDOM.nextDouble() < numberChance) {
                    chars[i] = replacement;
                    numberChance *= 0.5;
                }
            }
        }
        return new String(chars);
    }

    private static char getReplacement(char c) {
        return switch (c) {
            case 'a' -> '4';
            case 'e' -> '3';
            case 'i' -> '1';
            case 'o' -> '0';
            case 't' -> '7';
            case 's' -> '5';
            default -> c;
        };
    }
}
