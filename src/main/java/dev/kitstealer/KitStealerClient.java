package dev.kitstealer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class KitStealerClient implements ClientModInitializer {
    private static KitStealerClient instance;
    private KitStore kitStore;

    @Override
    public void onInitializeClient() {
        instance = this;
        KeyBindingHelper.registerKeyBinding(KitStealerKeyMapping.OPEN_MENU);
        Minecraft minecraft = Minecraft.getInstance();
        kitStore = KitStore.load(minecraft.gameDirectory.toPath());
    }

    public static void tick(Minecraft minecraft) {
        if (!KitStealerKeyMapping.OPEN_MENU.consumeClick() || minecraft.player == null) {
            return;
        }

        if (minecraft.screen == null || minecraft.screen instanceof AbstractContainerScreen<?>) {
            KitStore loadedKitStore = getKitStore();
            if (loadedKitStore != null) {
                KitStealerScreen.open(minecraft, loadedKitStore);
            }
        }
    }

    private static KitStore getKitStore() {
        return instance == null ? null : instance.kitStore;
    }
}
