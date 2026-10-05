package za.co.smartpantry;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
        setContentView(page);

        TextView titleBar = new TextView(this);
        titleBar.setText("  " + title);
        titleBar.setTextColor(Color.WHITE);
        titleBar.setTextSize(22);
        titleBar.setTypeface(null, Typeface.BOLD);
        titleBar.setBackgroundColor(ink);
        titleBar.setPadding(18, 20, 18, 20);
        page.addView(titleBar);

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content = new LinearLayout(this);
        content.setPadding(18, 16, 18, 16);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setBackgroundColor(Color.WHITE);
        page.addView(navigation);
        addNavigationButton(navigation, "Pantry", PantryActivity.class);
        addNavigationButton(navigation, "Recipes", SuggestionsActivity.class);
        addNavigationButton(navigation, "Settings", SettingsActivity.class);
    }

    private void addNavigationButton(LinearLayout row, String label, Class<?> screen) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(green);
        row.addView(button, new LinearLayout.LayoutParams(0, 56, 1));
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
        content.addView(button);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    void message(String value) {
        new AlertDialog.Builder(this)
                .setMessage(value)
                .setPositiveButton("OK", null)
                .show();
    }
}
