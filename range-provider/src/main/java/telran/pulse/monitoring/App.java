package telran.pulse.monitoring;

import java.util.*;
import java.util.logging.Logger;

import org.json.JSONObject;
import com.amazonaws.services.lambda.runtime.*;
import com.amazonaws.services.lambda.runtime.events.*;
import static telran.pulse.monitoring.Constant.*;

record Range(int min, int max) {

}

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Map<String, Range> ranges = loadRangesFromEnv();
    private static final Logger logger = Logger.getLogger(App.class.getName());
    
    private static Map<String, Range> loadRangesFromEnv() {
        Map<String, Range> ranges = new HashMap<>();
        ranges.put(PATIENT_ID_1, new Range(
                parseEnvVar(ENV_VAR_RANGE_1_MIN, DEFAULT_RANGE_1_MIN),
                parseEnvVar(ENV_VAR_RANGE_1_MAX, DEFAULT_RANGE_1_MAX)));
        ranges.put(PATIENT_ID_2, new Range(
                parseEnvVar(ENV_VAR_RANGE_2_MIN, DEFAULT_RANGE_2_MIN),
                parseEnvVar(ENV_VAR_RANGE_2_MAX, DEFAULT_RANGE_2_MAX)));
        ranges.put(PATIENT_ID_3, new Range(
                parseEnvVar(ENV_VAR_RANGE_3_MIN, DEFAULT_RANGE_3_MIN),
                parseEnvVar(ENV_VAR_RANGE_3_MAX, DEFAULT_RANGE_3_MAX)));
        ranges.put(PATIENT_ID_4, new Range(
                parseEnvVar(ENV_VAR_RANGE_4_MIN, DEFAULT_RANGE_4_MIN),
                parseEnvVar(ENV_VAR_RANGE_4_MAX, DEFAULT_RANGE_4_MAX)));
        ranges.put(PATIENT_ID_5, new Range(
                parseEnvVar(ENV_VAR_RANGE_5_MIN, DEFAULT_RANGE_5_MIN),
                parseEnvVar(ENV_VAR_RANGE_5_MAX, DEFAULT_RANGE_5_MAX)));
        return ranges;
    }

    private static int parseEnvVar(String envVarName, int defaultValue) {
        int result = defaultValue;
        try {
            String envVar = System.getenv(envVarName);
            if (envVar != null) {
                result = Integer.parseInt(envVar);
            }
        } catch (NumberFormatException e) {
            logger.warning("Invalid number format for environment variable " + envVarName + ". Using default: " + defaultValue);
        }
        return result;
    }
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        Map<String, String> mapParameters = input.getQueryStringParameters();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        try {
            if (!mapParameters.containsKey("patientId")) {
                throw new IllegalArgumentException("no patientId parameter");
            }
            String patientIdStr = mapParameters.get("patientId");
            Range range = ranges.get(patientIdStr);
            if (range == null) {
                throw new IllegalStateException(patientIdStr + " not found in ranges");
            }

            response
                    .withStatusCode(200)
                    .withBody(getRangeJSON(range));
        } catch (IllegalArgumentException e) {
            String errorJSON = getErrorJSON(e.getMessage());
            response
                    .withBody(errorJSON)
                    .withStatusCode(400);
        } catch (IllegalStateException e) {
            String errorJSON = getErrorJSON(e.getMessage());
            response
                    .withBody(errorJSON)
                    .withStatusCode(404);
        }
        return response;
    }

    private String getErrorJSON(String message) {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("error", message);
        return jsonObj.toString();
    }

    private String getRangeJSON(Range range) {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("min", range.min());
        jsonObj.put("max", range.max());
        return jsonObj.toString();
    }

}
