package bq_standard.importers.hqm.converters.tasks;

import betterquesting.api.questing.tasks.ITask;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class HQMTaskAdvancement {
    public ITask[] convertTask(JsonObject json) {
        List<ITask> tasks = new ArrayList<>();
        // No equivalent

        return tasks.toArray(new ITask[0]);
    }
}
