package io.github.bakedlibs.dough.skins;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import com.mojang.authlib.GameProfile;

import io.github.bakedlibs.dough.skins.nms.PlayerHeadAdapter20v5;

/**
 * Patched implementation of PlayerHead that uses the patched skin handling
 * for Minecraft 1.21.10 compatibility.
 */
public final class PatchedPlayerHead {

    private static final Logger LOGGER = Logger.getLogger(PatchedPlayerHead.class.getName());

    private PatchedPlayerHead() {
        // Utility class
    }

    /**
     * Gets the skin currently applied to a skull block.
     *
     * @param block The skull block
     * @return The PlayerSkin, or null if none is set
     */
    @ParametersAreNonnullByDefault
    public static PatchedPlayerSkin getSkin(Block block) {
        BlockState state = block.getState();

        if (state instanceof Skull skull) {
            return getFromSkull(skull);
        }

        return null;
    }

    /**
     * Extracts skin data from a skull block state.
     */
    private static PatchedPlayerSkin getFromSkull(@Nonnull Skull skull) {
        OfflinePlayer owner = skull.getOwningPlayer();

        if (owner != null) {
            try {
                // Try to get the profile and extract the texture
                Method getProfileMethod = skull.getClass().getMethod("getOwnerProfile");
                Object profile = getProfileMethod.invoke(skull);

                if (profile != null) {
                    // Profile is a PlayerProfile, try to extract texture info
                    Method getTexturesMethod = profile.getClass().getMethod("getTextures");
                    Object textures = getTexturesMethod.invoke(profile);

                    if (textures != null) {
                        Method getSkinMethod = textures.getClass().getMethod("getSkin");
                        Object skinUrl = getSkinMethod.invoke(textures);

                        if (skinUrl != null) {
                            return PatchedPlayerSkin.fromUrl(skinUrl.toString(), owner.getName());
                        }
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                LOGGER.log(Level.FINE, "Could not extract skin from skull", e);
            }
        }

        return null;
    }

    /**
     * Sets a skin on a skull block.
     *
     * @param block The skull block to modify
     * @param skin  The skin to apply
     */
    @ParametersAreNonnullByDefault
    public static void setSkin(Block block, PatchedPlayerSkin skin) {
        setSkin(block, skin, true);
    }

    /**
     * Sets a skin on a skull block with option to update the block state.
     *
     * @param block  The skull block to modify
     * @param skin   The skin to apply
     * @param update Whether to update the block state immediately
     */
    @ParametersAreNonnullByDefault
    public static void setSkin(Block block, PatchedPlayerSkin skin, boolean update) {
        BlockState state = block.getState();

        if (state instanceof Skull skull) {
            setToSkull(skull, skin);

            if (update) {
                state.update(true, false);
            }
        }
    }

    /**
     * Applies skin data to a skull block state.
     * Uses the patched adapter for 1.21.10 compatibility.
     */
    private static void setToSkull(@Nonnull Skull skull, @Nonnull PatchedPlayerSkin skin) {
        GameProfile profile = skin.getProfile();

        try {
            // Use the patched adapter for 1.21.10
            PlayerHeadAdapter20v5 adapter = new PlayerHeadAdapter20v5();
            adapter.setGameProfile(skull, profile);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set skull profile using patched adapter, trying fallback", e);

            // Fallback: try using Bukkit API directly
            try {
                setSkullUsingBukkitApi(skull, skin);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to set skull profile completely", ex);
            }
        }
    }

    /**
     * Fallback method to set skull skin using Bukkit API.
     */
    private static void setSkullUsingBukkitApi(@Nonnull Skull skull, @Nonnull PatchedPlayerSkin skin) throws Exception {
        // Try using the Bukkit PlayerProfile API
        Method createProfileMethod = org.bukkit.Bukkit.class.getMethod("createPlayerProfile",
                java.util.UUID.class, String.class);

        String name = skin.getProfileName() != null ? skin.getProfileName() : "";
        Object playerProfile = createProfileMethod.invoke(null, skin.getUniqueId(), name);

        // Get textures and set skin
        Method getTexturesMethod = playerProfile.getClass().getMethod("getTextures");
        Object textures = getTexturesMethod.invoke(playerProfile);

        // Decode the base64 to extract URL
        String base64 = skin.getBase64Texture();
        String decoded = new String(java.util.Base64.getDecoder().decode(base64),
                java.nio.charset.StandardCharsets.UTF_8);

        // Extract URL from JSON
        int urlStart = decoded.indexOf("\"url\":\"") + 7;
        int urlEnd = decoded.indexOf("\"", urlStart);
        String url = decoded.substring(urlStart, urlEnd);

        Method setSkinMethod = textures.getClass().getMethod("setSkin", java.net.URL.class);
        setSkinMethod.invoke(textures, new java.net.URL(url));

        Method setTexturesMethod = playerProfile.getClass().getMethod("setTextures",
                textures.getClass().getInterfaces()[0]);
        setTexturesMethod.invoke(playerProfile, textures);

        // Set the profile on the skull
        Method setOwnerProfileMethod = skull.getClass().getMethod("setOwnerProfile",
                playerProfile.getClass().getInterfaces()[0]);
        setOwnerProfileMethod.invoke(skull, playerProfile);
    }
}
