package za.co.smartpantry;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class BaseActivity extends Activity {

    // Colour palette
    final int ink        = Color.rgb(18, 18, 18);
    final int subtle     = Color.rgb(100, 100, 110);
    final int background = Color.rgb(247, 247, 250);
    final int surface    = Color.WHITE;
    final int divider    = Color.rgb(230, 230, 235);
    final int accent     = Color.rgb(60, 120, 255);   // blue
    final int accentText = Color.WHITE;

    // Keep old names so existing screens compile without changes
    final int paper   = surface;
    final int gray100 = background;
    final int gray300 = divider;

    LinearLayout page;
    LinearLayout body;
    LinearLayout content;

    void startScreen(String title) {
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(background);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(page);

        // ── Title bar ────────────────────────────────────────────────────────
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setBackgroundColor(surface);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(4), dp(12), dp(20), dp(12));
        // thin bottom shadow line
        titleBar.setElevation(dp(2));
        page.addView(titleBar);

        // Back button
        Button backBtn = new Button(this);
        backBtn.setText("‹");
        backBtn.setAllCaps(false);
        backBtn.setTextSize(28);
        backBtn.setTextColor(accent);
        backBtn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        backBtn.setPadding(dp(8), 0, dp(4), 0);
        backBtn.setMinWidth(0);
        backBtn.setMinHeight(0);
        backBtn.setOnClickListener(v -> finish());
        backBtn.setVisibility(isTaskRoot() ? View.GONE : View.VISIBLE);
        titleBar.addView(backBtn, new LinearLayout.LayoutParams(-2, -2));

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextColor(ink);
        titleText.setTextSize(22);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setPadding(dp(isTaskRoot() ? 16 : 4), 0, 0, 0);
        titleBar.addView(titleText, new LinearLayout.LayoutParams(0, -2, 1));

        // ── Body (fills space between title and nav) ──────────────────────────
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView scroll = new ScrollView(this);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        content = new LinearLayout(this);
        content.setPadding(dp(16), dp(16), dp(16), dp(8));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        // ── Bottom navigation ─────────────────────────────────────────────────
        LinearLayout navigation = new LinearLayout(this);
        navigation.setBackgroundColor(surface);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        // top divider
        navigation.setElevation(dp(4));
        page.addView(navigation);
        addNavigationButton(navigation, "Pantry",   PantryActivity.class);
        addNavigationButton(navigation, "Recipes",  RecipeCollectionActivity.class);
        addNavigationButton(navigation, "Settings", SettingsActivity.class);
    }

    private void addNavigationButton(LinearLayout row, String label, Class<?> screen) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(subtle);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMaxLines(1);
        button.setPadding(dp(6), dp(12), dp(6), dp(12));
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        row.addView(button, new LinearLayout.LayoutParams(0, dp(60), 1));
        button.setOnClickListener(view -> startActivity(new Intent(this, screen)));
    }

    /** Plain text view added to content. */
    TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(bold ? ink : subtle);
        view.setLineSpacing(dp(2), 1f);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    /** Pill-shaped primary action button added to content. */
    Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(accentText);
        button.setTextSize(16);
        button.setTypeface(null, Typeface.BOLD);
        button.setMinHeight(dp(52));
        button.setPadding(dp(20), dp(4), dp(20), dp(4));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(accent);
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin    = dp(8);
        params.bottomMargin = dp(8);
        content.addView(button, params);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    void message(String value) {
        new AlertDialog.Builder(this)
                .setMessage(value)
                .setPositiveButton("OK", null)
                .show();
    }
}
