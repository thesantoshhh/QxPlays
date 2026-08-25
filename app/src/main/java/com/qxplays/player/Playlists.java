package com.qxplays.player;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** User playlists (persisted locally as JSON). */
public class Playlists {

    public static class Playlist {
        public String name;
        public long created;
        public List<MediaItem> items = new ArrayList<>();
    }

    public static List<Playlist> all() {
        List<Playlist> out = new ArrayList<>();
        JSONArray arr = Prefs.getJson("playlists");
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                Playlist p = new Playlist();
                p.name = o.getString("name");
                p.created = o.optLong("created", 0);
                JSONArray items = o.optJSONArray("items");
                if (items != null) {
                    for (int j = 0; j < items.length(); j++) {
                        try {
                            MediaItem it = MediaItem.fromJson(items.getJSONObject(j));
                            if (it.uri != null && !it.uri.isEmpty()) p.items.add(it);
                        } catch (Exception ignored) {}
                    }
                }
                out.add(p);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static void save(List<Playlist> list) {
        JSONArray arr = new JSONArray();
        for (Playlist p : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", p.name);
                o.put("created", p.created);
                JSONArray items = new JSONArray();
                for (MediaItem it : p.items) items.put(it.toJson());
                o.put("items", items);
            } catch (Exception ignored) {}
            arr.put(o);
        }
        Prefs.putJson("playlists", arr);
    }

    public static Playlist get(String name) {
        for (Playlist p : all()) if (p.name.equals(name)) return p;
        return null;
    }

    public static Playlist create(String name) throws Exception {
        if (name == null || name.trim().isEmpty()) throw new Exception("Enter a playlist name");
        if (get(name) != null) throw new Exception("A playlist with this name already exists");
        Playlist p = new Playlist();
        p.name = name.trim();
        p.created = System.currentTimeMillis();
        List<Playlist> list = all();
        list.add(p);
        save(list);
        return p;
    }

    public static void delete(String name) {
        List<Playlist> list = all();
        List<Playlist> keep = new ArrayList<>();
        for (Playlist p : list) if (!p.name.equals(name)) keep.add(p);
        save(keep);
    }

    public static void rename(String oldName, String newName) throws Exception {
        Playlist p = get(oldName);
        if (p == null) return;
        if (get(newName) != null) throw new Exception("A playlist with this name already exists");
        p.name = newName;
        delete(oldName);
        List<Playlist> list = all();
        list.add(p);
        save(list);
    }

    public static void add(String name, MediaItem item) {
        Playlist p = get(name);
        if (p == null) return;
        for (MediaItem it : p.items) if (it.uri.equals(item.uri)) return;
        p.items.add(item);
        persist(p);
    }

    public static void remove(String name, String uri) {
        Playlist p = get(name);
        if (p == null) return;
        List<MediaItem> keep = new ArrayList<>();
        for (MediaItem it : p.items) if (!it.uri.equals(uri)) keep.add(it);
        p.items = keep;
        persist(p);
    }

    public static boolean contains(String name, String uri) {
        Playlist p = get(name);
        if (p == null) return false;
        for (MediaItem it : p.items) if (it.uri.equals(uri)) return true;
        return false;
    }

    private static void persist(Playlist p) {
        List<Playlist> list = all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(p.name)) { list.set(i, p); break; }
        }
        save(list);
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Playlist p : all()) out.add(p.name);
        Collections.sort(out, String::compareToIgnoreCase);
        return out;
    }
}
