package betterquesting.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the tracked quest and its task progress in the corner of the screen.
 */
@SideOnly(Side.CLIENT)
public class QuestTrackerHUD {

    private static boolean hiddenByKey = false;

    public static void toggleVisibility() {
        hiddenByKey = !hiddenByKey;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HELMET) return;
        if (!BQ_Settings.trackerHud || hiddenByKey) return;

        final UUID questId = QuestTrackerHandler.getTracked();
        if (questId == null) return;

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.hideGUI
            || mc.currentScreen != null
            || mc.gameSettings.showDebugInfo) return;

        final IQuest quest = QuestDatabase.INSTANCE.get(questId);
        if (quest == null) {
            // Quest book was reimported and this quest no longer exists
            QuestTrackerHandler.clear();
            return;
        }

        final UUID owner = QuestingAPI.getQuestingUUID(mc.thePlayer);
        final List<String> lines = buildLines(quest, owner);

        final float scale = BQ_Settings.trackerScale <= 0 ? 1F : BQ_Settings.trackerScale;
        final int screenWidth = (int) (event.resolution.getScaledWidth() / scale);

        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, mc.fontRenderer.getStringWidth(line));
        }

        // Positive X anchors to the left edge, negative anchors (and right aligns) to the right edge
        final boolean rightAligned = BQ_Settings.trackerOffsetX < 0;
        final int x = rightAligned ? screenWidth + BQ_Settings.trackerOffsetX - widest : BQ_Settings.trackerOffsetX;
        int y = BQ_Settings.trackerOffsetY;

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, scale);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        for (String line : lines) {
            int lineX = rightAligned ? x + widest - mc.fontRenderer.getStringWidth(line) : x;
            mc.fontRenderer.drawStringWithShadow(line, lineX, y, 0xFFFFFF);
            y += mc.fontRenderer.FONT_HEIGHT + 1;
        }
        GL11.glPopMatrix();
    }

    private static List<String> buildLines(IQuest quest, UUID owner) {
        final List<String> lines = new ArrayList<>();
        final boolean questDone = quest.isComplete(owner);
        lines.add(
            (questDone ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                + QuestTranslation.translateQuestName(QuestDatabase.INSTANCE.lookupKey(quest), quest));

        for (DBEntry<ITask> entry : quest.getTasks()
            .getEntries()) {
            final ITask task = entry.getValue();
            final boolean taskDone = task.isComplete(owner);
            final String prefix = (taskDone ? EnumChatFormatting.GREEN + "✔ " : EnumChatFormatting.GRAY + "• ");

            List<String> progress = null;
            try {
                progress = task.getHudProgress(owner);
            } catch (Exception ignored) {
                // A broken third party task must not take the whole overlay down
            }

            if (progress == null || progress.isEmpty()) {
                lines.add(prefix + QuestTranslation.translate(task.getUnlocalisedName()));
            } else {
                for (String line : progress) {
                    lines.add(prefix + line);
                }
            }
        }

        return lines;
    }
}
