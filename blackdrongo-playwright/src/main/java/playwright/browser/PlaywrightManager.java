package playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PlaywrightManager {

    private static final PlaywrightManager INSTANCE = new PlaywrightManager();
    private final ThreadLocal<BrowserContext> threadContext = new ThreadLocal<>();
    private final ThreadLocal<Page> threadPage = new ThreadLocal<>();
    private final Set<BrowserContext> activeContexts = ConcurrentHashMap.newKeySet();
    private Playwright playwright;
    private Browser browser;
    private boolean started;

    private PlaywrightManager() {
    }

    public static PlaywrightManager getInstance() {
        return INSTANCE;
    }

    public void start() {
        ensureEngineStarted();
        ensureThreadSession();
    }

    public void openBaseUrl() {
        start();
        String url = ConfigReader.getInstance().getOptionalProperty("url");
        if (!url.isBlank()) {
            getPage().navigate(url);
        }
    }

    public Page getPage() {
        start();
        return threadPage.get();
    }

    public BrowserContext getContext() {
        start();
        return threadContext.get();
    }

    public void closeThreadSession() {
        BrowserContext context = threadContext.get();
        try {
            if (context != null) {
                context.close();
                activeContexts.remove(context);
            }
        } finally {
            threadPage.remove();
            threadContext.remove();
        }
    }

    public synchronized void stop() {
        closeThreadSession();
        for (BrowserContext context : Set.copyOf(activeContexts)) {
            try {
                context.close();
            } catch (Exception ignored) {
                // best effort close
            } finally {
                activeContexts.remove(context);
            }
        }
        try {
            if (browser != null) {
                browser.close();
            }
        } finally {
            browser = null;
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } finally {
            playwright = null;
        }
        started = false;
    }

    private synchronized void ensureEngineStarted() {
        if (started) {
            return;
        }
        ConfigReader config = ConfigReader.getInstance();
        String browserName = config.getOptionalProperty("browser");
        if (browserName.isBlank()) {
            browserName = "chromium";
        }
        boolean headless = config.getBoolean("headless", false);

        playwright = Playwright.create();
        BrowserType browserType = resolveBrowserType(playwright, browserName);
        browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(headless));
        started = true;
    }

    private void ensureThreadSession() {
        BrowserContext context = threadContext.get();
        Page page = threadPage.get();
        if (context != null && page != null) {
            return;
        }
        BrowserContext newContext = browser.newContext();
        Page newPage = newContext.newPage();
        threadContext.set(newContext);
        threadPage.set(newPage);
        activeContexts.add(newContext);
    }

    private BrowserType resolveBrowserType(Playwright playwright, String browserName) {
        String value = browserName == null ? "chromium" : browserName.trim().toLowerCase();
        return switch (value) {
            case "chrome", "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit", "safari" -> playwright.webkit();
            default -> throw new IllegalArgumentException("Unsupported Playwright browser: " + browserName);
        };
    }
}
