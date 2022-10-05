package bq_standard.importers.hqm.converters.tasks;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.JsonHelper;
import bq_standard.importers.hqm.HQMUtilities;
import bq_standard.tasks.TaskInteractItem;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;

public class HQMTaskBlockPlace {
    public ITask[] convertTask(JsonObject json) {
        List<ITask> tList = new ArrayList<>();

        for (JsonElement je : JsonHelper.GetArray(json, "blocks")) {
            if (!(je instanceof JsonObject)) continue;
            JsonObject jObj = je.getAsJsonObject();

            TaskInteractItem task = new TaskInteractItem();
            BigItemStack stack = HQMUtilities.HQMStackT1(JsonHelper.GetObject(jObj, "item"));
            task.targetItem.readFromNBT(stack.writeToNBT(new NBTTagCompound()));
            task.required = JsonHelper.GetNumber(jObj, "required", 1).intValue();
            tList.add(task);
        }

        return tList.toArray(new ITask[0]);
    }
}
