package za.co.smartpantry;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;

public class PantryActivity extends BaseActivity {
    private final ArrayList<Ingredient> items = new ArrayList<>();
    private PantryAdapter adapter;
    private ListView list;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        startScreen("My Pantry");
        content.addView(text("Keep track of what you have at home.", 16, false));
        button("＋  Add ingredient", () ->
                startActivity(new Intent(this, IngredientFormActivity.class)));

        list = new ListView(this);
        adapter = new PantryAdapter(this, items);
        list.setAdapter(adapter);
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        list.setOnItemClickListener((parent, view, position, id) ->
                showItemActions(items.get(position)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            loadPantry();
        }
    }

    private void loadPantry() {
        Api.request("GET", "/pantry", null, (data, error) -> {
            if (error != null) {
                message("Could not load pantry. Check the API server address in Api.java.\n\n"
                        + error);
                return;
            }

            items.clear();
            for (int i = 0; i < data.length(); i++) {
                try {
                    items.add(new Ingredient(data.getJSONObject(i)));
                } catch (Exception ignored) {
                    // Skip an invalid response item and keep displaying the others.
                }
            }
            adapter.refresh();
        });
    }

    private void showItemActions(Ingredient ingredient) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(ingredient.name)
                .setItems(new String[]{"Edit", "Delete"}, (dialog, choice) -> {
                    if (choice == 0) {
                        openEditor(ingredient);
                    } else {
                        deleteIngredient(ingredient);
                    }
                })
                .show();
    }

    private void openEditor(Ingredient ingredient) {
        Intent intent = new Intent(this, IngredientFormActivity.class);
        intent.putExtra("id", ingredient.id);
        intent.putExtra("name", ingredient.name);
        intent.putExtra("quantity", ingredient.quantity);
        intent.putExtra("unit", ingredient.unit);
        intent.putExtra("expiry", ingredient.expiry);
        startActivity(intent);
    }

    private void deleteIngredient(Ingredient ingredient) {
        Api.request("DELETE", "/pantry/" + ingredient.id, null, (data, error) -> {
            if (error != null) {
                message(error);
            } else {
                loadPantry();
            }
        });
    }
}
