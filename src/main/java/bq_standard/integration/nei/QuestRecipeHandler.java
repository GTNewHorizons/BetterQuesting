package bq_standard.integration.nei;

import static codechicken.lib.gui.GuiDraw.changeTexture;
import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;
import static net.minecraft.util.EnumChatFormatting.DARK_GRAY;
import static net.minecraft.util.EnumChatFormatting.ITALIC;
import static net.minecraft.util.EnumChatFormatting.UNDERLINE;
import static net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.google.common.base.Stopwatch;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.themes.gui_args.GArgsNone;
import betterquesting.api2.client.gui.themes.presets.PresetGUIs;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.GuiHome;
import betterquesting.client.gui2.GuiQuest;
import betterquesting.client.gui2.GuiQuestLines;
import betterquesting.client.themes.ThemeRegistry;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.QuestDatabase;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.IRewardItemOutput;
import bq_standard.rewards.RewardChoice;
import bq_standard.tasks.ITaskItemInput;
import bq_standard.tasks.TaskOptionalRetrieval;
import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;

@SuppressWarnings("UnstableApiUsage")
public class QuestRecipeHandler extends TemplateRecipeHandler {

    private static final boolean debug = false;

    private static final int SLOT_SIZE = 18;
    private static final int GRID_X_COUNT = 4;
    private static final int GRID_Y_COUNT = 4;
    private static final int GRID_COUNT = GRID_X_COUNT * GRID_Y_COUNT;
    private static final int GUI_WIDTH = 166;
    private static final int LINE_SPACE = GuiDraw.fontRenderer.FONT_HEIGHT + 1;

