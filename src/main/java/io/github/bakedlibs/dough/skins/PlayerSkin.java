package io.github.bakedlibs.dough.skins;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;

/**
 * Patched implementation of PlayerSkin that DOES NOT use CustomGameProfile.
 * 
 * In Minecraft 1.21.10, GameProfile became a final class, so we cannot
 * extend it. This implementation uses Bukkit's PlayerProfile API instead.
 * 
 * This class shadows the dough-api PlayerSkin to fix the compatibility issue.
 */
public class PlayerSkin {

    private static final Logger LOGGER = Logger.getLogger(PlayerSkin.class.getName());

    private final UUID uuid;
    private final String base64Texture;
    private final String profileName;
    private PlayerProfile cachedProfile;

    @ParametersAreNonnullByDefault
    private PlayerSkin(UUID uuid, String base64Texture, @Nullable String profileName) {
        this.uuid = uuid;
        this.base64Texture = base64Texture;
        this.profileName = profileName;
    }

    @Nonnull
    public UUID getUniqueId() {
        return uuid;
    }

    @Nullable
    public String getProfileName() {
        return profileName;
    }

    @Nonnull
    public String getBase64Texture() {
        return base64Texture;
    }

    /**
     * Creates a PlayerSkin from a base64 encoded texture string.
     * 
     * @param base64 The base64 encoded texture
     * @return A new PlayerSkin instance
     */
    @Nonnull
    public static PlayerSkin fromBase64(@Nonnull String base64) {
        return fromBase64(base64, null);
    }

    /**
     * Creates a PlayerSkin from a base64 encoded texture string with profile name.
     * 
     * @param base64      The base64 encoded texture
     * @param profileName The profile name (optional)
     * @return A new PlayerSkin instance
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static PlayerSkin fromBase64(String base64, @Nullable String profileName) {
        UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
        return new PlayerSkin(uuid, base64, profileName);
    }

    /**
     * Creates a PlayerSkin from a texture URL.
     * 
     * @param url The texture URL
     * @return A new PlayerSkin instance
     */
    @Nonnull
    public static PlayerSkin fromUrl(@Nonnull String url) {
        return fromUrl(url, null);
    }

    /**
     * Creates a PlayerSkin from a texture URL with profile name.
     * 
     * @param url         The texture URL
     * @param profileName The profile name (optional)
     * @return A new PlayerSkin instance
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static PlayerSkin fromUrl(String url, @Nullable String profileName) {
        String base64 = encodeUrl(url);
        UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
        return new PlayerSkin(uuid, base64, profileName);
    }

    /**
     * Creates a PlayerSkin from a texture hash code.
     * 
     * @param hashCode The texture hash code
     * @return A new PlayerSkin instance
     */
    @Nonnull
    public static PlayerSkin fromHashCode(@Nonnull String hashCode) {
        return fromHashCode(hashCode, null);
    }

    /**
     * Creates a PlayerSkin from a texture hash code with profile name.
     * 
     * @param hashCode    The texture hash code
     * @param profileName The profile name (optional)
     * @return A new PlayerSkin instance
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static PlayerSkin fromHashCode(String hashCode, @Nullable String profileName) {
        String url = "http://textures.minecraft.net/texture/" + hashCode;
        return fromUrl(url, profileName);
    }

    /**
     * Creates a PlayerSkin from a texture hash code with a specific UUID.
     * Used by CapacitorTextureUpdateTask.
     * 
     * @param uuid     The UUID to use for the profile
     * @param hashCode The texture hash code
     * @return A new PlayerSkin instance
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static PlayerSkin fromHashCode(UUID uuid, String hashCode) {
        String url = "http://textures.minecraft.net/texture/" + hashCode;
        String base64 = encodeUrl(url);
        return new PlayerSkin(uuid, base64, null);
    }

    /**
     * Creates a PlayerSkin by fetching the skin of a player from their UUID.
     * This is an async operation.
     * 
     * @param plugin The plugin requesting the skin
     * @param uuid   The UUID of the player
     * @return A CompletableFuture that will contain the PlayerSkin
     */
    @Nonnull
    @SuppressWarnings("deprecation")
    public static CompletableFuture<PlayerSkin> fromPlayerUUID(@Nonnull Plugin plugin, @Nonnull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(uuid);

                // Try to update the profile to fetch skin data
                // The update() method is async in Paper but we're already async
                try {
                    // Use reflection to call update() if available (Paper API)
                    java.lang.reflect.Method updateMethod = profile.getClass().getMethod("update");
                    Object future = updateMethod.invoke(profile);
                    if (future instanceof CompletableFuture<?> cf) {
                        profile = (PlayerProfile) cf.get();
                    }
                } catch (NoSuchMethodException e) {
                    // update() not available, profile might already have data or we need another
                    // approach
                    LOGGER.log(Level.FINE, "PlayerProfile.update() not available, using current profile", e);
                }

                PlayerTextures textures = profile.getTextures();
                if (textures.getSkin() != null) {
                    String url = textures.getSkin().toString();
                    String base64 = encodeUrl(url);
                    return new PlayerSkin(uuid, base64, profile.getName());
                }

                // No skin found, return empty skin
                return new PlayerSkin(uuid, "", profile.getName());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to get skin for UUID: " + uuid, e);
                return new PlayerSkin(uuid, "", null);
            }
        });
    }

    /**
     * Encodes a URL into a base64 texture string.
     */
    @Nonnull
    private static String encodeUrl(@Nonnull String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the PlayerProfile for this skin.
     * Uses Bukkit's PlayerProfile API instead of GameProfile to avoid
     * the final class issue in 1.21.10.
     * 
     * @return The PlayerProfile configured with this skin's texture
     */
    @Nonnull
    @SuppressWarnings("deprecation")
    public PlayerProfile getProfile() {
        if (cachedProfile != null) {
            return cachedProfile;
        }

        try {
            String name = profileName != null ? profileName : "CustomHead";
            PlayerProfile profile = Bukkit.createPlayerProfile(uuid, name);
            PlayerTextures textures = profile.getTextures();

            // Extract texture URL from base64
            String url = extractUrlFromBase64(base64Texture);
            if (url != null) {
                textures.setSkin(URI.create(url).toURL());
                profile.setTextures(textures);
            }

            cachedProfile = profile;
            return profile;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create PlayerProfile for skin", e);
            // Return a basic profile without texture as fallback
            return Bukkit.createPlayerProfile(uuid, profileName != null ? profileName : "CustomHead");
        }
    }

    /**
     * Extracts the texture URL from a base64 encoded texture string.
     */
    @Nullable
    private static String extractUrlFromBase64(@Nonnull String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);

            // Parse URL from JSON: {"textures":{"SKIN":{"url":"..."}}}
            int urlStart = decoded.indexOf("\"url\":\"");
            if (urlStart == -1) {
                return null;
            }
            urlStart += 7;

            int urlEnd = decoded.indexOf("\"", urlStart);
            if (urlEnd == -1) {
                return null;
            }

            return decoded.substring(urlStart, urlEnd);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to extract URL from base64", e);
            return null;
        }
    }
}
