package bq_standard.importers.hqm.converters.tasks;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.JsonHelper;
import bq_standard.tasks.TaskLocation;

public class HQMTaskLocation {

    public ITask[] convertTask(JsonObject json) {
        List<ITask> tList = new ArrayList<>();

        for (JsonElement element : JsonHelper.GetArray(json, "locations")) {
            if (!(element instanceof JsonObject)) continue;
            JsonObject jLoc = element.getAsJsonObject();

            TaskLocation task = new TaskLocation();
            task.name = JsonHelper.GetString(jLoc, "name", "New Location");
            task.x = JsonHelper.GetNumber(jLoc, "x", 0)
                .intValue();
            task.y = JsonHelper.GetNumber(jLoc, "y", 0)
                .intValue();
            task.z = JsonHelper.GetNumber(jLoc, "z", 0)
                .intValue();
            task.dim = JsonHelper.GetNumber(jLoc, "dim", 0)
                .intValue();
            task.range = JsonHelper.GetNumber(jLoc, "radius", -1)
                .intValue();
            task.hideInfo = JsonHelper.GetString(jLoc, "visible", "Location")
                .equalsIgnoreCase("None");
            tList.add(task);
        }

        return tList.toArray(new ITask[0]);
    }
}
