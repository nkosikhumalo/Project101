package za.co.smartpantry;

import android.os.Bundle;
import android.widget.EditText;

import org.json.JSONObject;

public class IngredientFormActivity extends BaseActivity {
    private EditText nameField;
    private EditText quantityField;
    private EditText unitField;
    private EditText expiryField;
    private long ingredientId;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ingredientId = getIntent().getLongExtra("id", 0);
        startScreen(ingredientId == 0 ? "Add Ingredient" : "Edit Ingredient");

        nameField = input("Ingredient name", getIntent().getStringExtra("name"));
        String quantity = ingredientId == 0
                ? ""
                : String.valueOf(getIntent().getDoubleExtra("quantity", 0));
        quantityField = input("Quantity (must be greater than zero)", quantity);
        unitField = input("Unit (g, ml, piece...)", getIntent().getStringExtra("unit"));
        expiryField = input("Expiry date (optional, YYYY-MM-DD)",
                getIntent().getStringExtra("expiry"));

        button("Save ingredient", this::saveIngredient);
    }

    private EditText input(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        if (value != null) {
            field.setText(value);
        }
        content.addView(field);
        return field;
    }

    private void saveIngredient() {
        String name = nameField.getText().toString().trim();
        String quantityText = quantityField.getText().toString().trim();
        String unit = unitField.getText().toString().trim();
        String expiry = expiryField.getText().toString().trim();

        if (name.isEmpty() || quantityText.isEmpty() || unit.isEmpty()) {
            message("Name, quantity and unit are required.");
            return;
        }

        double quantity;
        try {
            quantity = Double.parseDouble(quantityText);
            if (quantity <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            message("Enter a quantity greater than zero.");
            return;
        }

        if (!expiry.isEmpty() && !expiry.matches("\\d{4}-\\d{2}-\\d{2}")) {
            message("Expiry date must use YYYY-MM-DD.");
            return;
        }

        JSONObject body = Api.json(
                "name", name,
                "quantity", quantity,
                "unit", unit,
                "expiry_date", expiry.isEmpty() ? JSONObject.NULL : expiry);
        String method = ingredientId == 0 ? "POST" : "PUT";
        String path = ingredientId == 0 ? "/pantry" : "/pantry/" + ingredientId;

        Api.request(method, path, body, (data, error) -> {
            if (error != null) {
                message("Could not save ingredient: " + error);
            } else {
                finish();
            }
        });
    }
}
