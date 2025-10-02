// net/liopyu/civilization/client/skin/AdventurerSkins.java
package net.liopyu.civilization.client.skin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdventurerSkins {
    private AdventurerSkins() {
    }

    private static final Map<String, PlayerSkin> CACHE = new ConcurrentHashMap<>();

    public static ResourceLocation getTexture(String username) {
        return resolve(username).texture();
    }

    public static boolean isSlim(String username) {
        return resolve(username).model() == PlayerSkin.Model.SLIM;
    }


    private static PlayerSkin resolve(String username) {
        if (username == null || username.isEmpty()) {
            return DefaultPlayerSkin.get(defaultUuid("default"));
        }

        final String key = username.toLowerCase();
        PlayerSkin cached = CACHE.get(key);
        if (cached != null) return cached;

        UUID offlineUuid = defaultUuid(key);
        GameProfile profile = new GameProfile(offlineUuid, username);

        SkinManager sm = Minecraft.getInstance().getSkinManager();
        PlayerSkin skin = sm.getInsecureSkin(profile);
        CACHE.put(key, skin);
        return skin;
    }

    private static UUID defaultUuid(String key) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static void invalidate(String username) {
        if (username != null && !username.isEmpty()) {
            CACHE.remove(username.toLowerCase());
        }
    }
}
