package za.co.smartpantry;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class PantryAdapter extends BaseAdapter {
    private final Context context;
    private final List<Ingredient> items;

    public PantryAdapter(Context context, List<Ingredient> items) {
        this.context = context;
        this.items = items;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public View getView(int position, View recycledView, ViewGroup parent) {
        TextView row = recycledView instanceof TextView
                ? (TextView) recycledView
                : new TextView(context);
        row.setText(items.get(position).label());
        row.setTextColor(0xff1e3027);
        row.setTextSize(17);
        row.setPadding(18, 15, 12, 15);
        return row;
    }

    public void refresh() {
        notifyDataSetChanged();
    }
}
