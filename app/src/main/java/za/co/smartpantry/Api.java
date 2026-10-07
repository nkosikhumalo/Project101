package za.co.smartpantry;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Api {
    // The Android emulator reaches host services through 10.0.2.2.
    public static final String BASE = "http://10.0.2.2:8080/api";

    public interface Done {
        void call(JSONArray data, String error);
    }

    private static final ExecutorService REQUESTS = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());
    private static final int MAX_ATTEMPTS = 3;

    private Api() {
    }

    public static void request(String method, String path, JSONObject body, Done done) {
        REQUESTS.execute(() -> {
            JSONArray result = null;
            String error = null;

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpURLConnection connection =
                            (HttpURLConnection) new URL(BASE + path).openConnection();
                    connection.setRequestMethod(method);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(7000);
                    connection.setRequestProperty("Accept", "application/json");

                    if (body != null) {
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/json");
                        try (OutputStream output = connection.getOutputStream()) {
                            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                        }
                    }

                    int status = connection.getResponseCode();
                    InputStream input = status < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();
                    String response = readText(input);
                    connection.disconnect();

                    if (status >= 400) {
                        throw new IOException("Server returned " + status + ": " + response);
                    }

                    result = parseResponse(response);
                    error = null;
                    break;
                } catch (Exception exception) {
                    error = exception.getMessage() == null
                            ? "Unable to connect to pantry server"
                            : exception.getMessage();
                    if (attempt == MAX_ATTEMPTS || !isTransient(exception)) {
                        break;
                    }
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        error = "Request interrupted";
                        break;
                    }
                }
            }

            JSONArray responseData = result;
            String responseError = error;
            MAIN_THREAD.post(() -> done.call(responseData, responseError));
        });
    }

    private static boolean isTransient(Exception exception) {
        return exception instanceof ConnectException
                || exception instanceof NoRouteToHostException
;
    }

    private static String readText(InputStream input) throws IOException {
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    private static JSONArray parseResponse(String response) throws JSONException {
        if (response.startsWith("[")) {
            return new JSONArray(response);
        }
        if (response.startsWith("{")) {
            JSONObject object = new JSONObject(response);
            JSONArray data = object.optJSONArray("data");
            if (data != null) {
                return data;
            }

            JSONArray wrapped = new JSONArray();
            wrapped.put(object);
            return wrapped;
        }
        return new JSONArray();
    }

    public static JSONObject json(Object... keyValues) {
        JSONObject object = new JSONObject();
        try {
            for (int i = 0; i < keyValues.length; i += 2) {
                object.put((String) keyValues[i], keyValues[i + 1]);
            }
        } catch (JSONException ignored) {
            // Keys are supplied as string constants by the calling screens.
        }
        return object;
    }
}
