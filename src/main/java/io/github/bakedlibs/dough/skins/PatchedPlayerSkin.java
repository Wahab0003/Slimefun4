package io.github.bakedlibs.dough.skins;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

/**
 * Patched implementation of PlayerSkin that handles GameProfile/PropertyMap
 * changes in Minecraft 1.21.10.
 * 
 * In 1.21.10, PropertyMap can have immutable backing maps causing
 * UnsupportedOperationException when trying to add properties. This class
 * uses reflection to work around these limitations.
 */
public class PatchedPlayerSkin {

    private static final Logger LOGGER = Logger.getLogger(PatchedPlayerSkin.class.getName());

    private final UUID uuid;
    private final String base64Texture;
    private final String profileName;

    @ParametersAreNonnullByDefault
    private PatchedPlayerSkin(UUID uuid, String base64Texture, @Nullable String profileName) {
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

    @Nonnull
    public static PatchedPlayerSkin fromBase64(@Nonnull String base64) {
        return fromBase64(base64, null);
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    public static PatchedPlayerSkin fromBase64(String base64, @Nullable String profileName) {
        UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
        return new PatchedPlayerSkin(uuid, base64, profileName);
    }

    @Nonnull
    public static PatchedPlayerSkin fromUrl(@Nonnull String url) {
        return fromUrl(url, null);
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    public static PatchedPlayerSkin fromUrl(String url, @Nullable String profileName) {
        String base64 = encodeUrl(url);
        UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
        return new PatchedPlayerSkin(uuid, base64, profileName);
    }

    @Nonnull
    public static PatchedPlayerSkin fromHashCode(@Nonnull String hashCode) {
        return fromHashCode(hashCode, null);
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    public static PatchedPlayerSkin fromHashCode(String hashCode, @Nullable String profileName) {
        String url = "http://textures.minecraft.net/texture/" + hashCode;
        return fromUrl(url, profileName);
    }

    @Nonnull
    private static String encodeUrl(@Nonnull String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets a GameProfile configured with this skin's texture.
     * This method handles the immutable PropertyMap issue in 1.21.10.
     */
    @Nonnull
    public GameProfile getProfile() {
        String name = profileName != null ? profileName : "";
        GameProfile profile = new GameProfile(uuid, name);
        applyTextureToProfile(profile);
        return profile;
    }

    /**
     * Applies the skin texture to a GameProfile, handling the PropertyMap
     * immutability issues in 1.21.10.
     */
    private void applyTextureToProfile(@Nonnull GameProfile profile) {
        Property textureProperty = new Property("textures", base64Texture);

        try {
            // First, try to get the PropertyMap
            PropertyMap properties = getPropertiesFromProfile(profile);

            if (properties != null) {
                try {
                    // Try direct modification first
                    properties.put("textures", textureProperty);
                } catch (UnsupportedOperationException e) {
                    // PropertyMap is immutable, need to use reflection to replace backing map
                    replacePropertyMapBacking(properties, textureProperty);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply texture to GameProfile", e);
        }
    }

    /**
     * Gets the PropertyMap from a GameProfile using reflection to handle
     * different API versions.
     */
    @Nullable
    private PropertyMap getPropertiesFromProfile(@Nonnull GameProfile profile) {
        try {
            // Try method first (newer API)
            Method getProperties = GameProfile.class.getMethod("getProperties");
            return (PropertyMap) getProperties.invoke(profile);
        } catch (NoSuchMethodException e) {
            // Try field access (older API)
            try {
                Field propertiesField = GameProfile.class.getDeclaredField("properties");
                propertiesField.setAccessible(true);
                return (PropertyMap) propertiesField.get(profile);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to access properties from GameProfile", ex);
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get properties from GameProfile", e);
            return null;
        }
    }

    /**
     * Replaces the backing Multimap in a PropertyMap when it's immutable.
     * This is necessary in 1.21.10 where PropertyMap uses an immutable backing map.
     */
    private void replacePropertyMapBacking(@Nonnull PropertyMap properties, @Nonnull Property textureProperty) {
        try {
            // Find the delegate field in ForwardingMultimap
            Field delegateField = findDelegateField(properties);

            if (delegateField != null) {
                delegateField.setAccessible(true);

                // Get the current delegate
                @SuppressWarnings("unchecked")
                Multimap<String, Property> oldDelegate = (Multimap<String, Property>) delegateField.get(properties);

                // Create a new mutable multimap with existing properties plus texture
                LinkedHashMultimap<String, Property> newDelegate = LinkedHashMultimap.create();
                if (oldDelegate != null) {
                    newDelegate.putAll(oldDelegate);
                }
                newDelegate.put("textures", textureProperty);

                // Replace the backing map
                delegateField.set(properties, newDelegate);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to replace PropertyMap backing", e);
        }
    }

    /**
     * Finds the delegate field in ForwardingMultimap hierarchy.
     * Skips static fields to avoid PropertyMap.EMPTY.
     */
    @Nullable
    private Field findDelegateField(@Nonnull PropertyMap properties) {
        Class<?> clazz = properties.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                // Skip static fields (avoids PropertyMap.EMPTY)
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                if (Multimap.class.isAssignableFrom(field.getType())) {
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
        }

        return null;
    }
}
