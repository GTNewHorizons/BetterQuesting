package betterquesting.commands.admin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

import betterquesting.commands.QuestCommandBase;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.handlers.SaveLoadHandler.ProgressRewriteResult;
import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Rewrites saved progress in the background so offline players can be migrated without loading their records into the
 * world.
 */
public class QuestCommandMigrateProgress extends QuestCommandBase {

    @Override
    public String getCommand() {
        return "migrateprogress";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args) {
        CompletableFuture<ProgressRewriteResult> job;
        try {
            job = SaveLoadHandler.INSTANCE.rewriteSavedProgress();
        } catch (IllegalStateException failure) {
            throw new CommandException(failure.getMessage());
        }

        sender.addChatMessage(new ChatComponentTranslation("betterquesting.cmd.migrateprogress.start"));
        job.whenComplete((result, failure) -> {
            if (failure != null) {
                BetterQuesting.logger.error("BetterQuesting progress migration stopped", failure);
            } else if (server.isDedicatedServer()) {
                BetterQuesting.logger.info(
                    "BetterQuesting progress migration finished: {} converted, {} already current, {} failed",
                    result.converted,
                    result.current,
                    result.failed);
                result.failures.forEach(message -> BetterQuesting.logger.error(message));
                return;
            }
            if (server != FMLCommonHandler.instance()
                .getMinecraftServerInstance()) return;
            BetterQuesting.proxy.scheduleServerTask(Executors.callable(() -> {
                if (failure != null) {
                    sender.addChatMessage(new ChatComponentTranslation("betterquesting.cmd.error"));
                } else {
                    sender.addChatMessage(
                        new ChatComponentTranslation(
                            "betterquesting.cmd.migrateprogress.finished",
                            result.converted,
                            result.current,
                            result.failed));
                    for (String message : result.failures) {
                        sender.addChatMessage(new ChatComponentText(message));
                    }
                }
            }), false);
        });
    }
}
