package za.co.smartpantry.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.Executors;

public final class Server {
    private static final String DB_URL = env(
            "DATABASE_URL",
            "jdbc:postgresql://127.0.0.1:5432/smartpantry");
    private static final String DB_USER = env("DB_USER", "pantry_app");
    private static final String DB_PASSWORD = env("DB_PASSWORD", "pantry_local_dev");
    private static final String PORT = env("PORT", "8080");

    private Server() {
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", Integer.parseInt(PORT)),
                0);
        server.createContext("/api/", Server::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Smart Pantry Java API listening on port " + PORT);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET,POST,PUT,DELETE,OPTIONS");

        if (exchange.getRequestMethod().equals("OPTIONS")) {
            send(exchange, 204, "");
            return;
        }

        try (Connection database = DriverManager.getConnection(
                DB_URL,
                DB_USER,
                DB_PASSWORD)) {
            String path = exchange.getRequestURI()
                    .getPath()
                    .substring("/api".length());
            String method = exchange.getRequestMethod();

            if (path.equals("/pantry") || path.startsWith("/pantry/")) {
                pantry(exchange, database, path, method);
            } else if (path.equals("/recipes") || path.startsWith("/recipes/")) {
                recipes(exchange, database, path, method);
            } else if (path.equals("/settings")) {
                settings(exchange, database, method);
            } else {
                sendJson(exchange, 404, new JSONObject().put("error", "not found"));
            }
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, new JSONObject().put("error", exception.getMessage()));
        } catch (Exception exception) {
            exception.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "database or server error"));
        }
    }

    private static void pantry(
            HttpExchange exchange,
            Connection database,
            String path,
            String method) throws Exception {
        String tail = path.substring("/pantry".length());
        if (tail.isEmpty() || tail.equals("/")) {
            pantryCollection(exchange, database, method);
            return;
        }

        long id = Long.parseLong(tail.substring(1));
        if (method.equals("DELETE")) {
            deleteIngredient(exchange, database, id);
        } else if (method.equals("GET")) {
            getIngredient(exchange, database, id);
        } else if (method.equals("PUT")) {
            updateIngredient(exchange, database, id);
        } else {
            methodNotAllowed(exchange);
        }
    }

    private static void pantryCollection(
            HttpExchange exchange,
            Connection database,
            String method) throws Exception {
        if (method.equals("GET")) {
            JSONArray items = new JSONArray();
            String sql = "SELECT id,name,quantity,unit,expiry_date "
                    + "FROM pantry_items ORDER BY name";
            try (PreparedStatement statement = database.prepareStatement(sql);
                 ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    items.put(pantryJson(results));
                }
            }
            sendJson(exchange, 200, items);
            return;
        }

        if (method.equals("POST")) {
            Ingredient ingredient = ingredient(body(exchange));
            String sql = "INSERT INTO pantry_items(name,quantity,unit,expiry_date) "
                    + "VALUES(?,?,?,?)";
            try (PreparedStatement statement = database.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS)) {
                bindPantry(statement, ingredient);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        ingredient.id = keys.getLong(1);
                    }
                }
            }
            sendJson(exchange, 201, ingredient.json());
            return;
        }

        methodNotAllowed(exchange);
    }

    private static void deleteIngredient(
            HttpExchange exchange,
            Connection database,
            long id) throws Exception {
        try (PreparedStatement statement = database.prepareStatement(
                "DELETE FROM pantry_items WHERE id=?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() == 0) {
                notFound(exchange);
                return;
            }
        }
        sendJson(exchange, 200, new JSONObject().put("ok", true));
    }

    private static void getIngredient(
            HttpExchange exchange,
            Connection database,
            long id) throws Exception {
        String sql = "SELECT id,name,quantity,unit,expiry_date "
                + "FROM pantry_items WHERE id=?";
        try (PreparedStatement statement = database.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    notFound(exchange);
                    return;
                }
                sendJson(exchange, 200, pantryJson(results));
            }
        }
    }

    private static void updateIngredient(
            HttpExchange exchange,
            Connection database,
            long id) throws Exception {
        Ingredient ingredient = ingredient(body(exchange));
        String sql = "UPDATE pantry_items SET name=?,quantity=?,unit=?,expiry_date=?,"
                + "updated_at=now() WHERE id=?";
        try (PreparedStatement statement = database.prepareStatement(sql)) {
            bindPantry(statement, ingredient);
            statement.setLong(5, id);
            if (statement.executeUpdate() == 0) {
                notFound(exchange);
                return;
            }
        }
        sendJson(exchange, 200, new JSONObject().put("ok", true));
    }

    private static void recipes(
            HttpExchange exchange,
            Connection database,
            String path,
            String method) throws Exception {
        if (!method.equals("GET")) {
            methodNotAllowed(exchange);
            return;
        }

        String tail = path.substring("/recipes".length());
        if (tail.equals("/suggested")) {
            sendJson(exchange, 200, suggested(database));
            return;
        }
        if (tail.isEmpty() || tail.equals("/")) {
            sendJson(exchange, 200, readRecipes(database, null));
            return;
        }

        long id = Long.parseLong(tail.substring(1));
        JSONArray results = readRecipes(database, id);
        if (results.isEmpty()) {
            notFound(exchange);
            return;
        }
        sendJson(exchange, 200, results.getJSONObject(0));
    }

    private static JSONArray readRecipes(Connection database, Long id) throws SQLException {
        JSONArray recipes = new JSONArray();
        String sql = "SELECT id,name,steps,ingredients FROM recipes"
                + (id == null ? " ORDER BY name" : " WHERE id=?");

        try (PreparedStatement statement = database.prepareStatement(sql)) {
            if (id != null) {
                statement.setLong(1, id);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    JSONObject recipe = new JSONObject()
                            .put("id", results.getLong("id"))
                            .put("name", results.getString("name"))
                            .put("steps", results.getString("steps"))
                            .put("ingredients", new JSONArray(results.getString("ingredients")));
                    recipes.put(recipe);
                }
            }
        }
        return recipes;
    }

    private static JSONArray suggested(Connection database) throws SQLException {
        JSONArray pantry = new JSONArray();
        String sql = "SELECT name,quantity,unit FROM pantry_items";
        try (PreparedStatement statement = database.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                JSONObject item = new JSONObject()
                        .put("name", results.getString("name"))
                        .put("quantity", results.getDouble("quantity"))
                        .put("unit", results.getString("unit"));
                pantry.put(item);
            }
        }

        JSONArray recipes = readRecipes(database, null);
        JSONArray matches = new JSONArray();
        for (int i = 0; i < recipes.length(); i++) {
            JSONObject recipe = recipes.getJSONObject(i);
            if (hasAllIngredients(recipe, pantry)) {
                matches.put(recipe);
            }
        }
        return matches;
    }

    private static boolean hasAllIngredients(JSONObject recipe, JSONArray pantry) {
        JSONArray requirements = recipe.getJSONArray("ingredients");
        for (int i = 0; i < requirements.length(); i++) {
            JSONObject requirement = requirements.getJSONObject(i);
            String requiredName = canonical(requirement.getString("name"));
            Measure required = measure(
                    requirement.getDouble("quantity"),
                    requirement.getString("unit"));
            double available = 0;

            for (int j = 0; j < pantry.length(); j++) {
                JSONObject item = pantry.getJSONObject(j);
                if (!canonical(item.getString("name")).equals(requiredName)) {
                    continue;
                }

                Measure stock = measure(item.getDouble("quantity"), item.getString("unit"));
                if (stock.dimension.equals(required.dimension)) {
                    available += stock.amount;
                }
            }

            if (available + 1e-9 < required.amount) {
                return false;
            }
        }
        return true;
    }

    private static void settings(
            HttpExchange exchange,
            Connection database,
            String method) throws Exception {
        if (method.equals("GET")) {
            String sql = "SELECT expiry_alerts FROM app_settings WHERE id=1";
            try (PreparedStatement statement = database.prepareStatement(sql);
                 ResultSet results = statement.executeQuery()) {
                boolean alertsEnabled = !results.next() || results.getBoolean(1);
                sendJson(exchange, 200,
                        new JSONObject().put("expiry_alerts", alertsEnabled));
            }
            return;
        }

        if (method.equals("PUT")) {
            boolean alertsEnabled = body(exchange).getBoolean("expiry_alerts");
            String sql = "UPDATE app_settings SET expiry_alerts=? WHERE id=1";
            try (PreparedStatement statement = database.prepareStatement(sql)) {
                statement.setBoolean(1, alertsEnabled);
                statement.executeUpdate();
            }
            sendJson(exchange, 200,
                    new JSONObject().put("expiry_alerts", alertsEnabled));
            return;
        }

        methodNotAllowed(exchange);
    }

    private static JSONObject pantryJson(ResultSet results) throws SQLException {
        Date expiry = results.getDate("expiry_date");
        JSONObject item = new JSONObject()
                .put("id", results.getLong("id"))
                .put("name", results.getString("name"))
                .put("quantity", results.getDouble("quantity"))
                .put("unit", results.getString("unit"));
        item.put("expiry_date", expiry == null ? JSONObject.NULL : expiry.toString());
        return item;
    }

    private static Ingredient ingredient(JSONObject input) {
        String name = input.optString("name").trim();
        String unit = input.optString("unit").trim();
        double quantity = input.optDouble("quantity", 0);

        if (name.isEmpty() || unit.isEmpty() || !Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException(
                    "name, positive quantity and unit are required");
        }

        String expiry = input.isNull("expiry_date")
                ? null
                : input.optString("expiry_date", "");
        if (expiry != null && !expiry.isBlank()) {
            LocalDate.parse(expiry);
        }
        return new Ingredient(name, quantity, unit, expiry);
    }

    private static void bindPantry(PreparedStatement statement, Ingredient ingredient)
            throws SQLException {
        statement.setString(1, ingredient.name);
        statement.setDouble(2, ingredient.quantity);
        statement.setString(3, ingredient.unit);
        if (ingredient.expiry == null || ingredient.expiry.isBlank()) {
            statement.setNull(4, Types.DATE);
        } else {
            statement.setDate(4, Date.valueOf(ingredient.expiry));
        }
    }

    private static JSONObject body(HttpExchange exchange) throws IOException {
        String request = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        return new JSONObject(request);
    }

    private static String canonical(String name) {
        String normalized = name.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", "");
        if (normalized.endsWith("ies") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 3) + "y";
        }
        if (normalized.endsWith("oes") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("s")
                && !normalized.endsWith("ss")
                && normalized.length() > 2) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static Measure measure(double quantity, String unit) {
        String normalizedUnit = unit.toLowerCase(Locale.ROOT).trim();
        return switch (normalizedUnit) {
            case "kg" -> new Measure(quantity * 1000, "mass");
            case "g", "gram", "grams" -> new Measure(quantity, "mass");
            case "l", "litre", "liter", "litres", "liters" ->
                    new Measure(quantity * 1000, "volume");
            case "ml", "millilitre", "milliliter" ->
                    new Measure(quantity, "volume");
            case "piece", "pieces", "pc", "pcs" -> new Measure(quantity, "piece");
            case "slice", "slices" -> new Measure(quantity, "slice");
            case "clove", "cloves" -> new Measure(quantity, "clove");
            default -> new Measure(quantity, "unit:" + normalizedUnit);
        };
    }

    private static void sendJson(HttpExchange exchange, int status, Object value)
            throws IOException {
        send(exchange, status, value.toString());
    }

    private static void send(HttpExchange exchange, int status, String value)
            throws IOException {
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        sendJson(exchange, 404, new JSONObject().put("error", "not found"));
    }

    private static void methodNotAllowed(HttpExchange exchange) throws IOException {
        sendJson(exchange, 405, new JSONObject().put("error", "method not allowed"));
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class Ingredient {
        final String name;
        final double quantity;
        final String unit;
        final String expiry;
        long id;

        Ingredient(String name, double quantity, String unit, String expiry) {
            this.name = name;
            this.quantity = quantity;
            this.unit = unit;
            this.expiry = expiry;
        }

        JSONObject json() {
            JSONObject item = new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("quantity", quantity)
                    .put("unit", unit);
            item.put("expiry_date", expiry == null ? JSONObject.NULL : expiry);
            return item;
        }
    }

    private static final class Measure {
        final double amount;
        final String dimension;

        Measure(double amount, String dimension) {
            this.amount = amount;
            this.dimension = dimension;
        }
    }
}
