package listeners;

import org.testng.IExecutionListener;

import java.util.logging.Logger;

public class TestNgExecutionListener implements IExecutionListener {

    private static final Logger LOGGER = Logger.getLogger(TestNgExecutionListener.class.getName());

    @Override
    public void onExecutionStart() {
        LOGGER.info("TestNG execution started.");
    }

    @Override
    public void onExecutionFinish() {
        LOGGER.info("TestNG execution finished.");
    }
}
