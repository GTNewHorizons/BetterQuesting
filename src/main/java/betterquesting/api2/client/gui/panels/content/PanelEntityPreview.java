package betterquesting.api2.client.gui.panels.content;

import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.controls.IValueIO;
import betterquesting.api2.client.gui.controls.io.ValueFuncIO;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.core.BetterQuesting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.MathHelper;
import org.apache.logging.log4j.Level;
import org.lwjgl.opengl.GL11;
import twilightforest.entity.boss.EntityTFHydra;
import twilightforest.entity.boss.EntityTFHydraHead;
import twilightforest.entity.boss.EntityTFNaga;
import twilightforest.entity.boss.HydraHeadContainer;

import java.util.List;

public class PanelEntityPreview implements IGuiPanel
{
	private final IGuiRect transform;
	private boolean enabled = true;
	
    public Entity entity;
	
	private final IValueIO<Float> basePitch;
	private final IValueIO<Float> baseYaw;
	private IValueIO<Float> pitchDriver;
	private IValueIO<Float> yawDriver;
	
	private float zDepth = 100F;

    public PanelEntityPreview(IGuiRect rect, Entity entity)
    {
		this.transform = rect;
		this.entity = entity;
		
		this.basePitch = new ValueFuncIO<>(() -> 15F);
		this.pitchDriver = basePitch;
		
		this.baseYaw = new ValueFuncIO<>(() -> -30F);
		this.yawDriver = baseYaw;
    }
	
	public PanelEntityPreview setRotationFixed(float pitch, float yaw)
	{
		this.pitchDriver = basePitch;
		this.yawDriver = baseYaw;
		basePitch.writeValue(pitch);
		baseYaw.writeValue(yaw);
		return this;
	}
	
	public PanelEntityPreview setRotationDriven(IValueIO<Float> pitch, IValueIO<Float> yaw)
	{
		this.pitchDriver = pitch == null? basePitch : pitch;
		this.yawDriver = yaw == null? baseYaw : yaw;
		return this;
	}
	
	public PanelEntityPreview setDepth(float z)
	{
		this.zDepth = z;
		return this;
	}
	
	public void setEntity(Entity entity)
    {
        this.entity = entity;
    }
	
	@Override
	public void initPanel()
	{
	}
	
	@Override
	public void setEnabled(boolean state)
	{
		this.enabled = state;
	}
	
	@Override
	public boolean isEnabled()
	{
		return this.enabled;
	}
	
	@Override
	public IGuiRect getTransform()
	{
		return transform;
	}
	
	@Override
	public void drawPanel(int mx, int my, float partialTick)
    {
        if(entity == null)
            return;

		IGuiRect bounds = this.getTransform();
		GL11.glPushMatrix();
		RenderUtils.startScissor(new GuiRectangle(bounds));

		GL11.glColor4f(1F, 1F, 1F, 1F);

		int sizeX = bounds.getWidth();
		int sizeY = bounds.getHeight();
		float scale = Math.min((sizeY/2F)/entity.height, (sizeX/2F)/entity.width);
		float thePlayerPitch = Minecraft.getMinecraft().thePlayer.rotationPitch;
		float pitch;
		if (EntityList.getEntityString(entity).contains("Wisp")) {
			changeTheCameraPitch(90F);
			pitch = 90F;
		} else if (EntityList.getEntityString(entity).equals("TwilightForest.Naga")) {
			pitch = pitchDriver.readValue();
			scale /= 2;
			prepareNagaModel((EntityTFNaga) entity);
		} else if (EntityList.getEntityString(entity).equals("TwilightForest.Hydra")) {
			pitch = pitchDriver.readValue();
			prepareHydraModel((EntityTFHydra) entity);
		} else
			pitch = pitchDriver.readValue();

		RenderUtils.RenderEntity(bounds.getX() + sizeX/2, bounds.getY() + sizeY/2 + MathHelper.ceiling_float_int(entity.height * scale / 2F), (int)scale, yawDriver.readValue(), pitch, entity);
		RenderUtils.endScissor();
		GL11.glPopMatrix();

		if (thePlayerPitch != Minecraft.getMinecraft().thePlayer.rotationPitch)
			changeTheCameraPitch(thePlayerPitch);
		GL11.glColor4f(1F, 1F, 1F, 1F);
    }

