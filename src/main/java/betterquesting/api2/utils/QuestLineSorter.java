package betterquesting.api2.utils;

import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public class QuestLineSorter implements Comparator<Map.Entry<UUID, IQuestLine>>
{
    private final IQuestLineDatabase QL_DB;
    
    public QuestLineSorter(IQuestLineDatabase database)
    {
        this.QL_DB = database;
    }
    
    @Override
    public int compare(Map.Entry<UUID, IQuestLine> objA, Map.Entry<UUID, IQuestLine> objB)
    {
        return Integer.compare(QL_DB.getOrderIndex(objA.getKey()), QL_DB.getOrderIndex(objB.getKey()));
    }
}
