package betterquesting.commands.admin;

import java.util.ArrayList;
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
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.storage.NameCache;

public class QuestCommandCheckCompletion extends QuestCommandBase {

    @Override
    public String getUsageSuffix() {
        return "<username|uuid> <quest_id>";
    }

    @Override
    public boolean validArgs(String[] args) {
        return args.length == 3;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> autoComplete(MinecraftServer server, ICommandSender sender, String[] args) {
        ArrayList<String> list = new ArrayList<>();

        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(
                args,
                NameCache.INSTANCE.getAllNames()
                    .toArray(new String[0]));
        } else if (args.length == 3) {
            for (UUID id : QuestDatabase.INSTANCE.keySet()) {
                list.add(UuidConverter.encodeUuid(id));
            }
        }

        return list;
    }

    @Override
    public String getCommand() {
        return "check";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args)
        throws CommandException {
        UUID uuid;

        uuid = this.findPlayerID(server, sender, args[1]);

        if (uuid == null) {
            throw this.getException(command);
        }

        String pName = NameCache.INSTANCE.getName(uuid);

        UUID id = UuidConverter.decodeUuid(args[2].trim());
        IQuest quest = QuestDatabase.INSTANCE.get(id);
        if (quest == null) throw getException(command);
        boolean ready = quest.getProperty(NativeProps.GLOBAL) ? SaveLoadHandler.INSTANCE.ensureAllProgressLoaded()
            : SaveLoadHandler.INSTANCE.ensurePlayerProgressLoaded(uuid);
        if (!ready) throw new CommandException("Could not load BetterQuesting progress. Check the server log.");
        sender.addChatMessage(
            new ChatComponentTranslation(
                "betterquesting.cmd.check." + quest.isComplete(uuid),
                pName,
                new ChatComponentTranslation(quest.getProperty(NativeProps.NAME))));
    }

    @Override
    public boolean isArgUsername(String[] args, int index) {
        return index == 1;
    }
}
