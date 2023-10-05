package bq_standard.integration.nei;

import codechicken.nei.PositionedStack;

import java.util.Collections;
import java.util.List;

public class CustomPositionedStack extends PositionedStack {

    private final List<String> tooltips;

    public CustomPositionedStack(Object object, int x, int y, List<String> tooltips) {
        super(object, x, y);
        this.tooltips = tooltips;
    }

    public CustomPositionedStack(Object object, int x, int y, String tooltip) {
        this(object, x, y, Collections.singletonList(tooltip));
    }

    public List<String> getTooltips() {
        return tooltips;
    }
}
