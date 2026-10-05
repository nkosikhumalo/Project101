package za.co.smartpantry;

import android.os.Bundle;
import android.widget.Switch;

public class SettingsActivity extends BaseActivity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        startScreen("Settings");
        content.addView(text("Preferences", 22, true));

        Switch expiryAlerts = new Switch(this);
        expiryAlerts.setText("Show expiring soon alerts");
        expiryAlerts.setTextColor(ink);
        content.addView(expiryAlerts);

        Api.request("GET", "/settings", null, (data, error) -> {
            if (error == null && data.length() > 0) {
                expiryAlerts.setChecked(data.optJSONObject(0)
                        .optBoolean("expiry_alerts", true));
            }
        });

        expiryAlerts.setOnCheckedChangeListener((button, enabled) ->
                Api.request("PUT", "/settings",
                        Api.json("expiry_alerts", enabled),
                        (data, error) -> {
                            if (error != null) {
                                message("Could not save setting: " + error);
                            }
                        }));

        content.addView(text(
                "Pantry matching compares normalized ingredient names, including common "
                        + "singular and plural forms, and converts compatible metric units "
                        + "before checking each recipe's required quantity.",
                15,
                false));
        content.addView(text("Smart Pantry Manager · Mobile App Development 700", 13, false));
    }
}
