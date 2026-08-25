package com.qxplays.player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Recently played items with resume positions. */
public class History {

    public static class Entry {
        public MediaItem item;
        public long posMs;
        public long playedAt;
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        JSONArray arr = Prefs.getJson("history");
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.item = MediaItem.fromJson(o.optJSONObject("item"));
                e.posMs = o.optLong("pos", 0);
                e.playedAt = o.optLong("at", 0);
                if (e.item.uri != null && !e.item.uri.isEmpty()) out.add(e);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static void record(MediaItem item, long posMs) {
        List<Entry> list = all();
        Entry found = null;
        for (Entry e : list) {
            if (e.item.uri.equals(item.uri)) { found = e; break; }
        }
        if (found == null) {
            found = new Entry();
            found.item = item;
            list.add(0, found);
        } else {
            list.remove(found);
            list.add(0, found);
        }
        found.posMs = posMs;
        found.playedAt = System.currentTimeMillis();
        while (list.size() > 60) list.remove(list.size() - 1);

        JSONArray arr = new JSONArray();
        for (Entry e : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("item", e.item.toJson());
                o.put("pos", e.posMs);
                o.put("at", e.playedAt);
            } catch (Exception ignored) {}
            arr.put(o);
        }
        Prefs.putJson("history", arr);
    }

    public static long resumeFor(String uri) {
        if (!Prefs.resume()) return 0;
        for (Entry e : all()) {
            if (e.item.uri.equals(uri) && e.posMs > 3000) {
                long dur = e.item.durationMs;
                if (dur <= 0 || e.posMs < dur - 10000) return e.posMs;
            }
        }
        return 0;
    }

    public static void clear() { Prefs.putJson("history", new JSONArray()); }

    public static void remove(String uri) {
        List<Entry> list = all();
        List<Entry> keep = new ArrayList<>();
        for (Entry e : list) if (!e.item.uri.equals(uri)) keep.add(e);
        JSONArray arr = new JSONArray();
        for (Entry e : keep) {
            JSONObject o = new JSONObject();
            try {
                o.put("item", e.item.toJson());
                o.put("pos", e.posMs);
                o.put("at", e.playedAt);
            } catch (Exception ignored) {}
            arr.put(o);
        }
        Prefs.putJson("history", arr);
    }
}
