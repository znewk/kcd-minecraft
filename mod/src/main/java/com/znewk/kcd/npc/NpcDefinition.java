package com.znewk.kcd.npc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Описание жителя из {@code data/<ns>/kcd_npc/<id>.json}:
 * <pre>{ "name": "Мартин", "skin": "martin", "slim": false, "dialogue": "martin",
 *   "barks": ["Бог в помощь."], "traits": ["строгий"] }</pre>
 * skin "martin" → {@code <ns>:textures/entity/npc/martin.png}; dialogue "martin" → {@code <ns>:martin}.
 * Без диалога житель отвечает случайной фразой из barks.
 */
public record NpcDefinition(ResourceLocation id, String name, String skin, boolean slim,
                            @Nullable ResourceLocation dialogue, List<String> barks, List<String> traits) {

    static NpcDefinition parse(ResourceLocation id, JsonObject o) {
        String name = GsonHelper.getAsString(o, "name");
        String skin = GsonHelper.getAsString(o, "skin", id.getPath());
        ResourceLocation dialogue = o.has("dialogue") ? withDefaultNs(id, GsonHelper.getAsString(o, "dialogue")) : null;
        return new NpcDefinition(id, name, skinPath(id, skin), GsonHelper.getAsBoolean(o, "slim", false),
            dialogue, strings(o, "barks"), strings(o, "traits"));
    }

    private static String skinPath(ResourceLocation id, String skin) {
        if (skin.endsWith(".png")) return withDefaultNs(id, skin).toString();
        ResourceLocation rl = withDefaultNs(id, skin);
        return rl.getNamespace() + ":textures/entity/npc/" + rl.getPath() + ".png";
    }

    public static ResourceLocation withDefaultNs(ResourceLocation owner, String s) {
        ResourceLocation rl = s.contains(":") ? ResourceLocation.tryParse(s) : ResourceLocation.tryBuild(owner.getNamespace(), s);
        if (rl == null) throw new JsonParseException("неверный идентификатор: " + s);
        return rl;
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : GsonHelper.getAsJsonArray(o, key)) out.add(e.getAsString());
        return List.copyOf(out);
    }
}
