package listeners;

import browsers.ConfigReader;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRY_COUNT = resolveMaxRetryCount();
    private static final Map<String, Integer> RETRY_TRACKER = new ConcurrentHashMap<>();

    @Override
    public boolean retry(ITestResult result) {
        String key = buildKey(result);
        int currentRetryCount = RETRY_TRACKER.getOrDefault(key, 0);
        if (currentRetryCount < MAX_RETRY_COUNT) {
            RETRY_TRACKER.put(key, currentRetryCount + 1);
            return true;
        }
        return false;
    }

    private static int resolveMaxRetryCount() {
        String configured = ConfigReader.getInstance().getOptionalProperty("retry.count");
        if (configured == null || configured.isBlank()) {
            return 1;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            return Math.max(value, 0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("retry.count must be a number but found: " + configured, e);
        }
    }

    private String buildKey(ITestResult result) {
        String methodName = result.getMethod().getMethodName();
        Object[] params = result.getParameters();
        StringBuilder keyBuilder = new StringBuilder(methodName);
        if (params != null) {
            for (Object param : params) {
                keyBuilder.append("|").append(String.valueOf(param));
            }
        }
        return keyBuilder.toString();
    }
}
