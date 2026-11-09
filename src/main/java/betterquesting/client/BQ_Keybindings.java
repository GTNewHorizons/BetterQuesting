package betterquesting.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import betterquesting.core.BetterQuesting;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.Loader;

public class BQ_Keybindings {

    public static boolean inNH = Loader.isModLoaded("dreamcraft");
    public static KeyBinding openQuests;

    public static void RegisterKeys() {
        openQuests = new KeyBinding(
            "key.betterquesting.quests",
            inNH ? Keyboard.KEY_NONE : Keyboard.KEY_GRAVE,
            BetterQuesting.NAME);
        ClientRegistry.registerKeyBinding(openQuests);
    }
}
