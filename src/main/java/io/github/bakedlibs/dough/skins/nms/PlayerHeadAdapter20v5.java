package io.github.bakedlibs.dough.skins.nms;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.block.Skull;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

/**
 * PlayerHeadAdapter for Minecraft 1.20.5+ / 1.21.10
 * 
 * This implementation uses Bukkit's PlayerProfile API instead of NMS
 * to avoid the SkullBlockEntity.a(ResolvableProfile) method signature
 * issues in 1.21.10.
 * 
 * This shadows the class from dough-api to provide a working implementation.
 */
public class PlayerHeadAdapter20v5 {

    private static final Logger LOGGER = Logger.getLogger(PlayerHeadAdapter20v5.class.getName());

    /**
     * Sets a GameProfile on a skull block using Bukkit API.
     *
     * @param skull   The skull block state to modify
     * @param profile The GameProfile containing texture information
     */
    @ParametersAreNonnullByDefault
    public void setGameProfile(Skull skull, GameProfile profile) {
        try {
            UUID uuid = profile.getId();
            String name = profile.getName();

            if (name == null || name.isEmpty()) {
                name = "CustomHead";
            }

            // Create a new PlayerProfile
            PlayerProfile playerProfile = Bukkit.createPlayerProfile(uuid, name);
            PlayerTextures textures = playerProfile.getTextures();

            // Extract texture URL from GameProfile properties
            String textureUrl = extractTextureUrl(profile);

            if (textureUrl != null) {
                try {
                    textures.setSkin(new URL(textureUrl));
                    playerProfile.setTextures(textures);
                } catch (MalformedURLException e) {
                    LOGGER.log(Level.WARNING, "Invalid texture URL: " + textureUrl, e);
                }
            }

            // Set the profile on the skull
            skull.setOwnerProfile(playerProfile);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set GameProfile on skull", e);
        }
    }

    /**
     * Extracts the texture URL from a GameProfile's properties.
     *
     * @param profile The GameProfile to extract from
     * @return The texture URL, or null if not found
     */
    @Nonnull
    private String extractTextureUrl(@Nonnull GameProfile profile) {
        try {
            Collection<Property> textureProperties = profile.getProperties().get("textures");

            for (Property property : textureProperties) {
                String base64Value = property.value();

                if (base64Value != null && !base64Value.isEmpty()) {
                    // Decode the base64 value
                    String decoded = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);

                    // Parse URL from JSON
                    // Format:
                    // {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/..."}}}
                    return parseUrlFromJson(decoded);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to extract texture URL from GameProfile", e);
        }

        return null;
    }

    /**
     * Parses the skin URL from the decoded texture JSON.
     *
     * @param json The decoded JSON string
     * @return The extracted URL, or null if not found
     */
    private String parseUrlFromJson(@Nonnull String json) {
        // Simple JSON parsing without dependencies
        // Looking for: "url":"http://..."
        int urlKeyIndex = json.indexOf("\"url\"");
        if (urlKeyIndex == -1) {
            return null;
        }

        int colonIndex = json.indexOf(":", urlKeyIndex);
        if (colonIndex == -1) {
            return null;
        }

        int urlStartQuote = json.indexOf("\"", colonIndex);
        if (urlStartQuote == -1) {
            return null;
        }

        int urlEndQuote = json.indexOf("\"", urlStartQuote + 1);
        if (urlEndQuote == -1) {
            return null;
        }

        return json.substring(urlStartQuote + 1, urlEndQuote);
    }
}
