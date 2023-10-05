package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.utils.ParticipantInfo;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public interface ITaskTickable extends ITask {
    void tickTask(@Nonnull ParticipantInfo pInfo, @Nonnull Map.Entry<UUID, IQuest> quest);
}
