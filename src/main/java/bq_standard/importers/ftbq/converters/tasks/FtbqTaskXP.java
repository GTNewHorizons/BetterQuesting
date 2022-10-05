package bq_standard.importers.ftbq.converters.tasks;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import bq_standard.importers.ftbq.FTBQQuestImporter;
import bq_standard.tasks.TaskXP;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;

public class FtbqTaskXP {
    /*
       Fun Fact:
       As of writing this, FTBQ doesn't care if the level you submit to its task is from level 30 or level 1. This means high level players get screwed over. GG
    */
    public ITask[] convertTask(NBTTagCompound tag) {
        TaskXP task = new TaskXP();
        task.consume = true; // FTBQ is consume only. Not really sure why it doesn't allow detect
        task.amount = (int) (tag.getLong("value")
                % Integer.MAX_VALUE); // Suuuure FTBQ. The vanilla int XP level can totally exceed 2.14B
        FTBQQuestImporter.provideIcon(new BigItemStack(Items.experience_bottle));

        return new ITask[] {task};
    }
}
