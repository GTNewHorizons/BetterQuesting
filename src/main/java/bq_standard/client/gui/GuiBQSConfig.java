package bq_standard.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import bq_standard.core.BQ_Standard;
import bq_standard.handlers.ConfigHandler;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiBQSConfig extends GuiConfig {

    @SuppressWarnings("unchecked")
    public GuiBQSConfig(GuiScreen parent) {
        super(
            parent,
            (List<IConfigElement>) new ConfigElement(ConfigHandler.config.getCategory(ConfigHandler.CATEGORY_GENERAL))
                .getChildElements(),
            BQ_Standard.MODID,
            false,
            false,
            BQ_Standard.NAME);
    }
}
