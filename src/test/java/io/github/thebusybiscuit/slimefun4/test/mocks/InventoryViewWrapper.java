package io.github.thebusybiscuit.slimefun4.test.mocks;

import be.seeseemelk.mockbukkit.inventory.InventoryViewMock;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Temporary class which implements {@link #getItem(int)} and
 * {@link #setItem(int, ItemStack)}
 * provided {@link #getInventory(int)} and {@link #convertSlot(int)} are
 * implemented by the backing
 * {@link InventoryView}
 * <p>
 * This class should be replaced by MockBukkit when
 * <a href="https://github.com/MockBukkit/MockBukkit/pull/1011">this pr</a>
 * is merged.
 * <br>
 * Code is taken directly from CraftBukkit <a href=
 * "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftAbstractInventoryView.java">here</a>.
 *
 * @author md5sha256
 */
public class InventoryViewWrapper extends InventoryViewMock {

    private InventoryViewWrapper(HumanEntity player,
            String name,
            Inventory top,
            Inventory bottom,
            InventoryType type) {
        super(player, name, top, bottom, type);
    }

    @Nonnull
    public static InventoryViewWrapper wrap(@Nonnull InventoryView inventoryView) {
        HumanEntity player = inventoryView.getPlayer();
        String name = inventoryView.getTitle();
        Inventory top = inventoryView.getTopInventory();
        Inventory bottom = inventoryView.getBottomInventory();
        InventoryType inventoryType = inventoryView.getType();
        return new InventoryViewWrapper(player, name, top, bottom, inventoryType);
    }

    @Override
    @Nullable
    public ItemStack getItem(int slot) {
        Inventory inventory = getInventory(slot);
        return (inventory == null) ? null : inventory.getItem(convertSlot(slot));
    }

    @Override
    public void setItem(int slot, @Nullable ItemStack item) {
        Inventory inventory = getInventory(slot);
        if (inventory != null) {
            inventory.setItem(convertSlot(slot), item);
        }
    }

    /**
     * Opens the inventory view for the player.
     * Required by InventoryView interface in Minecraft 1.21+.
     */
    @Override
    public void open() {
        // Mock implementation - do nothing in tests
        // The real implementation would open the inventory GUI
    }

    /**
     * Gets the menu type of this inventory view.
     * Required by InventoryView interface in Minecraft 1.21+.
     * 
     * @return The MenuType corresponding to this inventory
     */
    @Nonnull
    @SuppressWarnings("deprecation")
    public MenuType getMenuType() {
        // Map InventoryType to MenuType
        InventoryType type = getType();
        return switch (type) {
            case CHEST -> MenuType.GENERIC_9X3;
            case DISPENSER, DROPPER -> MenuType.GENERIC_3X3;
            case FURNACE -> MenuType.FURNACE;
            case WORKBENCH -> MenuType.CRAFTING;
            case ENCHANTING -> MenuType.ENCHANTMENT;
            case BREWING -> MenuType.BREWING_STAND;
            case MERCHANT -> MenuType.MERCHANT;
            case ENDER_CHEST -> MenuType.GENERIC_9X3;
            case ANVIL -> MenuType.ANVIL;
            case SMITHING -> MenuType.SMITHING;
            case BEACON -> MenuType.BEACON;
            case HOPPER -> MenuType.HOPPER;
            case SHULKER_BOX -> MenuType.SHULKER_BOX;
            case BARREL -> MenuType.GENERIC_9X3;
            case BLAST_FURNACE -> MenuType.BLAST_FURNACE;
            case LECTERN -> MenuType.LECTERN;
            case SMOKER -> MenuType.SMOKER;
            case LOOM -> MenuType.LOOM;
            case CARTOGRAPHY -> MenuType.CARTOGRAPHY_TABLE;
            case GRINDSTONE -> MenuType.GRINDSTONE;
            case STONECUTTER -> MenuType.STONECUTTER;
            case SMITHING_NEW -> MenuType.SMITHING;
            case CRAFTER -> MenuType.CRAFTER_3X3;
            default -> MenuType.GENERIC_9X3;
        };
    }
}
