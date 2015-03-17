package com.coinomi.wallet.ui;

import java.util.List;

/**
 * @author John L. Jegutanis
 */
public class NavDrawerItem {
    NavDrawerItemType itemType;
    String title;
    int iconRes;
    Object itemData;

    public NavDrawerItem(NavDrawerItemType itemType, String title, int iconRes, Object itemData) {
        this.itemType = itemType;
        this.title = title;
        this.iconRes = iconRes;
        this.itemData = itemData;
    }

    public static void addItem(List<NavDrawerItem> items, NavDrawerItemType type) {
        NavDrawerItem.addItem(items, type, null, -1, null);
    }

    public static void addItem(List<NavDrawerItem> items, NavDrawerItemType type, String title) {
        NavDrawerItem.addItem(items, type, title, -1, null);
    }

    public static void addItem(List<NavDrawerItem> items, NavDrawerItemType type, String title, Integer iconRes, Object data) {
        items.add(new NavDrawerItem(type, title, iconRes, data));
    }
}
