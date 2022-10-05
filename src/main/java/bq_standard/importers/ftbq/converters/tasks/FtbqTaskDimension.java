package bq_standard.importers.ftbq.converters.tasks;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import bq_standard.importers.ftbq.FTBQQuestImporter;
import bq_standard.tasks.TaskLocation;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;

public class FtbqTaskDimension {
    public ITask[] converTask(NBTTagCompound tag) {
        TaskLocation task = new TaskLocation();

        task.range = -1;
        task.dim = tag.getInteger("dim");
        task.x = 0;
        task.y = 0;
        task.z = 0;
        task.visible = true;
        task.name = tag.hasKey("title", 8) ? tag.getString("title") : TaskLocation.getDimName(task.dim);

        FTBQQuestImporter.provideIcon(new BigItemStack(Blocks.portal));

        return new ITask[] {task};
    }
}
