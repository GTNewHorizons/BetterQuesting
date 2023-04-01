package bq_standard.importers.hqm.converters.tasks;

import betterquesting.api.questing.tasks.ITask;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class HQMTaskTame {
    public ITask[] convertTask(JsonObject json) {
        List<ITask> tList = new ArrayList<>();
        // No Equivalent

        return tList.toArray(new ITask[0]);
    }
}
