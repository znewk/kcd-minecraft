package com.znewk.kcd.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.util.GsonHelper;

/**
 * Задание из {@code data/<ns>/kcd_quest/<id>.json} (id — имя файла, общий для всех пространств имён):
 * <pre>
 * { "title": "Меч для пана Радцига", "type": "main",
 *   "entries": { "start": "Запись в дневнике...", "done": "..." },
 *   "objectives": [
 *     { "id": "crossguard", "text": "Забрать крестовину у стражи" },
 *     { "id": "return", "text": "Вернуться к отцу", "after": ["crossguard"] } ] }
 * </pre>
 * Запись «start» появляется при получении задания, «done» — при выполнении, остальные — действием
 * {@code diary:<квест>:<запись>}. Цель с «after» видна, только когда выполнены перечисленные.
 */
public record QuestDefinition(String id, String title, boolean main, Map<String, String> entries, List<Objective> objectives) {

    public record Objective(String id, String text, List<String> after) {}

    static QuestDefinition parse(String id, JsonObject o) {
        String type = GsonHelper.getAsString(o, "type", "side");
        if (!type.equals("main") && !type.equals("side")) throw new JsonParseException("type: main или side");
        Map<String, String> entries = new LinkedHashMap<>();
        if (o.has("entries")) {
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(o, "entries").entrySet()) {
                entries.put(e.getKey(), e.getValue().getAsString());
            }
        }
        List<Objective> objectives = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "objectives")) {
            JsonObject ob = e.getAsJsonObject();
            List<String> after = new ArrayList<>();
            if (ob.has("after")) for (JsonElement a : GsonHelper.getAsJsonArray(ob, "after")) after.add(a.getAsString());
            objectives.add(new Objective(GsonHelper.getAsString(ob, "id"), GsonHelper.getAsString(ob, "text"), List.copyOf(after)));
        }
        return new QuestDefinition(id, GsonHelper.getAsString(o, "title"), type.equals("main"), Map.copyOf(entries), List.copyOf(objectives));
    }

    public boolean hasObjective(String objective) {
        return objectives.stream().anyMatch(o -> o.id().equals(objective));
    }
}
