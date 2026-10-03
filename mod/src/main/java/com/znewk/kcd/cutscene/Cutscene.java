package com.znewk.kcd.cutscene;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.util.GsonHelper;

/**
 * Кат-сцена из {@code data/<ns>/kcd_cutscene/<id>.json} (id — имя файла):
 * <pre>
 * { "title": "Меч готов", "anchor": "npc:kcd:martin", "duration": 200,
 *   "camera": [ {"at": 0, "pos": [-6, 2.5, -3], "look": [0, 1.5, 0]}, {"at": 200, "pos": [...], "look": [...]} ],
 *   "subtitles": [ {"at": 0, "until": 70, "who": "Мартин", "text": "..."} ] }
 * </pre>
 * Координаты камеры — смещения от якоря (житель или точка {@code "pos:x,y,z"}), время — в тиках (20 в секунду).
 */
public record Cutscene(String id, String title, String anchor, int duration, List<Key> camera, List<Subtitle> subtitles) {

    public record Key(int at, double[] pos, double[] look) {}

    public record Subtitle(int at, int until, String who, String text) {}

    static Cutscene parse(String id, JsonObject o) {
        List<Key> keys = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "camera")) {
            JsonObject k = e.getAsJsonObject();
            keys.add(new Key(GsonHelper.getAsInt(k, "at"), vec(k, "pos"), vec(k, "look")));
        }
        if (keys.isEmpty()) throw new JsonParseException("нет ключей камеры");
        keys.sort((a, b) -> Integer.compare(a.at(), b.at()));
        List<Subtitle> subs = new ArrayList<>();
        if (o.has("subtitles")) for (JsonElement e : GsonHelper.getAsJsonArray(o, "subtitles")) {
            JsonObject s = e.getAsJsonObject();
            subs.add(new Subtitle(GsonHelper.getAsInt(s, "at"), GsonHelper.getAsInt(s, "until"),
                GsonHelper.getAsString(s, "who", ""), GsonHelper.getAsString(s, "text")));
        }
        int duration = GsonHelper.getAsInt(o, "duration", keys.get(keys.size() - 1).at());
        return new Cutscene(id, GsonHelper.getAsString(o, "title"), GsonHelper.getAsString(o, "anchor"), duration,
            List.copyOf(keys), List.copyOf(subs));
    }

    private static double[] vec(JsonObject o, String key) {
        JsonArray a = GsonHelper.getAsJsonArray(o, key);
        if (a.size() != 3) throw new JsonParseException(key + ": нужно три числа");
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }
}
