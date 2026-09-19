package dev.kitstealer;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

public final class KitSlot {
    private final int menuSlot;
    private final JsonElement serializedStack;

    private KitSlot(int menuSlot, JsonElement serializedStack) {
        this.menuSlot = menuSlot;
        this.serializedStack = serializedStack;
    }

    public int getMenuSlot() {
        return menuSlot;
    }

    public JsonElement getSerializedStack() {
        return serializedStack;
    }

    public ItemStack createStack(HolderLookup.Provider registryAccess) {
        return ItemStack.CODEC.parse(
                registryAccess.createSerializationContext(JsonOps.INSTANCE),
                serializedStack
        ).result().orElse(ItemStack.EMPTY);
    }

    public static KitSlot capture(int menuSlot, ItemStack stack, HolderLookup.Provider registryAccess) {
        JsonElement serializedStack = ItemStack.CODEC
                .encodeStart(registryAccess.createSerializationContext(JsonOps.INSTANCE), stack)
                .result()
                .orElse(null);

        if (serializedStack == null) {
            return null;
        }

        return new KitSlot(menuSlot, serializedStack);
    }

    public static KitSlot fromSerialized(int menuSlot, JsonElement serializedStack) {
        return new KitSlot(menuSlot, serializedStack);
    }
}
