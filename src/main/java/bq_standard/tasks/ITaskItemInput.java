package bq_standard.tasks;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;

import java.util.List;

public interface ITaskItemInput extends ITask {
    List<BigItemStack> getItemInputs();
}
