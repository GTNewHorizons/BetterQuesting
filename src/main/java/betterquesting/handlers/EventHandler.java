package betterquesting.handlers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.UserListBansEntry;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.event.world.WorldEvent;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.events.BQLivingUpdateEvent;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.MarkDirtyPlayerEvent;
import betterquesting.api.events.QuestEvent;
import betterquesting.api.events.QuestEvent.Type;
import betterquesting.api.placeholders.FluidPlaceholder;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.party.IParty;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.cache.QuestCache.QResetTime;
import betterquesting.api2.client.gui.themes.gui_args.GArgsNone;
import betterquesting.api2.client.gui.themes.presets.PresetGUIs;
import betterquesting.api2.storage.DBEntry;
import betterquesting.client.BQ_Keybindings;
import betterquesting.client.BookmarkHandler;
import betterquesting.client.gui2.GuiHome;
import betterquesting.client.gui2.GuiQuestLines;
import betterquesting.client.themes.ThemeRegistry;
import betterquesting.commands.client.QuestCommandShow;
import betterquesting.core.BetterQuesting;
import betterquesting.network.handlers.NetBulkSync;
import betterquesting.network.handlers.NetNameSync;
import betterquesting.network.handlers.NetNotices;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NoticeConfig;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.party.PartyInvitations;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.LifeDatabase;
import betterquesting.storage.NameCache;
import betterquesting.storage.QuestSettings;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Event handling for standard quests and core BetterQuesting functionality
 */
