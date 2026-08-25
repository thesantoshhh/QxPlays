package com.qxplays.player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Favorites (♥). */
public class Favorites {

    public static List<MediaItem> all() {
        List<MediaItem> out = new ArrayList<>();
        JSONArray arr = Prefs.getJson("favorites");
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                MediaItem it = MediaItem.fromJson(o);
                if (it.uri != null && !it.uri.isEmpty()) out.add(it);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static boolean has(String uri) {
        for (MediaItem it : all()) if (it.uri.equals(uri)) return true;
        return false;
    }

    public static void toggle(MediaItem item) {
        List<MediaItem> list = all();
        List<MediaItem> keep = new ArrayList<>();
        boolean removed = false;
        for (MediaItem it : list) {
            if (it.uri.equals(item.uri)) { removed = true; continue; }
            keep.add(it);
        }
        if (!removed) keep.add(0, item);
        JSONArray arr = new JSONArray();
        for (MediaItem it : keep) arr.put(it.toJson());
        Prefs.putJson("favorites", arr);
    }
}
