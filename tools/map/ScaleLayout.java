import com.google.gson.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

/**
 * Масштабировать план вокруг (0,0): координаты и радиусы × k, размеры построек (w, d) не трогаем.
 * Запуск: java -cp gson.jar tools/map/ScaleLayout.java <k> <файл.json>...
 */
public class ScaleLayout {
    static final Set<String> SCALAR = Set.of("x", "z", "x2", "z2", "r", "rx", "rz");
    static final Set<String> POINTS = Set.of("points", "poly");

    public static void main(String[] args) throws Exception {
        double k = Double.parseDouble(args[0]);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        for (int i = 1; i < args.length; i++) {
            File f = new File(args[i]);
            JsonElement root = JsonParser.parseString(Files.readString(f.toPath()));
            scale(root, k);
            Files.writeString(f.toPath(), gson.toJson(root));
            System.out.println("× " + k + ": " + f);
        }
    }

    static void scale(JsonElement e, double k) {
        if (e.isJsonArray()) { for (JsonElement c : e.getAsJsonArray()) scale(c, k); return; }
        if (!e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        for (String key : o.keySet().toArray(new String[0])) {
            JsonElement v = o.get(key);
            if (SCALAR.contains(key) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                o.addProperty(key, Math.round(v.getAsDouble() * k));
            } else if (POINTS.contains(key) && v.isJsonArray()) {
                for (JsonElement p : v.getAsJsonArray()) {
                    JsonArray a = p.getAsJsonArray();
                    a.set(0, new JsonPrimitive(Math.round(a.get(0).getAsDouble() * k)));
                    a.set(1, new JsonPrimitive(Math.round(a.get(1).getAsDouble() * k)));
                }
            } else {
                scale(v, k);
            }
        }
    }
}
