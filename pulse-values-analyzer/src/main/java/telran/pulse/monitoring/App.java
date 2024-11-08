package telran.pulse.monitoring;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.logging.*;

import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import software.amazon.awssdk.services.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest.Builder;
import static telran.pulse.monitoring.Constants.*;

public class App {
	record Range(int min, int max) {
	}

	static DynamoDbClient client = DynamoDbClient.builder().build();
	static Builder request;
	static Logger logger = Logger.getLogger("pulse-value-analyzer");
	static String rangeProviderUrl = System.getenv("RANGE_PROVIDER_URL");
	static {
		loggerSetUp();

	}

	public void handleRequest(DynamodbEvent event, Context context) {
		request = PutItemRequest.builder().tableName(ABNORMAL_VALUES_TABLE_NAME);
		event.getRecords().forEach(r -> {
			Map<String, AttributeValue> map = r.getDynamodb().getNewImage();
			if (map == null) {
				logger.warning("No new image found");
			} else if (r.getEventName().equals("INSERT")) {
				processPulseValue(map);
			} else {
				logger.warning(String.format("The event isn't INSERT but %s", r.getEventName()));
			}

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

	private void processPulseValue(Map<String, AttributeValue> map) {
		int value = Integer.parseInt(map.get(VALUE_ATTRIBUTE).getN());
		int patientId = Integer.parseInt(map.get(PATIENT_ID_ATTRIBUTE).getN());

		logger.finer(getLogMessage(map));
		Range range = getRange(patientId);
		if (range != null && value > range.max || value < range.min) {
			processAbnormalPulseValue(map);
		}
	}

	private Range getRange(int patientId) {
		HttpClient client = HttpClient.newHttpClient();
		String url = String.format("%s?patientId=%d", rangeProviderUrl, patientId);
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				JSONObject json = new JSONObject(response.body());
				return new Range(json.getInt("min"), json.getInt("max"));
			} else {
				throw new RuntimeException("Failed to fetch range: " + response.body());
			}
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException("Error fetching range", e);
		}

	}

	private void processAbnormalPulseValue(Map<String, AttributeValue> map) {
		logger.info(getLogMessage(map));
		client.putItem(request.item(getPutItemMap(map)).build());
	}

	private Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> getPutItemMap(
			Map<String, AttributeValue> map) {
		Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> res = new HashMap<>();
		res.put(PATIENT_ID_ATTRIBUTE, software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
				.n(map.get(PATIENT_ID_ATTRIBUTE).getN()).build());
		res.put(TIMESTAMP_ATTRIBUTE, software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
				.n(map.get(TIMESTAMP_ATTRIBUTE).getN()).build());
		res.put(VALUE_ATTRIBUTE, software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
				.n(map.get(VALUE_ATTRIBUTE).getN()).build());
		return res;
	}

	private String getLogMessage(Map<String, AttributeValue> map) {
		return String.format("patientId: %s, value: %s", map.get(PATIENT_ID_ATTRIBUTE).getN(),
				map.get(VALUE_ATTRIBUTE).getN());
	}
}
