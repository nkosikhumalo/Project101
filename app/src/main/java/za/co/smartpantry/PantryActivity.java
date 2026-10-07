package za.co.smartpantry;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class PantryActivity extends BaseActivity {
    private final ArrayList<Ingredient> items = new ArrayList<>();
    private PantryAdapter adapter;
    private ListView list;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        startScreen("My Pantry");
        content.addView(text(
                "Keep track of what you have at home. Tap an ingredient to edit or delete it.",
                16,
                false));
        button("＋  Add ingredient", () ->
                startActivity(new Intent(this, IngredientFormActivity.class)));

        TextView empty = text("Loading your pantry…", 16, false);
        content.addView(empty);
        list = new ListView(this);
        adapter = new PantryAdapter(this, items);
        list.setEmptyView(empty);
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
                emptyMessage("Pantry unavailable. Check the server connection and return to retry.");
                message("Could not connect to the pantry server. Make sure ./run.sh is running.\n\n"
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
            if (items.isEmpty()) {
                emptyMessage("Your pantry is empty. Add an ingredient to get started.");
            }
        });
    }

    private void emptyMessage(String value) {
        if (list != null && list.getEmptyView() instanceof android.widget.TextView) {
            ((android.widget.TextView) list.getEmptyView()).setText(value);
        }
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
