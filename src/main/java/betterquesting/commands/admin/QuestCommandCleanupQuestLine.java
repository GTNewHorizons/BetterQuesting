package betterquesting.commands.admin;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api2.storage.DBEntry;
import betterquesting.commands.QuestCommandBase;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class QuestCommandCleanupQuestLine extends QuestCommandBase {
    private static final Set<ICommandSender> confirm = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public String getCommand() {
        return "cleanup_questline";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args) {
        if (confirm.add(sender)) {
            sender.addChatMessage(new ChatComponentTranslation("betterquesting.cmd.cleanup_questline.confirm"));
            return;
        }
        int removed = 0;
        synchronized (QuestLineDatabase.INSTANCE) {
            synchronized (QuestDatabase.INSTANCE) {
                for (DBEntry<IQuestLine> questLineDBEntry : QuestLineDatabase.INSTANCE.getEntries()) {
                    IQuestLine questLine = questLineDBEntry.getValue();
                    for (DBEntry<IQuestLineEntry> questLineEntryDBEntry : questLine.getEntries()) {
                        IQuest quest = QuestDatabase.INSTANCE.getValue(questLineEntryDBEntry.getID());
                        if (quest == null) {
                            removed++;
                            BetterQuesting.logger.info("Removed QuestLineEntry {} in QuestLine {} pointing into nonexistent quest {}", questLineEntryDBEntry.getValue(), questLineDBEntry.getID(), questLineEntryDBEntry.getID());
                            questLine.removeID(questLineEntryDBEntry.getID());
                        }
                    }
                }
            }
        }
        sender.addChatMessage(new ChatComponentTranslation("betterquesting.cmd.cleanup_questline.completed", removed));
    }
}
