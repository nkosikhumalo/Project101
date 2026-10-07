package za.co.smartpantry;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.json.JSONObject;

import java.util.ArrayList;

public class RecipeCollectionActivity extends BaseActivity {
    private final ArrayList<Recipe> recipes = new ArrayList<>();
    private RecipeAdapter adapter;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        startScreen("Recipe Collection");
        content.addView(text(
                "Browse all recipes, or see which ones you can make with your pantry.",
                16,
                false));
        button("Suggested Recipes", () ->
                startActivity(new Intent(this, SuggestionsActivity.class)));

        ListView list = new ListView(this);
        adapter = new RecipeAdapter(this, recipes);
        adapter.setEmptyMessage("Loading recipes…");
        list.setAdapter(adapter);
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < recipes.size()) {
                Intent intent = new Intent(this, RecipeDetailActivity.class);
                intent.putExtra("id", recipes.get(position).id);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            loadRecipes();
        }
    }

    private void loadRecipes() {
        Api.request("GET", "/recipes", null, (data, error) -> {
            recipes.clear();
            if (error != null) {
                adapter.setEmptyMessage("Could not load recipes. Check your server connection.");
                return;
            }

            for (int i = 0; i < data.length(); i++) {
                JSONObject value = data.optJSONObject(i);
                if (value == null) {
                    continue;
                }
                try {
                    recipes.add(new Recipe(value));
                } catch (Exception ignored) {
                    // Ignore malformed rows and keep the rest of the collection available.
                }
            }

            adapter.setEmptyMessage(recipes.isEmpty()
                    ? "No recipes are available yet. Seed the database with backend/seed.sql."
                    : null);
            adapter.notifyDataSetChanged();
        });
    }
}
