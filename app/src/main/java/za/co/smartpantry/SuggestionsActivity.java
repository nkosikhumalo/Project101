package za.co.smartpantry;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;


import java.util.ArrayList;

public class SuggestionsActivity extends BaseActivity {
    private final ArrayList<Recipe> recipes = new ArrayList<>();
    private RecipeAdapter adapter;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        startScreen("Suggested Recipes");
        content.addView(text(
                "Every recipe here matches all required ingredients and quantities in your pantry.",
                16,
                false));

        TextView empty = text("Loading suggestions…", 16, false);
        ListView list = new ListView(this);
        adapter = new RecipeAdapter(this, recipes);
        adapter.setEmptyView(empty);
        list.setEmptyView(empty);
        list.setAdapter(adapter);
        // Place empty view and list directly on body so ListView fills remaining space.
        body.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
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
            loadSuggestions();
        }
    }

    private void loadSuggestions() {
        adapter.setEmptyMessage("Loading suggestions…");
        Api.request("GET", "/recipes/suggested", null, (data, error) -> {
            recipes.clear();
            if (error != null) {
                adapter.setEmptyMessage(
                        "Could not load suggestions. Check your server connection.");
                adapter.notifyDataSetChanged();
                return;
            }
            for (int i = 0; i < data.length(); i++) {
                try {
                    recipes.add(new Recipe(data.getJSONObject(i)));
                } catch (Exception ignored) {
                    // Ignore malformed recipes while keeping valid suggestions visible.
                }
            }
            if (recipes.isEmpty()) {
                adapter.setEmptyMessage(
                        "No recipes match your pantry yet — add more ingredients.");
            } else {
                adapter.setEmptyMessage(null);
            }
            adapter.notifyDataSetChanged();
        });
    }
}
