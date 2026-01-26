package betterquesting.commands.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.UuidConverter;
import betterquesting.commands.QuestCommandBase;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestDatabase;
import betterquesting.storage.NameCache;

public class QuestCommandComplete extends QuestCommandBase {

    @Override
    public String getUsageSuffix() {
        return "<all|quest_id> [username|uuid]";
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
        return "complete";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args)
        throws CommandException {
        UUID uuid;

        if (args.length >= 3) {
            uuid = this.findPlayerID(server, sender, args[2]);

            if (uuid == null) {
                throw this.getException(command);
            }
        } else {
            uuid = this.findPlayerID(server, sender, sender.getCommandSenderName());
        }

        String pName = uuid == null ? "NULL" : NameCache.INSTANCE.getName(uuid);

        if (args[1].trim()
            .equalsIgnoreCase("all")) {
            ArrayList<UUID> allIds = new ArrayList<>(QuestDatabase.INSTANCE.keySet());
            NetQuestEdit.setQuestStates(allIds, true, uuid);
            sender
                .addChatMessage(new ChatComponentTranslation("betterquesting.cmd.complete_all", allIds.size(), pName));
            return;
        }

        UUID id = UuidConverter.decodeUuid(args[1].trim());
        IQuest quest = QuestDatabase.INSTANCE.get(id);

        if (quest == null) {
            throw getException(command);
        }

        NetQuestEdit.setQuestStates(Collections.singletonList(id), true, uuid);
        sender.addChatMessage(
            new ChatComponentTranslation(
                "betterquesting.cmd.complete",
                new ChatComponentTranslation(quest.getProperty(NativeProps.NAME)),
                pName));
    }

    @Override
    public boolean isArgUsername(String[] args, int index) {
        return index == 2;
    }
}
