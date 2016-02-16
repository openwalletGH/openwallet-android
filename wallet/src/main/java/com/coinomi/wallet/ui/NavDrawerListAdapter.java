package com.coinomi.wallet.ui;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.NavDrawerItemView;

import java.util.ArrayList;
import java.util.List;

import static com.coinomi.wallet.ui.NavDrawerItemType.ITEM_SECTION_TITLE;
import static com.coinomi.wallet.ui.NavDrawerItemType.ITEM_SEPARATOR;

/**
 * @author John L. Jegutanis
 */
public class NavDrawerListAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private List<NavDrawerItem> items = new ArrayList<>();

    public NavDrawerListAdapter(final Context context, List<NavDrawerItem> items) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.items = items;
    }

    public void setItems(List<NavDrawerItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).itemType.ordinal();
    }

    @Override
    public int getViewTypeCount() {
        return NavDrawerItemType.values().length;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public NavDrawerItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        NavDrawerItem item = getItem(position);

        if (row == null) {
            switch (item.itemType) {
                case ITEM_SEPARATOR:
                    row = inflater.inflate(R.layout.nav_drawer_separator, null);
                    break;
                case ITEM_SECTION_TITLE:
                    row = inflater.inflate(R.layout.nav_drawer_section_title, null);
                    break;
                case ITEM_COIN:
                case ITEM_OVERVIEW:
                case ITEM_TRADE:
                    row = new NavDrawerItemView(context);
                    break;
                default:
                    throw new RuntimeException("Unknown type: " + item.itemType);
            }
        }

        if (isSeparator(item.itemType)) {
            setNotClickable(row);
        }

        switch (item.itemType) {
            case ITEM_SECTION_TITLE:
                if (row instanceof TextView) ((TextView)row).setText(item.title);
                break;
            case ITEM_COIN:
            case ITEM_OVERVIEW:
            case ITEM_TRADE:
                ((NavDrawerItemView) row).setData(item.title, item.iconRes);
                break;
        }

        return row;
    }

    private boolean isSeparator(NavDrawerItemType itemType) {
        return itemType == ITEM_SEPARATOR || itemType == ITEM_SECTION_TITLE;
    }

    @Override
    public boolean isEnabled(int position) {
        return !isSeparator(getItem(position).itemType);
    }

    private void setNotClickable(View view) {
        view.setClickable(false);
        view.setFocusable(false);
        view.setContentDescription("");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }
}