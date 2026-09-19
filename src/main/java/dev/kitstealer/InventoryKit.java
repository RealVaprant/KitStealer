package dev.kitstealer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class InventoryKit {
    private static final int FIRST_PLAYER_INVENTORY_MENU_SLOT = 5;
    private static final int LAST_PLAYER_INVENTORY_MENU_SLOT = 45;
    private final UUID id;
    private final String name;
    private final int priority;
    private final List<KitSlot> slots;

    public InventoryKit(UUID id, String name, int priority, List<KitSlot> slots) {
        this.id = id;
        this.name = name;
        this.priority = priority;
        this.slots = new ArrayList<>(slots);
        this.slots.sort(Comparator.comparingInt(KitSlot::getMenuSlot));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public InventoryKit withPriority(int updatedPriority) {
        return new InventoryKit(id, name, updatedPriority, slots);
    }

    public List<KitSlot> getSlots() {
        return List.copyOf(slots);
    }

    public ItemStack createStackForSlot(int menuSlot, HolderLookup.Provider registryAccess) {
        for (KitSlot kitSlot : slots) {
            if (kitSlot.getMenuSlot() == menuSlot) {
                return kitSlot.createStack(registryAccess);
            }
        }

        return ItemStack.EMPTY;
    }

    public boolean loadIntoPlayer(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gameMode == null || !minecraft.player.isCreative()) {
            return false;
        }

        if (minecraft.level == null) {
            return false;
        }

        HolderLookup.Provider registryAccess = minecraft.level.registryAccess();
        for (int menuSlot = FIRST_PLAYER_INVENTORY_MENU_SLOT; menuSlot <= LAST_PLAYER_INVENTORY_MENU_SLOT; menuSlot++) {
            ItemStack stack = createStackForSlot(menuSlot, registryAccess);
            minecraft.player.inventoryMenu.getSlot(menuSlot).set(stack.copy());
            minecraft.gameMode.handleCreativeModeItemAdd(stack, menuSlot);
        }

        return true;
    }

    public static InventoryKit capturePlayerInventory(
            UUID id,
            String name,
            int priority,
            InventoryMenu inventoryMenu,
            HolderLookup.Provider registryAccess
    ) {
        List<KitSlot> kitSlots = new ArrayList<>();
        int lastAvailableMenuSlot = Math.min(LAST_PLAYER_INVENTORY_MENU_SLOT, inventoryMenu.slots.size() - 1);
        for (int menuSlot = FIRST_PLAYER_INVENTORY_MENU_SLOT; menuSlot <= lastAvailableMenuSlot; menuSlot++) {
            Slot inventorySlot = inventoryMenu.getSlot(menuSlot);
            ItemStack stack = inventorySlot.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            KitSlot kitSlot = KitSlot.capture(menuSlot, stack, registryAccess);
            if (kitSlot != null) {
                kitSlots.add(kitSlot);
            }
        }

        return new InventoryKit(id, name, priority, kitSlots);
    }
}
