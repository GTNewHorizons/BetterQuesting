package bq_standard.core.proxies;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import bq_standard.client.theme.BQSTextures;
import bq_standard.importers.DummyImporter;
import bq_standard.importers.hqm.HQMBagImporter;
import bq_standard.importers.hqm.HQMQuestImporter;
import bq_standard.integration.nei.IMCForNEI;

public class ClientProxy extends CommonProxy {
    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public void registerHandlers() {
        super.registerHandlers();

        IMCForNEI.IMCSender();
    }

    @Override
    public void registerExpansion() {
        super.registerExpansion();

        QuestingAPI.getAPI(ApiReference.IMPORT_REG).registerImporter(DummyImporter.INSTANCE);
        QuestingAPI.getAPI(ApiReference.IMPORT_REG).registerImporter(HQMQuestImporter.INSTANCE);
        QuestingAPI.getAPI(ApiReference.IMPORT_REG).registerImporter(HQMBagImporter.INSTANCE);

        BQSTextures.registerTextures();
    }
}
