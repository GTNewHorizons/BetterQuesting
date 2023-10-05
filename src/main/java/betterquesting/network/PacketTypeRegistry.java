package betterquesting.network;

import betterquesting.api.network.IPacketRegistry;
import betterquesting.api2.utils.Tuple2;
import betterquesting.network.handlers.NetBulkSync;
import betterquesting.network.handlers.NetCacheSync;
import betterquesting.network.handlers.NetChapterEdit;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetImport;
import betterquesting.network.handlers.NetInviteSync;
import betterquesting.network.handlers.NetLifeSync;
import betterquesting.network.handlers.NetNameSync;
import betterquesting.network.handlers.NetNotices;
import betterquesting.network.handlers.NetPartyAction;
import betterquesting.network.handlers.NetPartySync;
import betterquesting.network.handlers.NetQuestAction;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NetSettingSync;
import betterquesting.network.handlers.NetStationEdit;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.function.Consumer;

public class PacketTypeRegistry implements IPacketRegistry
{
	public static final PacketTypeRegistry INSTANCE = new PacketTypeRegistry();
	
	private final HashMap<ResourceLocation, Consumer<Tuple2<NBTTagCompound, EntityPlayerMP>>> serverHandlers = new HashMap<>();
	private final HashMap<ResourceLocation, Consumer<NBTTagCompound>> clientHandlers = new HashMap<>();
 
	public void init()
	{
        NetQuestSync.registerHandler();
        NetQuestEdit.registerHandler();
        NetQuestAction.registerHandler();
        
        NetChapterSync.registerHandler();
        NetChapterEdit.registerHandler();
        
        NetPartySync.registerHandler();
        NetPartyAction.registerHandler();
		NetInviteSync.registerHandler();
		
		NetLifeSync.registerHandler();
		NetNameSync.registerHandler();
		NetNotices.registerHandler();
		NetStationEdit.registerHandler();
		NetImport.registerHandler();
		NetSettingSync.registerHandler();
		
		NetCacheSync.registerHandler();
		NetBulkSync.registerHandler();
	}
	
	@Override
	public void registerServerHandler(@Nonnull ResourceLocation idName, @Nonnull Consumer<Tuple2<NBTTagCompound,EntityPlayerMP>> method)
    {
        if(serverHandlers.containsKey(idName))
        {
			throw new IllegalArgumentException("Cannot register dupliate packet handler: " + idName);
        }
        
        serverHandlers.put(idName, method);
    }
	
	@Override
    @SideOnly(Side.CLIENT)
	public void registerClientHandler(@Nonnull ResourceLocation idName, @Nonnull Consumer<NBTTagCompound> method)
    {
        if(clientHandlers.containsKey(idName))
        {
			throw new IllegalArgumentException("Cannot register dupliate packet handler: " + idName);
        }
        
        clientHandlers.put(idName, method);
    }
	
	@Nullable
	public Consumer<Tuple2<NBTTagCompound,EntityPlayerMP>> getServerHandler(@Nonnull ResourceLocation idName)
    {
        return serverHandlers.get(idName);
    }
	
	@Nullable
    @SideOnly(Side.CLIENT)
	public Consumer<NBTTagCompound> getClientHandler(@Nonnull ResourceLocation idName)
    {
        return clientHandlers.get(idName);
    }
}
