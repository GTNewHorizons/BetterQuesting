package betterquesting.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.ChatComponentText;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import betterquesting.api.questing.IQuest;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.questing.QuestDatabase;

public class BQ_CopyProgress extends CommandBase {

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length > 2) return null;

        MinecraftServer server = MinecraftServer.getServer();
        return CommandBase.getListOfStringsMatchingLastWord(
            args,
            server.getConfigurationManager().playerEntityList.stream()
                .map(EntityPlayerMP::getDisplayName)
                .toArray(String[]::new));
    }

    @Override
    public String getCommandName() {
        return "bq_copyquests";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList(getCommandName());
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bq_copyquests <toPlayer> |OR| /bq_copyquests <fromPlayer> <toPlayer>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || args.length > 2) {
            throw new CommandException("Wrong arguments");
        }

        UUID fromUUID = null;
        UUID toUUID = null;
        if (args.length == 2) {
            fromUUID = getPlayerUUID(args[0]);
            toUUID = getPlayerUUID(args[1]);
        } else if (sender instanceof EntityPlayer player) {
            fromUUID = player.getPersistentID();
            toUUID = getPlayerUUID(args[0]);
        }

        if (fromUUID == null || toUUID == null) {
            throw new CommandException("Wrong arguments");
        }

        long now = System.currentTimeMillis();
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, IQuest> questDBEntry : QuestDatabase.INSTANCE.entrySet()) {
            IQuest quest = questDBEntry.getValue();
            if (quest.isComplete(fromUUID) && !quest.isComplete(toUUID)) {
                quest.setComplete(toUUID, now);
                ids.add(questDBEntry.getKey());
            }
        }

        EntityPlayerMP player = getPlayerAdvanced(toUUID);
        if (player != null) {
            NetQuestSync.sendSync(player, ids, false, true);
        }

        sender.addChatMessage(new ChatComponentText("Completed " + ids.size() + " quests for " + toUUID));
    }

    public static EntityPlayerMP getPlayerAdvanced(UUID playerId) {
        MinecraftServer server = MinecraftServer.getServer();
        return server.getConfigurationManager().playerEntityList.stream()
            .filter(
                player -> player.getPersistentID()
                    .equals(playerId))
            .findFirst()
            .orElse(null);
    }

    @Nullable
    private static UUID getPlayerUUID(String data) {
        MinecraftServer server = MinecraftServer.getServer();
        try {
            return UUID.fromString(data);
        } catch (IllegalArgumentException e) {
            Optional<EntityPlayerMP> onlinePlayer = server.getConfigurationManager().playerEntityList.stream()
                .filter(
                    player -> player.getDisplayName()
                        .equals(data))
                .findFirst();

            if (onlinePlayer.isPresent()) {
                return onlinePlayer.get()
                    .getPersistentID();
            }

            GameProfile gameProfile = new PlayerProfileCache(server, MinecraftServer.field_152367_a)
                .func_152655_a(data);
            if (gameProfile != null) return gameProfile.getId();

            return null;
        }
    }
}
