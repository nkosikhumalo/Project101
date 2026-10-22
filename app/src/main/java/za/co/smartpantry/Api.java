package za.co.smartpantry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local, on-device data store. The callback interface keeps database work off the UI thread. */
public final class Api {
    public interface Done {
        void call(JSONArray data, String error);
    }

    private static final ExecutorService REQUESTS = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());
    private static PantryDatabase database;

    private Api() {
    }

    public static synchronized void initialize(Context context) {
        if (database == null) {
            database = new PantryDatabase(context.getApplicationContext());
        }
    }

    public static void request(String method, String path, JSONObject body, Done done) {
        REQUESTS.execute(() -> {
            JSONArray result = null;
            String error = null;
            try {
                if (database == null) {
                    throw new IllegalStateException("Local pantry database is not initialized");
                }
                result = execute(database.getWritableDatabase(), method, path, body);
            } catch (Exception exception) {
                error = exception.getMessage() == null
                        ? "Unable to access the local pantry database"
                        : exception.getMessage();
            }

            JSONArray responseData = result;
            String responseError = error;
            MAIN_THREAD.post(() -> done.call(responseData, responseError));
        });
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

    private static JSONArray execute(
            SQLiteDatabase db, String method, String path, JSONObject body) throws Exception {
        if (path.equals("/pantry")) {
            if (method.equals("GET")) {
                return readPantry(db);
            }
            if (method.equals("POST")) {
                long id = db.insertOrThrow("pantry_items", null, pantryValues(body));
                return one(readPantryItem(db, id));
            }
        }

        if (path.startsWith("/pantry/")) {
            long id = Long.parseLong(path.substring("/pantry/".length()));
            if (method.equals("GET")) {
                return one(readPantryItem(db, id));
            }
            if (method.equals("PUT")) {
                if (db.update("pantry_items", pantryValues(body), "id=?",
                        new String[]{String.valueOf(id)}) == 0) {
                    throw new IllegalArgumentException("Ingredient not found");
                }
                return one(readPantryItem(db, id));
            }
            if (method.equals("DELETE")) {
                db.delete("pantry_items", "id=?", new String[]{String.valueOf(id)});
                return new JSONArray();
            }
        }

        if (path.equals("/recipes")) {
            return readRecipes(db, null);
        }
        if (path.equals("/recipes/suggested")) {
            return suggested(db);
        }
        if (path.startsWith("/recipes/")) {
            return readRecipes(db, Long.parseLong(path.substring("/recipes/".length())));
        }

        if (path.equals("/settings")) {
            if (method.equals("GET")) {
                try (Cursor cursor = db.rawQuery(
                        "SELECT expiry_alerts FROM app_settings WHERE id=1", null)) {
                    boolean enabled = !cursor.moveToFirst() || cursor.getInt(0) != 0;
                    return one(json("expiry_alerts", enabled));
                }
            }
            if (method.equals("PUT")) {
                boolean enabled = body.getBoolean("expiry_alerts");
                ContentValues values = new ContentValues();
                values.put("expiry_alerts", enabled ? 1 : 0);
                db.update("app_settings", values, "id=1", null);
                return one(json("expiry_alerts", enabled));
            }
        }

        throw new IllegalArgumentException("Unknown local data request: " + method + " " + path);
    }

    private static ContentValues pantryValues(JSONObject body) {
        String name = body.optString("name", "").trim();
        String unit = body.optString("unit", "").trim();
        double quantity = body.optDouble("quantity", 0);
        if (name.isEmpty() || unit.isEmpty() || !Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException("Name, positive quantity and unit are required.");
        }

        String expiry = body.optString("expiry_date", "");
        if (!expiry.isEmpty()) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            dateFormat.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            if (!expiry.matches("\\d{4}-\\d{2}-\\d{2}")
                    || dateFormat.parse(expiry, position) == null
                    || position.getIndex() != expiry.length()) {
                throw new IllegalArgumentException("Enter a valid expiry date (YYYY-MM-DD).");
            }
        }

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("quantity", quantity);
        values.put("unit", unit);
        if (expiry.isEmpty() || JSONObject.NULL.equals(body.opt("expiry_date"))) {
            values.putNull("expiry_date");
        } else {
            values.put("expiry_date", expiry);
        }
        return values;
    }

    private static JSONArray readPantry(SQLiteDatabase db) throws JSONException {
        JSONArray items = new JSONArray();
        try (Cursor cursor = db.rawQuery(
                "SELECT id,name,quantity,unit,expiry_date FROM pantry_items ORDER BY id", null)) {
            while (cursor.moveToNext()) {
                items.put(pantryJson(cursor));
            }
        }
        return items;
    }

    private static JSONObject readPantryItem(SQLiteDatabase db, long id) throws Exception {
        try (Cursor cursor = db.rawQuery(
                "SELECT id,name,quantity,unit,expiry_date FROM pantry_items WHERE id=?",
                new String[]{String.valueOf(id)})) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("Ingredient not found");
            }
            return pantryJson(cursor);
        }
    }

    private static JSONObject pantryJson(Cursor cursor) throws JSONException {
        JSONObject item = json("id", cursor.getLong(0), "name", cursor.getString(1),
                "quantity", cursor.getDouble(2), "unit", cursor.getString(3));
        if (cursor.isNull(4)) {
            item.put("expiry_date", JSONObject.NULL);
        } else {
            item.put("expiry_date", cursor.getString(4));
        }
        return item;
    }

    private static JSONArray readRecipes(SQLiteDatabase db, Long id) throws JSONException {
        JSONArray recipes = new JSONArray();
        String sql = "SELECT id,name,steps,ingredients FROM recipes"
                + (id == null ? " ORDER BY name" : " WHERE id=?");
        try (Cursor cursor = db.rawQuery(sql,
                id == null ? null : new String[]{String.valueOf(id)})) {
            while (cursor.moveToNext()) {
                recipes.put(new JSONObject()
                        .put("id", cursor.getLong(0))
                        .put("name", cursor.getString(1))
                        .put("steps", cursor.getString(2))
                        .put("ingredients", new JSONArray(cursor.getString(3))));
            }
        }
        return recipes;
    }

    private static JSONArray suggested(SQLiteDatabase db) throws Exception {
        JSONArray pantry = new JSONArray();
        try (Cursor cursor = db.rawQuery("SELECT name,quantity,unit FROM pantry_items", null)) {
            while (cursor.moveToNext()) {
                pantry.put(json("name", cursor.getString(0), "quantity", cursor.getDouble(1),
                        "unit", cursor.getString(2)));
            }
        }

        JSONArray recipes = readRecipes(db, null);
        JSONArray matches = new JSONArray();
        for (int i = 0; i < recipes.length(); i++) {
            JSONObject recipe = recipes.getJSONObject(i);
            if (hasAllIngredients(recipe, pantry)) {
                matches.put(recipe);
            }
        }
        return matches;
    }

    private static boolean hasAllIngredients(JSONObject recipe, JSONArray pantry)
            throws JSONException {
        JSONArray requirements = recipe.getJSONArray("ingredients");
        for (int i = 0; i < requirements.length(); i++) {
            JSONObject required = requirements.getJSONObject(i);
            String requiredName = canonical(required.getString("name"));
            Measure requiredMeasure = measure(
                    required.getDouble("quantity"), required.getString("unit"));
            double available = 0;
            for (int j = 0; j < pantry.length(); j++) {
                JSONObject item = pantry.getJSONObject(j);
                if (!canonical(item.getString("name")).equals(requiredName)) {
                    continue;
                }
                Measure stock = measure(item.getDouble("quantity"), item.getString("unit"));
                if (stock.dimension.equals(requiredMeasure.dimension)) {
                    available += stock.amount;
                }
            }
            if (available + 1e-9 < requiredMeasure.amount) {
                return false;
            }
        }
        return true;
    }

    private static String canonical(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "");
        if (normalized.endsWith("ies") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 3) + "y";
        }
        if (normalized.endsWith("oes") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("s") && !normalized.endsWith("ss")
                && normalized.length() > 2) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static Measure measure(double quantity, String unit) {
        String normalized = unit.toLowerCase(Locale.ROOT).trim();
        switch (normalized) {
            case "kg": return new Measure(quantity * 1000, "mass");
            case "g":
            case "gram":
            case "grams": return new Measure(quantity, "mass");
            case "l":
            case "litre":
            case "liter":
            case "litres":
            case "liters": return new Measure(quantity * 1000, "volume");
            case "ml":
            case "millilitre":
            case "milliliter": return new Measure(quantity, "volume");
            case "piece":
            case "pieces":
            case "pc":
            case "pcs": return new Measure(quantity, "piece");
            case "slice":
            case "slices": return new Measure(quantity, "slice");
            case "clove":
            case "cloves": return new Measure(quantity, "clove");
            default: return new Measure(quantity, "unit:" + normalized);
        }
    }

    private static JSONArray one(JSONObject object) {
        JSONArray result = new JSONArray();
        if (object != null) {
            result.put(object);
        }
        return result;
    }

    private static final class Measure {
        final double amount;
        final String dimension;

        Measure(double amount, String dimension) {
            this.amount = amount;
            this.dimension = dimension;
        }
    }

    private static final class PantryDatabase extends SQLiteOpenHelper {
        private final Context context;

        PantryDatabase(Context context) {
            super(context, "smart_pantry.db", null, 1);
            this.context = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE pantry_items ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL, quantity REAL NOT NULL CHECK(quantity > 0),"
                    + "unit TEXT NOT NULL, expiry_date TEXT)");
            db.execSQL("CREATE TABLE recipes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE,"
                    + "steps TEXT NOT NULL, ingredients TEXT NOT NULL)");
            db.execSQL("CREATE TABLE app_settings ("
                    + "id INTEGER PRIMARY KEY CHECK(id=1), expiry_alerts INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("INSERT INTO app_settings(id,expiry_alerts) VALUES(1,1)");
            seedRecipes(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Version one is the first on-device schema; future versions should migrate in place.
        }

        private void seedRecipes(SQLiteDatabase db) {
            try (InputStream input = context.getAssets().open("recipes.json")) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                JSONArray recipes = new JSONArray(
                        new String(output.toByteArray(), StandardCharsets.UTF_8));
                for (int i = 0; i < recipes.length(); i++) {
                    JSONObject recipe = recipes.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("name", recipe.getString("name"));
                    values.put("steps", recipe.getString("steps"));
                    values.put("ingredients", recipe.getJSONArray("ingredients").toString());
                    db.insertOrThrow("recipes", null, values);
                }
            } catch (IOException | JSONException exception) {
                throw new IllegalStateException("Could not load starter recipes", exception);
            }
        }
    }
}
