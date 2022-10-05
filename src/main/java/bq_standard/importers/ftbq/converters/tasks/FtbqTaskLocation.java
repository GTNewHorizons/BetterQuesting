package bq_standard.importers.ftbq.converters.tasks;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import bq_standard.importers.ftbq.FTBQQuestImporter;
import bq_standard.tasks.TaskLocation;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;

public class FtbqTaskLocation {
    public ITask[] convertTask(NBTTagCompound tag) {
        TaskLocation task = new TaskLocation();

        int[] data = tag.getIntArray("location");
        if (data.length <= 7) return null; // Just incase soemthing was redacted for some reason

        task.dim = data[0];
        task.x = data[1];
        task.y = data[2];
        task.z = data[3];
        task.range = Math.min(data[4], Math.min(data[5], data[6])); // FTBQ uses a dimension task for infinite range
        if (task.range <= 0) task.range = 1; // Sanity checking

        task.name = tag.hasKey("title", 8) ? tag.getString("title") : TaskLocation.getDimName(task.dim);
        FTBQQuestImporter.provideIcon(new BigItemStack(Items.compass));

        return new ITask[] {task};
    }
}
