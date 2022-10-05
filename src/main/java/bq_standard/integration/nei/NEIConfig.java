package bq_standard.integration.nei;

import bq_standard.core.BQ_Standard;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

@SuppressWarnings("unused")
public class NEIConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        API.registerRecipeHandler(new QuestRecipeHandler());
        API.registerUsageHandler(new QuestRecipeHandler());
    }

    @Override
    public String getName() {
        return BQ_Standard.NAME;
    }

    @Override
    public String getVersion() {
        return BQ_Standard.VERSION;
    }
}