	private void prepareHydraModel(EntityTFHydra hydra) {
		hydra.hc[0].headEntity = new EntityTFHydraHead(Minecraft.getMinecraft().theWorld);
		hydra.hc[0].headEntity.setPosition(0.00, 9.1, 3.6);
		hydra.hc[0].necka.setPosition(0.0, 9.0, 2.6);
		hydra.hc[0].neckb.setPosition(0.0, 7.5, 1.7);
		hydra.hc[0].neckc.setPosition(0.0, 6.0, 0.8);
		hydra.hc[0].neckd.setPosition(0.0, 4.5, -0.1);
		hydra.hc[0].necke.setPosition(0.0, 3.0, -1.0);
		hydra.hc[1].headEntity = new EntityTFHydraHead(Minecraft.getMinecraft().theWorld);
		hydra.hc[1].headEntity.setPosition(-7.5, 5.5, 4.4);
		hydra.hc[1].necka.setPosition(-7.4, 5.4, 3.4);
		hydra.hc[1].neckb.setPosition(-6.3, 4.8, 2.3);
		hydra.hc[1].neckc.setPosition(-5.2, 4.2, 1.2);
		hydra.hc[1].neckd.setPosition(-4.1, 3.6, 0.1);
		hydra.hc[1].necke.setPosition(-3.0, 3.0, -1.0);
		hydra.hc[2].headEntity = new EntityTFHydraHead(Minecraft.getMinecraft().theWorld);
		hydra.hc[2].headEntity.setPosition(7.5, 5.5, 4.4);
		hydra.hc[2].necka.setPosition(7.4, 5.4, 3.4);
		hydra.hc[2].neckb.setPosition(6.3, 4.8, 2.3);
		hydra.hc[2].neckc.setPosition(5.2, 4.2, 1.2);
		hydra.hc[2].neckd.setPosition(4.1, 3.6, 0.1);
		hydra.hc[2].necke.setPosition(3.0, 3.0, -1.0);
		hydra.hc[3].headEntity = new EntityTFHydraHead(Minecraft.getMinecraft().theWorld);
		hydra.hc[3].headEntity.setPosition(-5.1, 9.1, 0.0);
		hydra.hc[3].necka.setPosition(-5.0, 9.0, -1.0);
		hydra.hc[3].neckb.setPosition(-4.1, 7.5, -1.4);
		hydra.hc[3].neckc.setPosition(-3.2, 6.0, -1.9);
		hydra.hc[3].neckd.setPosition(-2.3, 4.5, -2.3);
		hydra.hc[3].necke.setPosition(-1.4, 3.0, -2.8);
		hydra.hc[4].headEntity = new EntityTFHydraHead(Minecraft.getMinecraft().theWorld);
		hydra.hc[4].headEntity.setPosition(5.1, 9.1, 0.0);
		hydra.hc[4].necka.setPosition(5.0, 9.0, -1.0);
		hydra.hc[4].neckb.setPosition(4.1, 7.5, -1.4);
		hydra.hc[4].neckc.setPosition(3.2, 6.0, -1.9);
		hydra.hc[4].neckd.setPosition(2.3, 4.5, -2.3);
		hydra.hc[4].necke.setPosition(1.4, 3.0, -2.8);
		hydra.hc[5].headEntity = new EntityTFHydraHead(Minecraft.getMinecraft().theWorld);
		hydra.hc[5].headEntity.setPosition(-8.9, 1.5, 0.00);
		hydra.hc[5].necka.setPosition(-8.8, 1.4, -1.0);
		hydra.hc[5].neckb.setPosition(-7.3, 1.8, -1.8);
		hydra.hc[5].neckc.setPosition(-5.8, 2.2, -2.6);
		hydra.hc[5].neckd.setPosition(-4.3, 2.6, -3.4);
		hydra.hc[5].necke.setPosition(-2.8, 3.0, -4.2);
		hydra.hc[6].headEntity = new EntityTFHydraHead(Minecraft.getMinecraft().theWorld);
		hydra.hc[6].headEntity.setPosition(8.9, 1.5, 0.0);
		hydra.hc[6].necka.setPosition(8.8, 1.4, -1.0);
		hydra.hc[6].neckb.setPosition(7.3, 1.8, -1.8);
		hydra.hc[6].neckc.setPosition(5.8, 2.2, -2.6);
		hydra.hc[6].neckd.setPosition(4.3, 2.6, -3.4);
		hydra.hc[6].necke.setPosition(2.8, 3.0, -4.2);
	}

	private void prepareNagaModel(EntityTFNaga naga) {
		naga.getParts()[0].setPosition(0, 0, -2);
		naga.getParts()[1].setPosition(0, -2, -2);
		naga.getParts()[2].setPosition(-1.366, -2, -1.634);
		naga.getParts()[3].setPosition(-2.366, -2, -.634);
		naga.getParts()[4].setPosition(-2.732, -2, .732);
		naga.getParts()[5].setPosition(-2.366, -2, 2.098);
		naga.getParts()[6].setPosition(-1.366, -2, 3.098);
		naga.getParts()[7].setPosition(0, -2, 3.464);
		naga.getParts()[8].setPosition(1.366, -2, 3.098);
		naga.getParts()[9].setPosition(2.366, -2, 2.098);
		naga.getParts()[10].setPosition(2.732, -2, .732);
		naga.getParts()[11].setPosition(2.366, -2, -.634);
		naga.getParts()[0].rotationYaw = 0;
		naga.getParts()[1].rotationYaw = 0;
		naga.getParts()[2].rotationYaw = -30;
		naga.getParts()[3].rotationYaw = -60;
		naga.getParts()[4].rotationYaw = -90;
		naga.getParts()[5].rotationYaw = -120;
		naga.getParts()[6].rotationYaw = -150;
		naga.getParts()[7].rotationYaw = 0;
		naga.getParts()[8].rotationYaw = -30;
		naga.getParts()[9].rotationYaw = -60;
		naga.getParts()[10].rotationYaw = -90;
		naga.getParts()[11].rotationYaw = -120;
	}

	private void changeTheCameraPitch(float pitch) {
		Minecraft.getMinecraft().thePlayer.rotationPitch = pitch;
		ActiveRenderInfo.updateRenderInfo(Minecraft.getMinecraft().thePlayer,
				Minecraft.getMinecraft().gameSettings.thirdPersonView == 2);
	}
	
	@Override
	public boolean onMouseClick(int mx, int my, int click)
	{
		return false;
	}
	
	@Override
	public boolean onMouseRelease(int mx, int my, int click)
	{
		return false;
	}
	
	@Override
	public boolean onMouseScroll(int mx, int my, int scroll)
	{
		return false;
	}
	
	@Override
	public boolean onKeyTyped(char c, int keycode)
	{
		return false;
	}
	
	@Override
	public List<String> getTooltip(int mx, int my)
	{
		return null;
	}
}
