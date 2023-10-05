package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.client.gui.tasks.PanelTaskLocation;
import bq_standard.tasks.base.TaskBase;
import bq_standard.tasks.factory.FactoryTaskLocation;
import codechicken.lib.math.MathHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskLocation extends TaskBase implements ITaskTickable {
    // region Static
    private static final HashMap<Integer, String> dimNameCache = new HashMap<>();

    public static String getDimName(int dim) {
        if (dimNameCache.containsKey(dim)) {
            return dimNameCache.get(dim);
        }

        try {
            WorldProvider prov = DimensionManager.createProviderFor(dim);
            if (prov != null) {
                dimNameCache.put(dim, prov.getDimensionName());
                return prov.getDimensionName();
            } else {
                dimNameCache.put(dim, "" + dim);
                return "" + dim;
            }
        } catch (Exception e) {
            dimNameCache.put(dim, "" + dim);
            return "" + dim;
        }
    }
    // endregion Static

    // region Properties
    public String name = "New Location";
    public String structure = "";
    public int biome = -1;
    public int x = 0;
    public int y = 0;
    public int z = 0;
    public int dim = 0;
    public int range = -1;
    public boolean visible = false;
    public boolean hideInfo = false;
    public boolean invert = false;
    public boolean taxiCab = false;

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        name = nbt.getString("name");
        x = nbt.getInteger("posX");
        y = nbt.getInteger("posY");
        z = nbt.getInteger("posZ");
        dim = nbt.getInteger("dimension");
        biome = nbt.hasKey("biome", Constants.NBT.TAG_ANY_NUMERIC) ? nbt.getInteger("biome") : -1;
        structure = nbt.getString("structure");
        range = nbt.getInteger("range");
        visible = nbt.getBoolean("visible");
        hideInfo = nbt.getBoolean("hideInfo");
        invert = nbt.getBoolean("invert") || nbt.getBoolean("invertDistance");
        taxiCab = nbt.getBoolean("taxiCabDist");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setString("name", name);
        nbt.setInteger("posX", x);
        nbt.setInteger("posY", y);
        nbt.setInteger("posZ", z);
        nbt.setInteger("dimension", dim);
        nbt.setInteger("biome", biome);
        nbt.setString("structure", structure);
        nbt.setInteger("range", range);
        nbt.setBoolean("visible", visible);
        nbt.setBoolean("hideInfo", hideInfo);
        nbt.setBoolean("invert", invert);
        nbt.setBoolean("taxiCabDist", taxiCab);

        return nbt;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.location";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskLocation.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelTaskLocation(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, Map.Entry<UUID, IQuest> quest) {
        return null;
    }
    // endregion Basic

    @Override
    public void detect(@Nonnull ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest) {
        internalDetect(pInfo, quest);
    }

    private void internalDetect(@Nonnull ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest) {
        if (!pInfo.PLAYER.isEntityAlive() || !(pInfo.PLAYER instanceof EntityPlayerMP)) return;

        EntityPlayerMP playerMP = (EntityPlayerMP) pInfo.PLAYER;

        boolean flag = false;

        if (playerMP.dimension == dim && (range <= 0 || getDistance(playerMP) <= range)) {
            if (biome >= 0
                    && biome
                            != playerMP.getServerForPlayer()
                                    .getBiomeGenForCoords(
                                            MathHelper.floor_double(playerMP.posX),
                                            MathHelper.floor_double(playerMP.posZ))
                                    .biomeID) {
                if (!invert) return;
            } else if (!StringUtils.isNullOrEmpty(structure)
                    && playerMP.getServerForPlayer()
                                    .getChunkProvider()
                                    .func_147416_a(
                                            playerMP.getServerForPlayer(),
                                            structure,
                                            MathHelper.floor_double(playerMP.posX),
                                            MathHelper.floor_double(playerMP.posY),
                                            MathHelper.floor_double(playerMP.posZ))
                            == null) {
                if (!invert) return;
            } else if (visible && range > 0) // Do not do ray casting with infinite range!
            {
                Vec3 pPos =
                        Vec3.createVectorHelper(playerMP.posX, playerMP.posY + playerMP.getEyeHeight(), playerMP.posZ);
                Vec3 tPos = Vec3.createVectorHelper(x, y, z);
                MovingObjectPosition mop = playerMP.worldObj.func_147447_a(pPos, tPos, false, true, false);

                flag = mop == null || mop.typeOfHit != MovingObjectType.BLOCK;
            } else {
                flag = true;
            }
        }

        if (flag != invert) {
            pInfo.ALL_UUIDS.forEach((uuid) -> {
                if (!isComplete(uuid)) setComplete(uuid);
            });
            pInfo.markDirtyParty(quest.getKey());
        }
    }

    private double getDistance(EntityPlayer player) {
        if (!taxiCab) {
            return player.getDistance(x, y, z);
        } else {
            return Math.abs(player.posX - x) + Math.abs(player.posY - y) + Math.abs(player.posZ - z);
        }
    }

    @Override
    public void tickTask(@Nonnull ParticipantInfo pInfo, @Nonnull Map.Entry<UUID, IQuest> quest) {
        if (pInfo.PLAYER.ticksExisted % 100 == 0) internalDetect(pInfo, quest);
    }

    @Override
    public List<String> getTextsForSearch() {
        return Collections.singletonList(getDimName(dim));
    }
}
