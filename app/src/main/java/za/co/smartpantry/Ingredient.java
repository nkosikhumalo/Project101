package za.co.smartpantry;

import org.json.JSONObject;

public class Ingredient {
    public long id;
    public String name;
    public String unit;
    public String expiry;
    public double quantity;

    public Ingredient(JSONObject json) {
        id = json.optLong("id");
        name = json.optString("name");
        unit = json.optString("unit");
        quantity = json.optDouble("quantity");
        expiry = json.optString("expiry_date", "");
    }

    public String label() {
        String item = name + "  ·  " + quantity + " " + unit;
        return expiry.isEmpty() ? item : item + "\nExpires " + expiry;
    }
}
