package za.co.smartpantry;

import org.json.JSONArray;
import org.json.JSONObject;

public class Recipe {
    public long id;
    public String name;
    public String steps;
    public JSONArray ingredients;

    public Recipe(JSONObject json) {
        id = json.optLong("id");
        name = json.optString("name");
        steps = json.optString("steps");
        ingredients = json.optJSONArray("ingredients");
        if (ingredients == null) {
            ingredients = new JSONArray();
        }
    }

    public String label() {
        return name + "\n" + ingredients.length() + " ingredients";
    }
}
