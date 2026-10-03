package com.znewk.kcd.dialogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import com.znewk.kcd.stats.KcdStats;

/**
 * Разговор с жителем — граф реплик из {@code data/<ns>/kcd_dialogue/<id>.json}.
 * <pre>
 * { "start": [ {"node": "again", "if": ["memory:met"]}, {"node": "first"} ],
 *   "nodes": {
 *     "first": { "text": "Реплика жителя, {name} — имя собеседника", "do": ["remember:met"],
 *       "story": false,
 *       "options": [
 *         {"text": "Ответ", "next": "work"},
 *         {"text": "Ответ с проверкой", "check": "persuasion:3", "success": "ok", "fail": "no"},
 *         {"text": "Один раз", "once": true, "if": ["!flag:x"], "do": ["flag:x", "rep:+2"], "next": "end"} ] } } }
 * </pre>
 * Условия (if): flag:X, memory:X, role:henry|brother, rep:N (не ниже), stat:speech:N, quest:Q (задание идёт),
 * done:Q (выполнено), objective:Q:цель, has:minecraft:charcoal*10 (у говорящего есть); «!» в начале — отрицание.
 * Действия (do): flag:X, unflag:X, remember:X, forget:X, rep:+N, stat:speech:+N, give:minecraft:bread*2,
 * take:minecraft:charcoal*10, quest:Q (выдать задание), objective:Q:цель, diary:Q:запись, complete:Q, fail:Q,
 * cutscene:id (кат-сцена для отряда).
 * «story»: в этом месте решает Индржих (если он рядом). Узел без вариантов — в конце «Уйти».
 */
public record Dialogue(ResourceLocation id, List<Start> start, Map<String, Node> nodes) {
    public static final String END = "end";

    public record Start(String node, List<Cond> when) {}

    public record Node(String id, String text, List<Effect> effects, List<Option> options, boolean story) {}

    public record Option(String key, String text, List<Cond> when, List<Effect> effects, String next,
                         @Nullable Check check, boolean once) {}

    public record Check(String kind, int difficulty, String success, String fail) {}

    public record Cond(boolean negate, String type, String arg) {}

    public record Effect(String type, String arg) {}

    private static final Set<String> COND_TYPES = Set.of("flag", "memory", "role", "rep", "stat", "quest", "done", "objective", "has");
    private static final Set<String> EFFECT_TYPES = Set.of("flag", "unflag", "remember", "forget", "rep", "stat", "give", "take",
        "quest", "objective", "diary", "complete", "fail", "cutscene");

    static Dialogue parse(ResourceLocation id, JsonObject o) {
        List<Start> start = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "start")) {
            JsonObject s = e.getAsJsonObject();
            start.add(new Start(GsonHelper.getAsString(s, "node"), conds(s)));
        }
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(o, "nodes").entrySet()) {
            nodes.put(e.getKey(), node(e.getKey(), e.getValue().getAsJsonObject()));
        }
        Dialogue d = new Dialogue(id, List.copyOf(start), Map.copyOf(nodes));
        d.validate();
        return d;
    }

    private static Node node(String id, JsonObject o) {
        List<Option> options = new ArrayList<>();
        if (o.has("options")) {
            JsonArray arr = GsonHelper.getAsJsonArray(o, "options");
            for (int i = 0; i < arr.size(); i++) options.add(option(id, i, arr.get(i).getAsJsonObject()));
        }
        return new Node(id, text(o), effects(o), List.copyOf(options), GsonHelper.getAsBoolean(o, "story", false));
    }

    private static Option option(String nodeId, int index, JsonObject o) {
        String key = GsonHelper.getAsString(o, "id", nodeId + "#" + index);
        Check check = null;
        if (o.has("check")) {
            String[] c = GsonHelper.getAsString(o, "check").split(":");
            if (c.length != 2 || !KcdStats.CHECKS.contains(c[0])) throw new JsonParseException("проверка «" + String.join(":", c) + "» в " + key + "; виды: " + KcdStats.CHECKS);
            check = new Check(c[0], Integer.parseInt(c[1]), GsonHelper.getAsString(o, "success"), GsonHelper.getAsString(o, "fail"));
        }
        return new Option(key, text(o), conds(o), effects(o), GsonHelper.getAsString(o, "next", END), check,
            GsonHelper.getAsBoolean(o, "once", false));
    }

    /** Текст — строка или массив строк (абзацы). */
    private static String text(JsonObject o) {
        JsonElement t = o.get("text");
        if (t == null) return "";
        if (t.isJsonArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonElement e : t.getAsJsonArray()) lines.add(e.getAsString());
            return String.join("\n", lines);
        }
        return t.getAsString();
    }

    private static List<Cond> conds(JsonObject o) {
        List<Cond> out = new ArrayList<>();
        if (o.has("if")) for (JsonElement e : GsonHelper.getAsJsonArray(o, "if")) {
            String s = e.getAsString();
            boolean neg = s.startsWith("!");
            if (neg) s = s.substring(1);
            int c = s.indexOf(':');
            String type = c < 0 ? s : s.substring(0, c);
            if (!COND_TYPES.contains(type) || c < 0) throw new JsonParseException("условие «" + e.getAsString() + "»; виды: " + COND_TYPES);
            out.add(new Cond(neg, type, s.substring(c + 1)));
        }
        return List.copyOf(out);
    }

    private static List<Effect> effects(JsonObject o) {
        List<Effect> out = new ArrayList<>();
        if (o.has("do")) for (JsonElement e : GsonHelper.getAsJsonArray(o, "do")) {
            String s = e.getAsString();
            int c = s.indexOf(':');
            String type = c < 0 ? s : s.substring(0, c);
            if (!EFFECT_TYPES.contains(type) || c < 0) throw new JsonParseException("действие «" + s + "»; виды: " + EFFECT_TYPES);
            out.add(new Effect(type, s.substring(c + 1)));
        }
        return List.copyOf(out);
    }

    /** Все ссылки на узлы должны вести в существующие узлы (или "end"). */
    private void validate() {
        if (start.isEmpty()) throw new JsonParseException("пустой start");
        for (Start s : start) ref(s.node(), "start");
        for (Node n : nodes.values()) {
            for (Option o : n.options()) {
                ref(o.next(), o.key());
                if (o.check() != null) {
                    ref(o.check().success(), o.key());
                    ref(o.check().fail(), o.key());
                }
            }
        }
    }

    private void ref(String node, String from) {
        if (!node.equals(END) && !nodes.containsKey(node)) throw new JsonParseException("нет узла «" + node + "» (ссылка из " + from + ")");
    }
}
