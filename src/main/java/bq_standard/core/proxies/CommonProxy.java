package bq_standard.core.proxies;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import betterquesting.api2.registry.IRegistry;
import bq_standard.core.BQ_Standard;
import bq_standard.handlers.EventHandler;
import bq_standard.network.handlers.NetLootClaim;
import bq_standard.network.handlers.NetLootImport;
import bq_standard.network.handlers.NetLootSync;
import bq_standard.network.handlers.NetRewardChoice;
import bq_standard.network.handlers.NetScoreSync;
import bq_standard.network.handlers.NetTaskCheckbox;
import bq_standard.network.handlers.NetTaskInteract;
import bq_standard.rewards.factory.FactoryRewardChoice;
import bq_standard.rewards.factory.FactoryRewardCommand;
import bq_standard.rewards.factory.FactoryRewardItem;
import bq_standard.rewards.factory.FactoryRewardQuestCompletion;
import bq_standard.rewards.factory.FactoryRewardScoreboard;
import bq_standard.rewards.factory.FactoryRewardXP;
import bq_standard.tasks.factory.FactoryTaskBlockBreak;
import bq_standard.tasks.factory.FactoryTaskCheckbox;
import bq_standard.tasks.factory.FactoryTaskCrafting;
import bq_standard.tasks.factory.FactoryTaskFluid;
import bq_standard.tasks.factory.FactoryTaskHunt;
import bq_standard.tasks.factory.FactoryTaskInteractEntity;
import bq_standard.tasks.factory.FactoryTaskInteractItem;
import bq_standard.tasks.factory.FactoryTaskLocation;
import bq_standard.tasks.factory.FactoryTaskMeeting;
import bq_standard.tasks.factory.FactoryTaskOptionalRetrieval;
import bq_standard.tasks.factory.FactoryTaskRetrieval;
import bq_standard.tasks.factory.FactoryTaskScoreboard;
import bq_standard.tasks.factory.FactoryTaskXP;
import cpw.mods.fml.common.FMLCommonHandler;

public class CommonProxy {

    public boolean isClient() {
        return false;
    }

    public void registerHandlers() {
        EventHandler evHandle = new EventHandler();
        FMLCommonHandler.instance()
            .bus()
            .register(evHandle);
        MinecraftForge.EVENT_BUS.register(evHandle);
    }

    public void registerRenderers() {}

    public void registerExpansion() {
        IRegistry<IFactoryData<ITask, NBTTagCompound>, ITask> taskReg = QuestingAPI.getAPI(ApiReference.TASK_REG);
        taskReg.register(FactoryTaskBlockBreak.INSTANCE);
        taskReg.register(FactoryTaskCheckbox.INSTANCE);
        taskReg.register(FactoryTaskCrafting.INSTANCE);
        taskReg.register(FactoryTaskFluid.INSTANCE);
        taskReg.register(FactoryTaskHunt.INSTANCE);
        taskReg.register(FactoryTaskLocation.INSTANCE);
        taskReg.register(FactoryTaskMeeting.INSTANCE);
        taskReg.register(FactoryTaskRetrieval.INSTANCE);
        taskReg.register(FactoryTaskScoreboard.INSTANCE);
        taskReg.register(FactoryTaskXP.INSTANCE);
        taskReg.register(FactoryTaskInteractItem.INSTANCE);
        taskReg.register(FactoryTaskInteractEntity.INSTANCE);
        taskReg.register(FactoryTaskOptionalRetrieval.INSTANCE);

        IRegistry<IFactoryData<IReward, NBTTagCompound>, IReward> rewardReg = QuestingAPI
            .getAPI(ApiReference.REWARD_REG);
        rewardReg.register(FactoryRewardChoice.INSTANCE);
        rewardReg.register(FactoryRewardCommand.INSTANCE);
        rewardReg.register(FactoryRewardItem.INSTANCE);
        rewardReg.register(FactoryRewardScoreboard.INSTANCE);
        rewardReg.register(FactoryRewardXP.INSTANCE);
        rewardReg.register(FactoryRewardQuestCompletion.INSTANCE);

        NetLootSync.registerHandler();
        NetLootClaim.registerHandler();
        NetTaskCheckbox.registerHandler();
        NetScoreSync.registerHandler();
        NetRewardChoice.registerHandler();
        NetLootImport.registerHandler();
        NetTaskInteract.registerHandler();

        BQ_Standard.lootChest.setCreativeTab(QuestingAPI.getAPI(ApiReference.CREATIVE_TAB));
    }
}
