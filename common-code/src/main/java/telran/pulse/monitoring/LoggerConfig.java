package telran.pulse.monitoring;

import java.util.logging.*;
import static telran.pulse.monitoring.Constants.*;

public class LoggerConfig {
    private static Logger logger;

    static {
        logger = Logger.getLogger("telran.pulse.monitoring");
        loggerSetUp();
    }

    private static void loggerSetUp() {
        Level loggerLevel = getLoggerLevel();
        LogManager.getLogManager().reset();
        Handler handler = new ConsoleHandler();
        handler.setLevel(Level.FINEST);
        logger.setLevel(loggerLevel);
        logger.addHandler(handler);
    }

    private static Level getLoggerLevel() {
        String levelStr = System.getenv()
                .getOrDefault(LOGGER_LEVEL_ENV_VARIABLE, DEFAULT_LOGGER_LEVEL);
        Level res;
        try {
            res = Level.parse(levelStr);
        } catch (Exception e) {
            res = Level.parse(DEFAULT_LOGGER_LEVEL);
        }
        return res;
    }

    public static Logger getLogger() {
        return logger;
    }
}
