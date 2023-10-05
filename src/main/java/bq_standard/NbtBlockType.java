package bq_standard;

import betterquesting.api.placeholders.ItemPlaceholder;
import betterquesting.api.utils.BigItemStack;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

public class NbtBlockType // TODO: Make a version of this for the base mod and give it a dedicated editor
 {
    public Block b = Blocks.log;
    public int m = -1;
    public int n = 1;
    public String oreDict = "";
    public NBTTagCompound tags = new NBTTagCompound();

    public NbtBlockType() {}

    public NbtBlockType(Block block) {
        this.b = block;
        this.oreDict = "";
        this.tags = new NBTTagCompound();
    }

    public NbtBlockType(Block block, int meta) {
        this.b = block;
        this.m = meta;
        this.oreDict = "";
        this.tags = new NBTTagCompound();
    }

    public NBTTagCompound writeToNBT(NBTTagCompound json) {
        String bName = Block.blockRegistry.getNameForObject(b);
        json.setString("blockID", bName == null ? "" : bName);
        json.setInteger("meta", m);
        json.setTag("nbt", tags);
        json.setInteger("amount", n);
        json.setString("oreDict", oreDict == null ? "" : oreDict);
        return json;
    }

    public void readFromNBT(NBTTagCompound json) {
        b = (Block) Block.blockRegistry.getObject(json.getString("blockID"));
        m = json.getInteger("meta");
        tags = json.getCompoundTag("nbt");
        n = json.getInteger("amount");
        oreDict = json.getString("oreDict");
    }

    @Nullable
    public BigItemStack getItemStack() {
        BigItemStack stack;

        if (b == null) {
            stack = new BigItemStack(ItemPlaceholder.placeholder, n, m);
            stack.getBaseStack().setStackDisplayName("NULL");
        } else {
            if (Item.getItemFromBlock(b) == null) return null;
            stack = new BigItemStack(b, n, m);
        }

        stack.setOreDict(oreDict);
        return stack;
    }
}
