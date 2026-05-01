package betterquesting.commands.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.QuestAction;
import betterquesting.api.questing.QuestMutationResult;
import betterquesting.api.utils.UuidConverter;
import betterquesting.commands.QuestCommandBase;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.sync.QuestSyncService;
import betterquesting.storage.NameCache;

public class QuestCommandReset extends QuestCommandBase {

    @Override
    public String getUsageSuffix() {
        return "[all|<quest_id>] [username|uuid]";
    }

    @Override
    public boolean validArgs(String[] args) {
        return args.length == 2 || args.length == 3;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> autoComplete(MinecraftServer server, ICommandSender sender, String[] args) {
        ArrayList<String> list = new ArrayList<>();

        if (args.length == 2) {
            list.add("all");

            for (UUID id : QuestDatabase.INSTANCE.keySet()) {
                list.add(UuidConverter.encodeUuid(id));
            }
        } else if (args.length == 3) {
            return CommandBase.getListOfStringsMatchingLastWord(
                args,
                NameCache.INSTANCE.getAllNames()
                    .toArray(new String[0]));
        }

        return list;
    }

    @Override
    public String getCommand() {
        return "reset";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args) {
        String action = args[1];

        UUID uuid = null;

        if (args.length == 3) {
            uuid = this.findPlayerID(server, sender, args[2]);

            if (uuid == null) {
                throw this.getException(command);
            }
        }

        String pName = uuid == null ? "NULL" : NameCache.INSTANCE.getName(uuid);
        if (action.equalsIgnoreCase("all")) {
            QuestMutationResult result = new QuestMutationResult();
            for (IQuest quest : QuestDatabase.INSTANCE.values()) {
                if (uuid != null) {
                    result.merge(quest.applyAction(QuestAction.resetUsers(Collections.singletonList(uuid), true)));
                } else {
                    result.merge(quest.applyAction(QuestAction.resetUsers(null, true)));
                }
            }

            SaveLoadHandler.INSTANCE.markDirty();
            QuestSyncService.applyMutationResult(result);

            if (uuid != null) {
                sender.addChatMessage(new ChatComponentTranslation("betterquesting.cmd.reset.player_all", pName));
            } else {
                sender.addChatMessage(new ChatComponentTranslation("betterquesting.cmd.reset.all_all"));
            }
        } else {
            try {
                UUID id = UuidConverter.decodeUuid(action.trim());
                IQuest quest = QuestDatabase.INSTANCE.get(id);

                if (uuid != null) {
                    QuestMutationResult result = quest
                        .applyAction(QuestAction.resetUsers(Collections.singletonList(uuid), true));
                    SaveLoadHandler.INSTANCE.markDirty();
                    sender.addChatMessage(
                        new ChatComponentTranslation(
                            "betterquesting.cmd.reset.player_single",
                            new ChatComponentTranslation(quest.getProperty(NativeProps.NAME)),
                            pName));
                    QuestSyncService.applyMutationResult(result);
                } else {
                    QuestMutationResult result = quest.applyAction(QuestAction.resetUsers(null, true));
                    SaveLoadHandler.INSTANCE.markDirty();
                    sender.addChatMessage(
                        new ChatComponentTranslation(
                            "betterquesting.cmd.reset.all_single",
                            new ChatComponentTranslation(quest.getProperty(NativeProps.NAME))));
                    QuestSyncService.applyMutationResult(result);
                }
            } catch (Exception e) {
                throw getException(command);
            }
        }
    }

    @Override
    public boolean isArgUsername(String[] args, int index) {
        return index == 2;
    }
}
