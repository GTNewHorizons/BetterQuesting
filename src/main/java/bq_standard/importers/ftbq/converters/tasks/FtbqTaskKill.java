package bq_standard.importers.ftbq.converters.tasks;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import bq_standard.importers.ftbq.FTBQQuestImporter;
import bq_standard.tasks.TaskHunt;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;

public class FtbqTaskKill {
    public ITask[] convertTask(NBTTagCompound tag) {
        TaskHunt task = new TaskHunt();

        task.idName = tag.getString("entity");
        task.targetTags = new NBTTagCompound();
        task.required = tag.getInteger("value");
        task.ignoreNBT = true;
        task.subtypes = true;

        FTBQQuestImporter.provideIcon(new BigItemStack(Items.diamond_sword));

        return new ITask[] {task};
    }
}
