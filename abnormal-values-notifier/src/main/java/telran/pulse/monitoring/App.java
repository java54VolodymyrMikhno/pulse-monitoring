package telran.pulse.monitoring;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.*;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class App {
    String topicARN;
    String awsRegion;
    SnsClient snsClient;
    static Logger logger = Logger.getLogger("abnormal-values-notifier");
    static {
		loggerSetUp();
	}

    public void handleRequest(DynamodbEvent event, Context context) {
        setUpEnvironment();
        setUpSnsClient();
        event.getRecords().forEach(r -> {
            Map<String, AttributeValue> image = r.getDynamodb().getNewImage();
            String message = getMessage(image);
            logger.info("Generated message: " + message);
            publishMessage(message);
        });
    }
    private static void loggerSetUp() {
		Level loggerLevel = getLoggerLevel();
		LogManager.getLogManager().reset();
		Handler handler = new ConsoleHandler();
		logger.setLevel(loggerLevel);
		handler.setLevel(Level.FINEST);
		logger.addHandler(handler);
	}

	private static Level getLoggerLevel() {
		String levelStr = System.getenv()
				.getOrDefault(LOGGER_LEVEL_ENV_VARIABLE, DEFAULT_LOGGER_LEVEL);
		Level res = null;
		try {
			res = Level.parse(levelStr);
		} catch (Exception e) {
			res = Level.parse(DEFAULT_LOGGER_LEVEL);
		}
		return res;
	}

    private void publishMessage(String message) {
        PublishRequest request = PublishRequest.builder()
                .message(message)
                .topicArn(topicARN)
                .build();
        try {
            snsClient.publish(request);
            logger.info("Message published to SNS topic: " + topicARN);
        } catch (Exception e) {
            logger.warning("Failed to publish message to SNS: " + e.getMessage());
        }
    }
    

    private String getMessage(Map<String, AttributeValue> image) {
        String message = String.format("patient %s\nabnormal pulse value %s\ndate-time %s",
                image.get("patientId").getN(), image.get("value").getN(),
                getDateTime(image.get("timestamp").getN()));
        logger.fine("Constructed message: " + message);
        return message;
    }

    private Object getDateTime(String timestampStr) {
        long timestamp = Long.parseLong(timestampStr);
        Instant instant = Instant.ofEpochMilli(timestamp);
        LocalDateTime res = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        logger.finer("Parsed timestamp " + timestampStr + " to LocalDateTime: " + res);
        return res;
    }

    private void setUpSnsClient() {
        snsClient = SnsClient.builder().region(Region.of(awsRegion)).build();
    }

    private void setUpEnvironment() {
        Map<String, String> env = System.getenv();
        topicARN = env.get("TOPIC_ARN");
        if (topicARN == null) {
            logger.warning("Environment variable TOPIC_ARN not found");
            throw new NoSuchElementException("TOPIC_ARN not found");
        }
        awsRegion = env.getOrDefault("REGION", "us-east-1");
        logger.info("Environment set up with TOPIC_ARN: " + topicARN + " and REGION: " + awsRegion);
    }
}