public class EventHandler {

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onKey(InputEvent.KeyInputEvent event) {
        handleOnOpenQuests();
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onMouse(InputEvent.MouseInputEvent event) {
        handleOnOpenQuests();
    }

    @SideOnly(Side.CLIENT)
    private void handleOnOpenQuests() {
        if (BQ_Keybindings.openQuests.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (BQ_Settings.useBookmark && GuiHome.bookmark != null) {
                mc.displayGuiScreen(GuiHome.bookmark);
            } else {
                GuiScreen guiToDisplay = ThemeRegistry.INSTANCE.getGui(PresetGUIs.HOME, GArgsNone.NONE);
                if (BQ_Settings.useBookmark && BQ_Settings.skipHome) guiToDisplay = new GuiQuestLines(guiToDisplay);
                mc.displayGuiScreen(guiToDisplay);
            }
        }
    }

    /**
     * Handle quest share messages and prettify them
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        if (event.message == null) return;

        // Text is something like "<prefix>betterquesting.msg.sharequest:<questId><postfix>"
        String text = event.message.getFormattedText();
        int index = text.indexOf("betterquesting.msg.sharequest:");
        if (index == -1) return;

        int questIdIndex = index + "betterquesting.msg.sharequest:".length();

        // UUIDs in base64-encoded string form are 24 characters in length.
        if (text.length() - questIdIndex < 24) {
            event.message = new ChatComponentTranslation(
                "betterquesting.msg.share_quest_invalid",
                text.substring(questIdIndex));
            return;
        }

        final String questIdString = text.substring(questIdIndex, questIdIndex + 24);
        final UUID questId;
        try {
            questId = UuidConverter.decodeUuid(questIdString);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            event.message = new ChatComponentTranslation("betterquesting.msg.share_quest_invalid", questIdString);
            return;
        }

        IQuest quest = QuestDatabase.INSTANCE.get(questId);
        if (quest == null) {
            event.message = new ChatComponentTranslation("betterquesting.msg.share_quest_invalid", questIdString);
            return;
        }

        String questName = quest.getProperty(NativeProps.NAME);
        IChatComponent translated = new ChatComponentTranslation(
            "betterquesting.msg.share_quest",
            questIdString,
            questName);

        IChatComponent newMessage = new ChatComponentText(
            text.substring(0, index) + translated.getFormattedText() + text.substring(questIdIndex + 24));
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (QuestCache.isQuestShown(quest, QuestingAPI.getQuestingUUID(player), player)) {
            QuestCommandShow.sentViaClick = true;
            newMessage.getChatStyle()
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bq_client show " + questIdString))
                .setChatHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentTranslation("betterquesting.msg.share_quest_hover_text_success")));
        } else {
            newMessage.getChatStyle()
                .setChatHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentTranslation("betterquesting.msg.share_quest_hover_text_failure")));
        }
        event.message = newMessage;
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        BookmarkHandler.loadBookmarks(
            event.manager.getSocketAddress()
                .toString());
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.entity instanceof EntityPlayer
            && event.entity.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString()) == null) {
            event.entity.registerExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString(), new QuestCache());
        }
    }

    @SubscribeEvent
    public void onPlayerClone(Clone event) {
        QuestCache oCache = (QuestCache) event.original.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        QuestCache nCache = (QuestCache) event.entityPlayer
            .getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());

        if (oCache != null && nCache != null) {
            NBTTagCompound tmp = new NBTTagCompound();
            oCache.saveNBTData(tmp);
            nCache.loadNBTData(tmp);
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(BQLivingUpdateEvent event) {
        if (event.entityLiving.worldObj.isRemote) {
            return;
        }
        if (event.entityLiving.ticksExisted % 20 != 0) {
            return; // Only triggers once per second
        }

        EntityPlayerMP player = event.entityLiving;
        QuestCache qc = (QuestCache) player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);

        if (qc == null) {
            return;
        }

        Map<UUID, IQuest> activeQuests = QuestDatabase.INSTANCE.filterKeys(qc.getActiveQuests());
        Map<UUID, IQuest> pendingAutoClaims = QuestDatabase.INSTANCE.filterKeys(qc.getPendingAutoClaims());
        QResetTime[] pendingResets = qc.getScheduledResets();

        UUID playerId = QuestingAPI.getQuestingUUID(player);
        boolean refreshCache = false;

        // Passive quest state check every 3 seconds
        if (!editMode && player.ticksExisted % 60 == 0) {
            List<UUID> completed = new ArrayList<>();

            for (Map.Entry<UUID, IQuest> entry : activeQuests.entrySet()) {
                final UUID questId = entry.getKey();
                final IQuest quest = entry.getValue();

                if (!quest.isUnlocked(playerId)) {
                    continue; // Although it IS active, it cannot be completed yet
                }

                if (quest.canSubmit(player)) {
                    quest.update(player);
                }

                if (quest.isComplete(playerId) && !quest.canSubmit(player)) {
                    refreshCache = true;
                    qc.markQuestDirty(questId);

                    completed.add(questId);
                    if (!quest.getProperty(NativeProps.SILENT)) {
                        postPresetNotice(quest, player, 2);
                    }
                }
            }

            MinecraftForge.EVENT_BUS.post(new QuestEvent(Type.COMPLETED, playerId, completed));
        }

        // Repeatable quest resets
        if (!editMode && MinecraftServer.getServer() != null) {
            List<UUID> reset = new ArrayList<>();
            long currentTime = System.currentTimeMillis();

            for (QResetTime pendingReset : pendingResets) {
                IQuest quest = QuestDatabase.INSTANCE.get(pendingReset.questID);

                if (currentTime >= pendingReset.time && !quest.canSubmit(player)) {
                    if (quest.getProperty(NativeProps.GLOBAL)) {
                        quest.resetUser(null, false);
                    } else {
                        quest.resetUser(playerId, false);
                    }

                    refreshCache = true;
                    qc.markQuestDirty(pendingReset.questID);
                    reset.add(pendingReset.questID);
                    if (!quest.getProperty(NativeProps.SILENT)) {
                        postPresetNotice(quest, player, 1);
                    }
                } else {
                    break; // Entries are sorted by time so we fail fast and skip checking the others
                }
            }

            MinecraftForge.EVENT_BUS.post(new QuestEvent(Type.RESET, playerId, reset));
        }

        // Auto claims
        if (!editMode) {
            for (Map.Entry<UUID, IQuest> entry : pendingAutoClaims.entrySet()) {
                if (entry.getValue()
                    .canClaim(player)) {
                    entry.getValue()
                        .claimReward(player);
                    refreshCache = true;
                    qc.markQuestDirty(entry.getKey());
                    // Not going to notify of auto-claims anymore.
                    // Kinda pointless if they're already being pinged for completion
                }
            }
        }

        // Refresh the cache if something changed or every 10 seconds
        if (refreshCache || player.ticksExisted % 200 == 0) {
            qc.updateCache(player);
        }

        if (!qc.getDirtyQuests()
            .isEmpty()) {
            NetQuestSync.sendSync(player, qc.getDirtyQuests(), false, true, true);
        }
        qc.cleanAllQuests();
    }

    // TODO: Create a new message inbox system for these things. On screen popups aren't ideal in combat
    public static void postPresetNotice(IQuest quest, EntityPlayer player, int preset) {
        if (!(player instanceof EntityPlayerMP)) return;
        final BigItemStack stack = quest.getProperty(NativeProps.ICON);
        if (stack == null) return;
        ItemStack icon = stack.getBaseStack();
        UUID questId = QuestDatabase.INSTANCE.lookupKey(quest);

        String questName = quest.getProperty(NativeProps.NAME);
        String questIdStr = questId.toString();

        String mainText = "";
        String sound = "";

        switch (preset) {
            case 0: {
                mainText = "betterquesting.notice.unlock";
                sound = quest.getProperty(NativeProps.SOUND_UNLOCK);
                break;
            }
            case 1: {
                mainText = "betterquesting.notice.update";
                sound = quest.getProperty(NativeProps.SOUND_UPDATE);
                break;
            }
            case 2: {
                mainText = "betterquesting.notice.complete";
                sound = quest.getProperty(NativeProps.SOUND_COMPLETE);
                break;
            }
        }

        NoticeConfig cfg = new NoticeConfig();
        cfg.particle = quest.getProperty(NativeProps.COMPLETION_PARTICLE);
        cfg.animation = quest.getProperty(NativeProps.COMPLETION_ANIMATION);
        BigItemStack confettiBig = quest.getProperty(NativeProps.CONFETTI_ICON);
        ItemStack confettiStack = confettiBig != null ? confettiBig.getBaseStack() : null;
        cfg.confettiIcon = (confettiStack != null && confettiStack.getItem() != Items.stick) ? confettiStack : null;
        cfg.particleCount = quest.getProperty(NativeProps.PARTICLE_COUNT);
        cfg.style = quest.getProperty(NativeProps.NOTIFICATION_STYLE);
        cfg.showIcon = quest.getProperty(NativeProps.NOTIFICATION_SHOW_ICON);
        cfg.subtitleText = quest.getProperty(NativeProps.NOTIFICATION_SUBTITLE);
        cfg.duration = quest.getProperty(NativeProps.NOTIFICATION_DURATION);
        cfg.fadeIn = quest.getProperty(NativeProps.NOTIFICATION_FADE_IN);
        cfg.fadeOut = quest.getProperty(NativeProps.NOTIFICATION_FADE_OUT);
        cfg.titleScale = quest.getProperty(NativeProps.NOTIFICATION_TITLE_SCALE);
        cfg.subtitleScale = quest.getProperty(NativeProps.NOTIFICATION_SUBTITLE_SCALE);
        cfg.iconScale = quest.getProperty(NativeProps.NOTIFICATION_ICON_SCALE);
        cfg.iconOffsetY = quest.getProperty(NativeProps.NOTIFICATION_ICON_OFFSET_Y);
        cfg.posX = quest.getProperty(NativeProps.NOTIFICATION_POS_X);
        cfg.posY = quest.getProperty(NativeProps.NOTIFICATION_POS_Y);
        cfg.effectTier = quest.getProperty(NativeProps.NOTIFICATION_EFFECT);

        String customTitle = quest.getProperty(NativeProps.NOTIFICATION_TITLE);
        if (customTitle != null && !customTitle.isEmpty()) {
            mainText = customTitle;
        }

        NetNotices.sendNotice(
            quest.getProperty(NativeProps.GLOBAL) ? null : new EntityPlayerMP[] { (EntityPlayerMP) player },
            icon,
            mainText,
            questName,
            questIdStr,
            sound,
            cfg);
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.modID.equals(BetterQuesting.MODID)) {
            ConfigHandler.config.save();
            ConfigHandler.initConfigs();
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (!event.world.isRemote && BQ_Settings.curWorldDir != null && event.world.provider.dimensionId == 0) {
            SaveLoadHandler.INSTANCE.saveDatabases();
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.worldObj.isRemote || MinecraftServer.getServer() == null
            || !(event.player instanceof EntityPlayerMP)) return;

        EntityPlayerMP mpPlayer = (EntityPlayerMP) event.player;

        if (BetterQuesting.proxy.isClient() && !MinecraftServer.getServer()
            .isDedicatedServer()
            && MinecraftServer.getServer()
                .getServerOwner()
                .equals(
                    event.player.getGameProfile()
                        .getName())) {
            NameCache.INSTANCE.updateName(mpPlayer);
            return;
        }

        NetBulkSync.sendReset(mpPlayer, true, true);

        UUID questingUUID = QuestingAPI.getQuestingUUID(mpPlayer);
        DBEntry<IParty> party = PartyManager.INSTANCE.getParty(questingUUID);
        if (party != null) PartyManager.SyncPartyQuests(party.getValue(), false);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (QuestSettings.INSTANCE.getProperty(NativeProps.HARDCORE) && event.player instanceof EntityPlayerMP
            && !((EntityPlayerMP) event.player).playerConqueredTheEnd) {
            EntityPlayerMP mpPlayer = (EntityPlayerMP) event.player;

            int lives = LifeDatabase.INSTANCE.getLives(QuestingAPI.getQuestingUUID(mpPlayer));

            if (lives <= 0) {
                MinecraftServer server = MinecraftServer.getServer();

                if (server == null) {
                    return;
                }

                if (server.isSinglePlayer() && mpPlayer.getCommandSenderName()
                    .equals(server.getServerOwner())) {
                    mpPlayer.playerNetServerHandler
                        .kickPlayerFromServer("You have died. Game over, man, it\'s game over!");
                    server.deleteWorldAndStopServer();
                } else {
                    UserListBansEntry userlistbansentry = new UserListBansEntry(
                        mpPlayer.getGameProfile(),
                        null,
                        "(You just lost the game)",
                        null,
                        "Death in Hardcore");
                    server.getConfigurationManager()
                        .func_152608_h()
                        .func_152687_a(userlistbansentry);
                    mpPlayer.playerNetServerHandler
                        .kickPlayerFromServer("You have died. Game over, man, it\'s game over!");
                }
            } else {
                if (lives == 1) {
                    mpPlayer.addChatComponentMessage(new ChatComponentText("This is your last life!"));
                } else {
                    mpPlayer.addChatComponentMessage(new ChatComponentText(lives + " lives remaining!"));
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.entityLiving.worldObj.isRemote || !QuestSettings.INSTANCE.getProperty(NativeProps.HARDCORE)) {
            return;
        }

        if (event.entityLiving instanceof EntityPlayer) {
            UUID uuid = QuestingAPI.getQuestingUUID(((EntityPlayer) event.entityLiving));

            int lives = LifeDatabase.INSTANCE.getLives(uuid);
            LifeDatabase.INSTANCE.setLives(uuid, lives - 1);
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) {
            IIcon icon = event.map.registerIcon("betterquesting:fluid_placeholder");
            FluidPlaceholder.fluidPlaceholder.setIcons(icon);
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onDataUpdated(DatabaseEvent.Update event) {
        // TODO: Change this to a proper panel event. Also explain WHAT updated
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof INeedsRefresh) Minecraft.getMinecraft()
            .func_152343_a(Executors.callable(((INeedsRefresh) screen)::refreshGui));
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        MinecraftServer server = MinecraftServer.getServer();

        if (server != null && (event.command.getCommandName()
            .equalsIgnoreCase("op")
            || event.command.getCommandName()
                .equalsIgnoreCase("deop"))) {
            EntityPlayerMP playerMP = server.getConfigurationManager()
                .func_152612_a(event.parameters[0]);
            if (playerMP != null) opQueue.add(playerMP); // Has to be delayed until after the event when the command has
                                                         // executed
        }
    }

    private final ArrayDeque<EntityPlayerMP> opQueue = new ArrayDeque<>();
    private boolean openToLAN = false;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == Phase.START) {
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();

        if (!server.isDedicatedServer()) {
            boolean tmp = openToLAN;
            openToLAN = server instanceof IntegratedServer && ((IntegratedServer) server).getPublic();
            if (openToLAN && !tmp) opQueue.addAll(server.getConfigurationManager().playerEntityList);
        } else if (!openToLAN) {
            openToLAN = true;
        }

        while (!opQueue.isEmpty()) {
            EntityPlayerMP playerMP = opQueue.poll();
            if (playerMP != null && NameCache.INSTANCE.updateName(playerMP)) {
                DBEntry<IParty> party = PartyManager.INSTANCE.getParty(QuestingAPI.getQuestingUUID(playerMP));
                if (party != null) {
                    NetNameSync.quickSync(null, party.getID());
                } else {
                    NetNameSync.sendNames(
                        new EntityPlayerMP[] { playerMP },
                        new UUID[] { QuestingAPI.getQuestingUUID(playerMP) },
                        null);
                }
            }
        }

        if (server.getTickCounter() % 60 == 0) PartyInvitations.INSTANCE.cleanExpired();

        // === FIX FOR OnLivingUpdate FIRING MULTIPLE TIMES PER TICK ===
        for (EntityPlayerMP player : server.getConfigurationManager().playerEntityList) {
            MinecraftForge.EVENT_BUS.post(new BQLivingUpdateEvent(player));
        }
    }

    @SubscribeEvent
    public void onMarkDirtyPlayer(MarkDirtyPlayerEvent event) {
        SaveLoadHandler.INSTANCE.addDirtyPlayers(event.getDirtyPlayerIDs());
    }
}
