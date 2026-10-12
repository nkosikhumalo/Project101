package za.co.smartpantry;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class PantryAdapter extends BaseAdapter {

    public interface RowAction {
        void onEdit(Ingredient item);
        void onDelete(Ingredient item);
    }

    private final Context context;
    private final List<Ingredient> items;
    private final RowAction listener;

    public PantryAdapter(Context context, List<Ingredient> items, RowAction listener) {
        this.context  = context;
        this.items    = items;
        this.listener = listener;
    }

    @Override public int     getCount()              { return items.size(); }
    @Override public Object  getItem(int position)   { return items.get(position); }
    @Override public long    getItemId(int position) { return items.get(position).id; }

    @Override
    public View getView(int position, View recycledView, ViewGroup parent) {
        LinearLayout row;
        TextView label;
        TextView editBtn;
        TextView deleteBtn;

        if (recycledView instanceof LinearLayout) {
            row       = (LinearLayout) recycledView;
            label     = (TextView) row.getChildAt(0);
            editBtn   = (TextView) row.getChildAt(1);
            deleteBtn = (TextView) row.getChildAt(2);
        } else {
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xffffffff);
            row.setPadding(px(16), px(14), px(8), px(14));

            label = new TextView(context);
            label.setTextColor(0xff121212);
            label.setTextSize(15);
            label.setLineSpacing(px(2), 1f);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

            editBtn = iconButton("✏️");
            row.addView(editBtn, new LinearLayout.LayoutParams(px(44), px(44)));

            deleteBtn = iconButton("🗑");
            row.addView(deleteBtn, new LinearLayout.LayoutParams(px(44), px(44)));
        }

        Ingredient item = items.get(position);
        label.setText(item.label());
        editBtn.setOnClickListener(v -> listener.onEdit(item));
        deleteBtn.setOnClickListener(v -> listener.onDelete(item));

        return row;
    }

    private TextView iconButton(String emoji) {
        TextView btn = new TextView(context);
        btn.setText(emoji);
        btn.setTextSize(18);
        btn.setTypeface(null, Typeface.NORMAL);
        btn.setGravity(Gravity.CENTER);
        return btn;
    }

    private int px(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public void refresh() {
        notifyDataSetChanged();
    }
}
