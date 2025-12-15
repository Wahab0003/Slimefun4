package io.github.bakedlibs.dough.skins;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

/**
 * Patched implementation of PlayerHead that uses Bukkit's PlayerProfile API
 * instead of GameProfile for Minecraft 1.21.10 compatibility.
 * 
 * This class shadows the dough-api PlayerHead to fix the compatibility issue
 * where GameProfile became a final class.
 */
public final class PlayerHead {

    private static final Logger LOGGER = Logger.getLogger(PlayerHead.class.getName());

    private PlayerHead() {
        // Utility class
    }

    /**
     * Gets an ItemStack representing a player head with the given skin.
     *
     * @param skin The skin to apply to the head
     * @return An ItemStack with the player head
     */
    @Nonnull
    @SuppressWarnings("deprecation")
    public static ItemStack getItemStack(@Nonnull PlayerSkin skin) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);

        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            PlayerProfile profile = skin.getProfile();
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set skull profile on ItemStack", e);
        }

        return head;
    }

    /**
     * Gets the skin currently applied to a skull block.
     *
     * @param block The skull block
     * @return The PlayerSkin, or null if none is set
     */
    @Nullable
    @ParametersAreNonnullByDefault
    public static PlayerSkin getSkin(Block block) {
        BlockState state = block.getState();

        if (state instanceof Skull skull) {
            return getFromSkull(skull);
        }

        return null;
    }

    /**
     * Extracts skin data from a skull block state.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    private static PlayerSkin getFromSkull(@Nonnull Skull skull) {
        try {
            PlayerProfile profile = skull.getOwnerProfile();

            if (profile != null && profile.getTextures().getSkin() != null) {
                String url = profile.getTextures().getSkin().toString();
                String name = profile.getName();
                return PlayerSkin.fromUrl(url, name);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not extract skin from skull", e);
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
    public static void setSkin(Block block, PlayerSkin skin) {
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
    public static void setSkin(Block block, PlayerSkin skin, boolean update) {
        BlockState state = block.getState();

        if (state instanceof Skull skull) {
            setToSkull(skull, skin);

            if (update) {
                state.update(true, false);
            }
        }
    }

    /**
     * Applies skin data to a skull block state using Bukkit's PlayerProfile API.
     */
    @SuppressWarnings("deprecation")
    private static void setToSkull(@Nonnull Skull skull, @Nonnull PlayerSkin skin) {
        try {
            PlayerProfile profile = skin.getProfile();
            skull.setOwnerProfile(profile);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set skull profile", e);
        }
    }
}
