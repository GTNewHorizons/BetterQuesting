package betterquesting.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import betterquesting.core.BetterQuesting;
import cpw.mods.fml.client.registry.ClientRegistry;

public class BQ_Keybindings {

    public static KeyBinding openQuests;
    public static KeyBinding toggleTracker;

    public static void RegisterKeys() {
        openQuests = new KeyBinding("key.betterquesting.quests", Keyboard.KEY_GRAVE, BetterQuesting.NAME);

        toggleTracker = new KeyBinding("key.betterquesting.tracker", Keyboard.KEY_NONE, BetterQuesting.NAME);

        ClientRegistry.registerKeyBinding(openQuests);
        ClientRegistry.registerKeyBinding(toggleTracker);
    }
}
