package za.co.smartpantry;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class BaseActivity extends Activity {
    final int ink = Color.rgb(30, 48, 39);
    final int green = Color.rgb(40, 104, 73);
    final int cream = Color.rgb(247, 247, 239);

    LinearLayout page;
    LinearLayout content;

    void startScreen(String title) {
        getWindow().setStatusBarColor(ink);
        getWindow().setNavigationBarColor(ink);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(cream);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    view.getPaddingLeft(),
                    insets.getSystemWindowInsetTop(),
                    view.getPaddingRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(page);

        TextView titleBar = new TextView(this);
        titleBar.setText("  " + title);
        titleBar.setTextColor(Color.WHITE);
        titleBar.setTextSize(22);
        titleBar.setTypeface(null, Typeface.BOLD);
        titleBar.setBackgroundColor(ink);
        titleBar.setPadding(dp(18), dp(20), dp(18), dp(20));
        page.addView(titleBar);

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content = new LinearLayout(this);
        content.setPadding(dp(18), dp(16), dp(18), dp(16));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setBackgroundColor(Color.WHITE);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(navigation);
        addNavigationButton(navigation, "Pantry", PantryActivity.class);
        addNavigationButton(navigation, "Recipes", RecipeCollectionActivity.class);
        addNavigationButton(navigation, "Settings", SettingsActivity.class);
    }

    private void addNavigationButton(LinearLayout row, String label, Class<?> screen) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(green);
        button.setTextSize(16);
        button.setMinWidth(0);
        button.setMinHeight(dp(56));
        button.setMaxLines(1);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.WHITE));
        row.addView(button, new LinearLayout.LayoutParams(0, dp(64), 1));
        button.setOnClickListener(view -> startActivity(new Intent(this, screen)));
    }

    TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ink);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        view.setPadding(4, 10, 4, 10);
        return view;
    }

    Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(green));
        button.setTextSize(18);
        button.setMinHeight(dp(60));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        content.addView(button, params);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    void message(String value) {
        new AlertDialog.Builder(this)
                .setMessage(value)
                .setPositiveButton("OK", null)
                .show();
    }
}