    private Stopwatch stopwatch;
    private int textColor;
    private int textColorHovered;

    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new Rectangle(75, 59, 16, 13), getOverlayIdentifier()));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId.equals(getOverlayIdentifier())) {
            if (debug) stopwatch = Stopwatch.createStarted();
            setTextColors();
            for (Map.Entry<UUID, IQuest> entry : getVisibleQuests().entrySet()) {
                if (getTaskItemInputs(getTasks(entry.getValue())).isEmpty()
                    && getRewardItemOutputs(getRewards(entry.getValue())).isEmpty()) {
                    continue;
                }
                this.arecipes.add(new CachedQuestRecipe(entry));
            }
            if (debug) {
                BQ_Standard.logger
                    .debug(String.format("took %s: loadCraftingRecipes(String, Object...)", stopwatch.stop()));
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (debug) stopwatch = Stopwatch.createStarted();
        setTextColors();
        for (Map.Entry<UUID, IQuest> entry : getVisibleQuests().entrySet()) {
            for (BigItemStack compareTo : getRewardItemOutputs(getRewards(entry.getValue()))) {
                if (matchStack(result, compareTo)) {
                    this.arecipes.add(new CachedQuestRecipe(entry));
                    break;
                }
            }
        }
        if (debug) {
            BQ_Standard.logger.debug(String.format("took %s: loadCraftingRecipes(ItemStack)", stopwatch.stop()));
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (debug) stopwatch = Stopwatch.createStarted();
        setTextColors();
        for (Map.Entry<UUID, IQuest> entry : getVisibleQuests().entrySet()) {
            for (BigItemStack compareTo : getTaskItemInputs(getTasks(entry.getValue()))) {
                if (matchStack(ingredient, compareTo)) {
                    this.arecipes.add(new CachedQuestRecipe(entry));
                    break;
                }
            }
        }
        if (debug) {
            BQ_Standard.logger.debug(String.format("took %s: loadUsageRecipes(ItemStack)", stopwatch.stop()));
        }
    }

    @Override
    public String getGuiTexture() {
        return "bq_standard:textures/gui/nei.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return "bq_quest";
    }

    @Override
    public void drawExtras(int recipeIndex) {
        CachedQuestRecipe recipe = (CachedQuestRecipe) this.arecipes.get(recipeIndex);
        String questTitle = UNDERLINE + getTextWithoutFormattingCodes(recipe.questName);

        int color;
        if (isMouseOverTitle(recipeIndex)) {
            color = textColorHovered;
        } else {
            color = textColor;
        }

        // noinspection unchecked
        List<String> titleArray = GuiDraw.fontRenderer.listFormattedStringToWidth(questTitle, GUI_WIDTH);
        int y = 16 - (titleArray.size() - 1) * LINE_SPACE;
        for (String line : titleArray) {
            GuiDraw.drawStringC(line, GUI_WIDTH / 2, y, color, false);
            y += LINE_SPACE;
        }
    }

    @Override
    public void drawBackground(int recipe) {
        GL11.glColor4f(1, 1, 1, 1);
        changeTexture(getGuiTexture());
        drawTexturedModalRect(0, 0, 0, 0, GUI_WIDTH, 105);
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int recipeIndex) {
        CachedQuestRecipe recipe = (CachedQuestRecipe) this.arecipes.get(recipeIndex);
        for (PositionedStack pStack : recipe.inputs) {
            if (stack == pStack.item && pStack instanceof CustomPositionedStack) {
                currenttip.addAll(((CustomPositionedStack) pStack).getTooltips());
            }
        }
        return currenttip;
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipeIndex) {
        if (super.mouseClicked(gui, button, recipeIndex)) return true;

        if (isMouseOverTitle(recipeIndex)) {
            CachedQuestRecipe recipe = (CachedQuestRecipe) this.arecipes.get(recipeIndex);

            // prepare "Back" behavior
            GuiScreen parentScreen;
            if (GuiHome.bookmark instanceof GuiQuest && BQ_Settings.useBookmark) {
                // back to GuiQuestLines
                parentScreen = ((GuiScreenCanvas) GuiHome.bookmark).parent;
            } else if (GuiHome.bookmark instanceof GuiScreenCanvas && BQ_Settings.useBookmark) {
                // for example, GuiQuestLines.parent is GuiHome
                // going back to home screen is not good
                parentScreen = GuiHome.bookmark;
            } else {
                // init quest screen
                parentScreen = ThemeRegistry.INSTANCE.getGui(PresetGUIs.HOME, GArgsNone.NONE);
                if (BQ_Settings.useBookmark && BQ_Settings.skipHome) {
                    parentScreen = new GuiQuestLines(parentScreen);
                }
            }

            GuiQuest toDisplay = new GuiQuest(parentScreen, recipe.questID);
            toDisplay.setPreviousScreen(Minecraft.getMinecraft().currentScreen);
            Minecraft.getMinecraft()
                .displayGuiScreen(toDisplay);
            if (BQ_Settings.useBookmark) {
                GuiHome.bookmark = toDisplay;
            }
            return true;
        }
        return false;
    }

    @Override
    public String getRecipeName() {
        return BetterQuesting.NAME;
    }

    private static Map<UUID, IQuest> getVisibleQuests() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        return QuestDatabase.INSTANCE
            .filterEntries((id, quest) -> QuestCache.isQuestShown(quest, QuestingAPI.getQuestingUUID(player), player));
    }

    private static List<ITask> getTasks(IQuest quest) {
        return quest.getTasks()
            .getEntries()
            .stream()
            .map(DBEntry::getValue)
            .collect(Collectors.toList());
    }

    private static List<BigItemStack> getTaskItemInputs(List<ITask> tasks) {
        List<BigItemStack> ret = new ArrayList<>();
        for (ITask task : tasks) {
            ret.addAll(getTaskItemInputs(task));
        }
        return ret;
    }

    private static List<BigItemStack> getTaskItemInputs(ITask task) {
        if (!(task instanceof ITaskItemInput)) return Collections.emptyList();
        return ((ITaskItemInput) task).getItemInputs();
    }

    private static List<IReward> getRewards(IQuest quest) {
        return quest.getRewards()
            .getEntries()
            .stream()
            .map(DBEntry::getValue)
            .collect(Collectors.toList());
    }

    private static List<BigItemStack> getRewardItemOutputs(List<IReward> rewards) {
        List<BigItemStack> ret = new ArrayList<>();
        for (IReward reward : rewards) {
            ret.addAll(getRewardItemOutputs(reward));
        }
        return ret;
    }

    private static List<BigItemStack> getRewardItemOutputs(IReward reward) {
        if (!(reward instanceof IRewardItemOutput)) return Collections.emptyList();
        return ((IRewardItemOutput) reward).getItemOutputs();
    }

    private static boolean matchStack(ItemStack compared, BigItemStack bigStackCompareTo) {
        for (ItemStack compareTo : extractStacks(bigStackCompareTo)) {
            if (NEIServerUtils.areStacksSameTypeCraftingWithNBT(compared, compareTo)) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> extractStacks(BigItemStack bigStack) {
        if (bigStack.hasOreDict()) {
            List<ItemStack> ret = Arrays.asList(
                bigStack.getOreIngredient()
                    .getMatchingStacks());
            ret.forEach(s -> s.stackSize = bigStack.stackSize);
            return ret;
        } else {
            return Collections.singletonList(translateBigStack(bigStack));
        }
    }

    private static ItemStack translateBigStack(BigItemStack bigStack) {
        ItemStack stack = bigStack.getBaseStack();
        stack.stackSize = bigStack.stackSize;
        return stack;
    }

    private boolean isMouseOverTitle(int recipeIndex) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiRecipe)) return false;
        GuiRecipe<?> gui = (GuiRecipe<?>) Minecraft.getMinecraft().currentScreen;

        CachedQuestRecipe recipe = (CachedQuestRecipe) this.arecipes.get(recipeIndex);
        String questTitle = UNDERLINE + recipe.questName;
        // noinspection unchecked
        List<String> titleArray = GuiDraw.fontRenderer.listFormattedStringToWidth(questTitle, GUI_WIDTH);
        int titleWidth = titleArray.stream()
            .map(GuiDraw::getStringWidth)
            .max(Comparator.naturalOrder())
            .orElse(0);
        int titleHeight = GuiDraw.fontRenderer.FONT_HEIGHT + (titleArray.size() - 1) * LINE_SPACE;

        Point offset = gui.getRecipePosition(recipeIndex);
        Point pos = GuiDraw.getMousePosition();
        Point relMousePos = new Point(pos.x - gui.guiLeft - offset.x, pos.y - gui.guiTop - offset.y);
        // noinspection PointlessArithmeticExpression
        Rectangle titleArea = new Rectangle(
            GUI_WIDTH / 2 - titleWidth / 2 - 1,
            16 - (titleArray.size() - 1) * LINE_SPACE,
            titleWidth + 1 * 2,
            titleHeight + 1);
        return titleArea.contains(relMousePos);
    }

    private void setTextColors() {
        textColor = QuestTranslation.getColor("bq_standard.gui.neiQuestNameColor");
        textColorHovered = QuestTranslation.getColor("bq_standard.gui.neiQuestNameHoveredColor");
    }

    private class CachedQuestRecipe extends CachedRecipe {

        private final List<PositionedStack> inputs = new ArrayList<>();
        private final List<PositionedStack> outputs = new ArrayList<>();
        private final String questName;
        private final UUID questID;

        private CachedQuestRecipe(Map.Entry<UUID, IQuest> entry) {
            this.questName = QuestTranslation.translateQuestName(entry);
            this.questID = entry.getKey();

            loadTasks(entry.getValue());
            loadRewards(entry.getValue());
        }

        private void loadTasks(IQuest quest) {
            int xOffset = 3, yOffset = 29;
            int index = 0;
            for (ITask task : getTasks(quest)) {
                for (BigItemStack stack : getTaskItemInputs(task)) {
                    if (index >= GRID_COUNT) break;
                    int x = xOffset + (index % GRID_X_COUNT) * SLOT_SIZE;
                    int y = yOffset + (index / GRID_Y_COUNT) * SLOT_SIZE;
                    if (task instanceof TaskOptionalRetrieval) {
                        inputs.add(
                            new CustomPositionedStack(
                                extractStacks(stack),
                                x,
                                y,
                                DARK_GRAY.toString() + ITALIC
                                    + QuestTranslation.translate("bq_standard.task.optional_retrieval")));
                    } else {
                        inputs.add(new PositionedStack(extractStacks(stack), x, y));
                    }
                    index++;
                }
            }
        }

        private void loadRewards(IQuest quest) {
            int xOffset = 93, yOffset = 29;
            int index = 0;
            for (IReward reward : getRewards(quest)) {
                for (BigItemStack stack : getRewardItemOutputs(reward)) {
                    if (index >= GRID_COUNT) break;
                    int x = xOffset + (index % GRID_X_COUNT) * SLOT_SIZE;
                    int y = yOffset + (index / GRID_Y_COUNT) * SLOT_SIZE;
                    if (reward instanceof RewardChoice) {
                        inputs.add(
                            new CustomPositionedStack(
                                extractStacks(stack),
                                x,
                                y,
                                DARK_GRAY.toString() + ITALIC
                                    + QuestTranslation.translate("bq_standard.reward.choice")));
                    } else {
                        inputs.add(new PositionedStack(extractStacks(stack), x, y));
                    }
                    index++;
                }
            }
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return getCycledIngredients(cycleticks / 20, inputs);
        }

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return outputs;
        }
    }
}
