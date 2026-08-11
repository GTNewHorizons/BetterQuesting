package betterquesting.api2.client.gui.misc;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;

import betterquesting.core.BetterQuesting;

public class URIHandlers {

    private URIHandlers() {}

    private static final Map<String, Predicate<URI>> handlers = new ConcurrentHashMap<>();
    private static final Map<String, Function<URI, List<String>>> tooltipHandlers = new ConcurrentHashMap<>();

    static {
        register("http", new HTTPHandler());
        register("https", new HTTPHandler());
    }

    public static synchronized void register(String scheme, Predicate<URI> handler) {
        if (handlers.containsKey(scheme)) throw new IllegalArgumentException("duplicate handler");
        handlers.put(scheme, handler);
    }

    public static Predicate<URI> get(String scheme) {
        return handlers.get(scheme);
    }

    public static synchronized void registerTooltip(String scheme, Function<URI, List<String>> tooltipHandler) {
        if (tooltipHandlers.containsKey(scheme)) throw new IllegalArgumentException("duplicate tooltip handler");
        tooltipHandlers.put(scheme, tooltipHandler);
    }

    public static List<String> getTooltip(URI uri) {
        Function<URI, List<String>> tooltipHandler = tooltipHandlers.get(uri.getScheme());
        return tooltipHandler != null ? tooltipHandler.apply(uri) : null;
    }

    private static class HTTPHandler implements Predicate<URI> {

        @Override
        public boolean test(URI uri) {
            if (Minecraft.getMinecraft().gameSettings.chatLinksPrompt) {
                GuiScreen oldScreen = Minecraft.getMinecraft().currentScreen;
                // must be anonymous class. lambda doesn't work with reobf
                // noinspection Convert2Lambda
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiConfirmOpenLink(new GuiYesNoCallback() {

                        @Override
                        public void confirmClicked(boolean result, int id) {
                            if (result) {
                                openURL(uri);
                            }

                            Minecraft.getMinecraft()
                                .displayGuiScreen(oldScreen);
                        }
                    }, uri.toString(), 0, false));
            } else {
                openURL(uri);
            }
            return true;
        }

        private static void openURL(URI p_146407_1_) {
            try {
                Class<?> oclass = Class.forName("java.awt.Desktop");
                Object object = oclass.getMethod("getDesktop")
                    .invoke(null);
                oclass.getMethod("browse", URI.class)
                    .invoke(object, p_146407_1_);
            } catch (Throwable throwable) {
                BetterQuesting.logger.error("Couldn't open link", throwable);
            }
        }
    }
}
