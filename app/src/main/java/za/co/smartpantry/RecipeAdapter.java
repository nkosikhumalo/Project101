package za.co.smartpantry;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class RecipeAdapter extends BaseAdapter {
    private final Context context;
    private final List<Recipe> recipes;
    private String emptyMessage;

    public RecipeAdapter(Context context, List<Recipe> recipes) {
        this.context = context;
        this.recipes = recipes;
    }

    @Override
    public int getCount() {
        return recipes.isEmpty() ? 1 : recipes.size();
    }

    @Override
    public Object getItem(int position) {
        return recipes.isEmpty() ? null : recipes.get(position);
    }

    @Override
    public long getItemId(int position) {
        return recipes.isEmpty() ? 0 : recipes.get(position).id;
    }

    public void setEmptyMessage(String message) {
        emptyMessage = message;
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View recycledView, ViewGroup parent) {
        TextView row = recycledView instanceof TextView
                ? (TextView) recycledView
                : new TextView(context);

        String label;
        if (recipes.isEmpty()) {
            label = emptyMessage == null ? "Loading recipes…" : emptyMessage;
        } else {
            label = recipes.get(position).label();
        }

        row.setText(label);
        row.setTextColor(0xff141414);
        row.setBackgroundColor(0xfff2f2f2);
        row.setTextSize(17);
        row.setPadding(18, 15, 12, 15);
        return row;
    }
}
