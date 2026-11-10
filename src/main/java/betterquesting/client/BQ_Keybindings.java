package betterquesting.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.blamejared.controlling.api.ComboModifier;
import com.blamejared.controlling.api.ControllingApi;

import betterquesting.client.util.KeyBindHelper;
import betterquesting.core.BetterQuesting;
import cpw.mods.fml.client.registry.ClientRegistry;

public class BQ_Keybindings {

    public static KeyBinding openQuests;
    public static KeyBinding focusQuestSearch;

    public static void RegisterKeys() {
        openQuests = new KeyBinding("key.betterquesting.quests", Keyboard.KEY_GRAVE, BetterQuesting.NAME);
        focusQuestSearch = new KeyBinding("key.betterquesting.search", Keyboard.KEY_F, BetterQuesting.NAME);

        if (KeyBindHelper.isControllingLoaded())
            ControllingApi.setDefaultComboKeyBinding(focusQuestSearch, ComboModifier.CONTROL);

        ClientRegistry.registerKeyBinding(openQuests);
        ClientRegistry.registerKeyBinding(focusQuestSearch);
    }
}
