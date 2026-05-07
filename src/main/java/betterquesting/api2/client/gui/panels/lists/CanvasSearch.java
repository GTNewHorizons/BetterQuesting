package betterquesting.api2.client.gui.panels.lists;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;

import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;

public abstract class CanvasSearch<T, E> extends CanvasScrolling {

    private String searchTerm = "";
    private Iterator<E> searching = null;
    private final Stopwatch searchTime = Stopwatch.createStarted();
    private int resultWidth = 256; // Used for organising ongoing search results even if the size changes midway
    private int searchIdx = 0; // Where are we in the ongoing search?
    private ArrayDeque<T> pendingResults = new ArrayDeque<>();
    private final boolean deduplicate;
    private List<IGuiPanel> batch = new LinkedList<>();

    public CanvasSearch(IGuiRect rect, boolean deduplicate) {
        super(rect);
        this.deduplicate = deduplicate;
    }

    public CanvasSearch(IGuiRect rect) {
        this(rect, false);
    }

    public void setSearchFilter(String text) {
        this.searchTerm = text.toLowerCase();
        refreshSearch();
    }

    @Override
    public void initPanel() {
        super.initPanel();
        refreshSearch();
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        updateSearch();
        updateResults();

        super.drawPanel(mx, my, partialTick);
    }

    public void refreshSearch() {
        this.resetCanvas();
        this.searchIdx = 0;
        this.searching = getIterator();
        this.resultWidth = this.getTransform()
            .getWidth();
        this.pendingResults.clear();
    }

    private void updateSearch() {
        if (searching == null) {
            return;
        } else if (!searching.hasNext()) {
            searching = null;
            return;
        }

        searchTime.reset()
            .start();
        while (searching.hasNext() && searchTime.elapsed(TimeUnit.MILLISECONDS) < 10) {
            E entry = searching.next();

            if (entry != null) {
                queryMatches(entry, searchTerm, pendingResults);
            }
        }

        searchTime.stop();

        if (deduplicate) {
            pendingResults = new ArrayDeque<>(new LinkedHashSet<>(pendingResults));
        }
    }

    private void updateResults() {
        if (pendingResults.isEmpty()) {
            return;
        }

        searchTime.reset()
            .start();

        batch.clear();
        while (!pendingResults.isEmpty() && searchTime.elapsed(TimeUnit.MILLISECONDS) < 10) {
            if (addResult(pendingResults.poll(), searchIdx, resultWidth)) searchIdx++;
        }
        searchTime.stop();

        int currentScrollY = this.getScrollY();
        addCulledPanels(batch, true);
        if (this.getScrollY() > currentScrollY) {
            this.setScrollY(currentScrollY);
            updatePanelScroll();
        }

        batch.clear();
    }

    protected void addBatchPanel(IGuiPanel panel) {
        if (panel != null) {
            batch.add(panel);
        }
    }

    protected abstract Iterator<E> getIterator();

    protected abstract void queryMatches(E value, String query, final ArrayDeque<T> results);

    protected abstract boolean addResult(T entry, int index, int cachedWidth);
}
