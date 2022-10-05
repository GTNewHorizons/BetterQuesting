package bq_standard.commands;

import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import bq_standard.network.handlers.NetLootSync;
import bq_standard.rewards.loot.LootRegistry;
import com.google.gson.JsonObject;
import java.io.File;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class BQS_Commands extends CommandBase {
    @Override
    public String getCommandName() {
        return "bqs_loot";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bqs_loot default [save|load], /bqs_loot delete [all|<loot_id>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        if (args[0].equalsIgnoreCase("default")) {
            if (args[1].equalsIgnoreCase("save")) {
                NBTTagCompound jsonQ = new NBTTagCompound();
                LootRegistry.INSTANCE.writeToNBT(jsonQ, null);
                JsonHelper.WriteToFile(
                        new File(MinecraftServer.getServer().getFile("config/betterquesting/"), "DefaultLoot.json"),
                        NBTConverter.NBTtoJSON_Compound(jsonQ, new JsonObject(), true));
                sender.addChatMessage(new ChatComponentText("Loot database set as global default"));
            } else if (args[1].equalsIgnoreCase("load")) {
                File f1 = new File("config/betterquesting/DefaultLoot.json");
                NBTTagCompound j1;

                if (f1.exists()) {
                    j1 = NBTConverter.JSONtoNBT_Object(JsonHelper.ReadFromFile(f1), new NBTTagCompound(), true);
                    LootRegistry.INSTANCE.readFromNBT(j1, false);
                    NetLootSync.sendSync(null);
                    sender.addChatMessage(new ChatComponentText("Reloaded default loot database"));
                } else {
                    sender.addChatMessage(
                            new ChatComponentText(EnumChatFormatting.RED + "No default loot currently set"));
                }
            } else {
                throw new WrongUsageException(getCommandUsage(sender));
            }
        } else if (args[0].equalsIgnoreCase("delete")) {
            if (args[1].equalsIgnoreCase("all")) {
                LootRegistry.INSTANCE.reset();
                NetLootSync.sendSync(null);
                sender.addChatMessage(new ChatComponentText("Deleted all loot groups"));
            } else {
                try {
                    int idx = Integer.parseInt(args[1]);
                    if (LootRegistry.INSTANCE.removeID(idx)) {
                        NetLootSync.sendSync(null);
                        sender.addChatMessage(new ChatComponentText("Deleted loot group with ID " + idx));
                    } else {
                        sender.addChatMessage(new ChatComponentText("Unable to find loot group with ID " + idx));
                    }
                } catch (Exception e) {
                    throw new WrongUsageException(getCommandUsage(sender));
                }
            }
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }
}
