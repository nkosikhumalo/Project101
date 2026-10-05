package za.co.smartpantry;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.json.JSONArray;

import java.util.ArrayList;

public class SuggestionsActivity extends BaseActivity {
    private final ArrayList<Recipe> recipes = new ArrayList<>();
    private RecipeAdapter adapter;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        startScreen("Recipes You Can Make");
        content.addView(text(
                "Every recipe here matches all required ingredients and quantities in your pantry.",
                16,
                false));

        ListView list = new ListView(this);
        adapter = new RecipeAdapter(this, recipes);
        list.setAdapter(adapter);
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < recipes.size()) {
                Intent intent = new Intent(this, RecipeDetailActivity.class);
                intent.putExtra("id", recipes.get(position).id);
                startActivity(intent);
            }
        });
        loadSuggestions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            loadSuggestions();
        }
    }

    private void loadSuggestions() {
        Api.request("GET", "/recipes/suggested", null, (data, error) -> {
            recipes.clear();
            if (error != null) {
                adapter.setEmptyMessage(
                        "Could not load suggestions. Check your server connection.");
                return;
            }
            if (data.length() == 0) {
                adapter.setEmptyMessage(
                        "No recipes match your pantry yet — add more ingredients.");
                return;
            }

            for (int i = 0; i < data.length(); i++) {
                try {
                    recipes.add(new Recipe(data.getJSONObject(i)));
                } catch (Exception ignored) {
                    // Ignore malformed recipes while keeping valid suggestions visible.
                }
            }
            adapter.setEmptyMessage(null);
            adapter.notifyDataSetChanged();
        });
    }
}
