package bq_standard.tasks;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.util.ResourceLocation;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.client.gui.tasks.PanelTaskPartialRetrieval;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.factory.FactoryTaskPartialRetrieval;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TaskPartialRetrieval extends TaskRetrieval {

    @Override
    public String getUnlocalisedName() {
        return BQ_Standard.MODID + ".task.partial_retrieval";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskPartialRetrieval.INSTANCE.getRegistryName();
    }

    @Override
    protected void checkAndComplete(ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, boolean resync,
        List<Tuple2<UUID, int[]>> progress) {
        boolean updated = resync;

        for (Tuple2<UUID, int[]> value : progress) {
            for (int j = 0; j < requiredItems.size(); j++) {
                if (value.getSecond()[j] >= requiredItems.get(j).stackSize) {
                    updated = true;
                    progress.forEach((pair) -> setComplete(pair.getFirst()));
                    break;
                }
            }
        }

        if (updated) {
            pInfo.markDirtyParty(quest.getKey());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelTaskPartialRetrieval(rect, this);
    }
}
