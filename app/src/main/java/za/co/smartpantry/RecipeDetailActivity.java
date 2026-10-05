package za.co.smartpantry;

import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

public class RecipeDetailActivity extends BaseActivity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        startScreen("Recipe");

        long recipeId = getIntent().getLongExtra("id", 0);
        Api.request("GET", "/recipes/" + recipeId, null, (data, error) -> {
            if (error != null || data.length() == 0) {
                message(error == null ? "Recipe not found" : error);
                return;
            }

            try {
                showRecipe(new Recipe(data.getJSONObject(0)));
            } catch (Exception exception) {
                message("Could not read recipe details.");
            }
        });
    }

    private void showRecipe(Recipe recipe) {
        content.addView(text(recipe.name, 25, true));
        content.addView(text("Ingredients", 18, true));

        JSONArray ingredients = recipe.ingredients;
        for (int i = 0; i < ingredients.length(); i++) {
            JSONObject ingredient = ingredients.optJSONObject(i);
            if (ingredient == null) {
                continue;
            }
            String line = "•  " + ingredient.optString("name")
                    + " — " + ingredient.optDouble("quantity")
                    + " " + ingredient.optString("unit");
            content.addView(text(line, 16, false));
        }

        content.addView(text("Method", 18, true));
        content.addView(text(recipe.steps, 16, false));
    }
}
