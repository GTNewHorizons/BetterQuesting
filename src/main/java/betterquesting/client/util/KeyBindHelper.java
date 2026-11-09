package betterquesting.client.util;

import net.minecraft.client.settings.KeyBinding;

import com.blamejared.controlling.keybinding.ComboKeyBinding;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;

public class KeyBindHelper {

    public static boolean matchesBinding(KeyBinding b, int keyCode) {
        if (keyCode != b.getKeyCode()) return false;
        if (isControllingLoaded()) return isComboModifierActive(b);

        return true;
    }

    @Optional.Method(modid = "controlling")
    private static boolean isComboModifierActive(KeyBinding b) {
        if (b instanceof ComboKeyBinding combo) {
            return combo.controlling$isModifierActive();
        }

        return true;
    }

    public static boolean isControllingLoaded() {
        return Loader.isModLoaded("controlling") && KeyBindHelper.class.getClassLoader()
            .getResource("com/blamejared/controlling/keybinding/ComboKeyBinding.class") != null;
    }
}
