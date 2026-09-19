package dev.kitstealer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KitStealerKeyMapping {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.kitstealer.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyMapping.Category.INVENTORY
    );

    private KitStealerKeyMapping() {
    }
}
