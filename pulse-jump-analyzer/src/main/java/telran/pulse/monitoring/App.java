package telran.pulse.monitoring;

import java.util.*;
import java.util.logging.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import software.amazon.awssdk.services.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.model.*;
import static telran.pulse.monitoring.Constants.*;

public class App {
	static DynamoDbClient client = DynamoDbClient.builder().build();
	static Logger logger = Logger.getLogger("pulse-jump-analyzer");
	static float FACTOR;

	static {
		loggerSetUp();
		factorSetUp();
	};

	public void handleRequest(DynamodbEvent event, Context context) {
		event.getRecords().forEach(r -> {

			Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> map = r.getDynamodb().getNewImage();
			if (map == null) {
				logger.info("No new image found");
			} else if (r.getEventName().equals("INSERT")) {
				String patientId = map.get("patientId").getN();
				Integer currentValue = Integer.parseInt(map.get("value").getN());
				String timestamp = map.get("timestamp").getN();
				Integer lastValue = getLastValue(patientId);
				if (lastValue == null) {
					lastValue = currentValue;
				} else if (isJump(currentValue, lastValue)) {
					jumpProcessing(patientId, currentValue, lastValue, timestamp);
				}
				setLastValue(patientId, currentValue);

			} else {
				logger.info("Unhandled event type: " + r.getEventName());
			}
			logger.info(getLogMessage(map));
		});
	}

	private void setLastValue(String patientId, Integer value) {
		PutItemRequest request = PutItemRequest.builder()
            .tableName(LAST_VALUES_TABLE)
            .item(Map.of(
                PATIENT_ID_ATTRIBUTE, AttributeValue.builder().n(patientId).build(),
                VALUE_ATTRIBUTE, AttributeValue.builder().n(String.valueOf(value)).build()
            ))
            .build();
			client.putItem(request);
	}

	private Integer getLastValue(String patientId) {
		GetItemRequest request = GetItemRequest.builder()
            .tableName(LAST_VALUES_TABLE)
            .key(Map.of(PATIENT_ID_ATTRIBUTE, AttributeValue.builder().n(patientId).build()))
            .build();
			Map<String, AttributeValue> item = client.getItem(request).item();
		
			return item == null ? null : Integer.parseInt(item.get(VALUE_ATTRIBUTE).n());
	}

	private static void factorSetUp() {
		String factor = System.getenv().getOrDefault("FACTOR", "0.2");
		try {
			FACTOR = Float.parseFloat(factor);
		} catch (NumberFormatException e) {
			logger.severe("Factor value " + factor + " is wrong, set to default value " + DEFAULT_FACTOR);
			FACTOR = DEFAULT_FACTOR;
		}
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
				.getOrDefault("LOGGER_LEVEL", DEFAULT_LOGGER_LEVEL);
		Level res = null;
		try {
			res = Level.parse(levelStr);
		} catch (Exception e) {
			res = Level.parse(DEFAULT_LOGGER_LEVEL);
		}
		return res;
	}

	private void jumpProcessing(String patientId, Integer currentValue, Integer lastValue, String timestamp) {
		PutItemRequest request = getRequest(patientId, currentValue, lastValue, timestamp);
		client.putItem(request);
	}

	private PutItemRequest getRequest(String patientId, Integer currentValue, Integer lastValue, String timestamp) {
		PutItemRequest request = PutItemRequest.builder()
				.tableName(JUMP_VALUES_TABLE)
				.item(Map.of(
						PATIENT_ID_ATTRIBUTE, AttributeValue.builder().n(patientId).build(),
						PREVIOUS_VALUE_ATTRIBUTE, AttributeValue.builder().n(String.valueOf(lastValue)).build(),
						VALUE_ATTRIBUTE, AttributeValue.builder().n(String.valueOf(currentValue)).build(),
						TIMESTAMP_ATTRIBUTE, AttributeValue.builder().n(timestamp).build()))
				.build();
		return request;
	}

	private boolean isJump(Integer currentValue, Integer lastValue) {

		return (float) Math.abs(currentValue - lastValue) / lastValue > FACTOR;
	}

	private String getLogMessage(Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> map) {
		return String.format("patientId: %s, value: %s", map.get(PATIENT_ID_ATTRIBUTE).getN(),
				map.get(VALUE_ATTRIBUTE).getN());
	}
}
