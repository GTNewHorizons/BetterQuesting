package betterquesting.commands.user;

import java.io.IOException;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import betterquesting.api.api.QuestingAPI;
import betterquesting.commands.QuestCommandBase;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;

public class QuestCommandRecover extends QuestCommandBase {

    @Override
    public String getCommand() {
        return "recover";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args) {
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        try {
            int count = SaveLoadHandler.INSTANCE.recoverPartyProgress(QuestingAPI.getQuestingUUID(player));
            if (count < 0) throw new CommandException("betterquesting.cmd.recover.unneeded");
            player.addChatMessage(new ChatComponentTranslation("betterquesting.cmd.recover.done", count));
        } catch (IllegalStateException failure) {
            throw new CommandException(failure.getMessage());
        } catch (IOException failure) {
            BetterQuesting.logger.error(
                "Could not recover quest progress for {}",
                player.getGameProfile()
                    .getId(),
                failure);
            throw new CommandException("betterquesting.cmd.recover.error");
        }
    }
}
