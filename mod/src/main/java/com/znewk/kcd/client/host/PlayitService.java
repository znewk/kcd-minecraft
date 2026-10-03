package com.znewk.kcd.client.host;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

import com.znewk.kcd.KcdMod;

/**
 * Мост playit.gg для хоста: привязка аккаунта, туннель «Minecraft Java» и фоновый агент playit.exe.
 * Работает в отдельном потоке; игра читает {@link #state()} и {@link #address()}.
 * API — https://api.playit.gg (тот же протокол, что у официального плагина playit для Minecraft).
 */
public final class PlayitService {
    public enum State { IDLE, DOWNLOADING, CLAIMING, CONNECTING, ONLINE, ERROR }

    private static final String API = "https://api.playit.gg";
    /** Версия агента playit (github.com/playit-cloud/playit-agent/releases). */
    private static final String AGENT_VERSION = "1.0.12";
    private static final String EXE_URL = "https://github.com/playit-cloud/playit-agent/releases/download/v" + AGENT_VERSION + "/playit-windows-x86_64-signed.exe";
    private static final String EXE_SHA256 = "367fb813a20c67c7501e3921298ce51a641e5b25e6005f6059808f89404ba44b";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();

    private static volatile State state = State.IDLE;
    private static volatile String address;
    private static volatile String claimUrl;
    private static volatile String error;
    private static volatile boolean stopRequested;
    private static Thread worker;
    private static Process process;

    private PlayitService() {}

    public static State state() { return state; }

    public static String address() { return address; }

    public static String claimUrl() { return claimUrl; }

    public static String error() { return error; }

    public static synchronized void start() {
        if ("false".equals(System.getProperty("kcd.playit"))) return; // автотесты без playit
        if (worker != null && worker.isAlive()) return;
        stopRequested = false;
        error = null;
        worker = new Thread(PlayitService::run, "KCD-playit");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() {
        stopRequested = true;
        if (worker != null) worker.interrupt();
        worker = null;
        killProcess();
        state = State.IDLE;
        address = null;
        claimUrl = null;
    }

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("kcd").resolve("playit");
    }

    private static void run() {
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            Path exe = ensureExe(dir);

            Path secretFile = dir.resolve("secret.txt");
            String secret = Files.exists(secretFile) ? Files.readString(secretFile).strip() : null;
            String agentId = secret == null ? null : agentId(secret);
            if (agentId == null) {
                secret = claim();
                Files.writeString(secretFile, secret);
                agentId = agentId(secret);
                if (agentId == null) throw new IOException("playit не принял новый ключ агента");
            }
            claimUrl = null;

            state = State.CONNECTING;
            HostSession.notifyStatus();
            // сначала агент выходит на связь (сервис узнаёт его версию), потом создаём адрес
            startProcess(exe, dir, secret);
            address = tunnelWhenAgentReady(secret, agentId);
            state = State.ONLINE;
            HostSession.notifyAddress(address);

            // следим за агентом: если упал — перезапускаем
            int restarts = 0;
            while (!stopRequested) {
                Thread.sleep(3000);
                if (process != null && !process.isAlive() && !stopRequested) {
                    if (++restarts > 5) throw new IOException("агент playit несколько раз завершился, см. kcd/playit/playit.log");
                    KcdMod.LOGGER.warn("KCD: агент playit завершился, перезапуск");
                    startProcess(exe, dir, secret);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (stopRequested) return;
            KcdMod.LOGGER.error("KCD: playit не запустился", e);
            error = e.getMessage();
            state = State.ERROR;
            HostSession.notifyStatus();
        }
    }

    // ------------------------------------------------------------------ playit.exe

    private static Path ensureExe(Path dir) throws IOException, InterruptedException {
        Path exe = dir.resolve("playit.exe");
        if (Files.exists(exe) && EXE_SHA256.equals(sha256(exe))) return exe;
        state = State.DOWNLOADING;
        HostSession.notifyStatus();
        Path tmp = dir.resolve("playit.exe.part");
        // GitHub иногда долго отвечает — три попытки
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<InputStream> resp = HTTP.send(HttpRequest.newBuilder(URI.create(EXE_URL)).timeout(Duration.ofMinutes(3)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) throw new IOException("не удалось скачать playit (HTTP " + resp.statusCode() + ")");
                try (InputStream in = resp.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                break;
            } catch (IOException e) {
                if (attempt >= 3 || stopRequested) throw e;
                KcdMod.LOGGER.warn("KCD: скачивание playit, попытка {}: {}", attempt, e.toString());
                Thread.sleep(3000L * attempt);
            }
        }
        if (!EXE_SHA256.equals(sha256(tmp))) {
            Files.deleteIfExists(tmp);
            throw new IOException("скачанный playit.exe не совпал по контрольной сумме");
        }
        Files.move(tmp, exe, StandardCopyOption.REPLACE_EXISTING);
        return exe;
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static synchronized void startProcess(Path exe, Path dir, String secret) throws IOException {
        killProcess();
        ProcessBuilder pb = new ProcessBuilder(exe.toString(), "--secret", secret, "-l", dir.resolve("playit.log").toString())
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(dir.resolve("playit-console.log").toFile());
        process = pb.start();
    }

    private static synchronized void killProcess() {
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            process = null;
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PlayitService::killProcess, "KCD-playit-shutdown"));
    }

    // ------------------------------------------------------------------ привязка аккаунта

    private static String claim() throws IOException, InterruptedException {
        state = State.CLAIMING;
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        String code = HexFormat.of().formatHex(bytes);
        claimUrl = "https://playit.gg/claim/" + code;
        HostSession.notifyClaim(claimUrl);

        long deadline = System.currentTimeMillis() + 15 * 60_000L;
        while (!stopRequested && System.currentTimeMillis() < deadline) {
            JsonObject req = new JsonObject();
            req.addProperty("code", code);
            req.addProperty("agent_type", "self-managed");
            // как официальный агент: сервис проверяет версию и отказывает «слишком старым»
            req.addProperty("version", "playit " + AGENT_VERSION);
            JsonObject resp = call("/claim/setup", req, null);
            String status = resp.get("status").getAsString();
            String data = resp.has("data") && resp.get("data").isJsonPrimitive() ? resp.get("data").getAsString() : String.valueOf(resp.get("data"));
            if ("success".equals(status) && "UserAccepted".equals(data)) {
                JsonObject ex = new JsonObject();
                ex.addProperty("code", code);
                JsonObject exResp = call("/claim/exchange", ex, null);
                if ("success".equals(exResp.get("status").getAsString())) {
                    return exResp.getAsJsonObject("data").get("secret_key").getAsString();
                }
                throw new IOException("playit: обмен кода не удался: " + exResp.get("data"));
            }
            if ("success".equals(status) && "UserRejected".equals(data)) throw new IOException("привязка playit отклонена");
            if ("fail".equals(status) && (data.contains("CodeExpired") || data.contains("InvalidCode"))) {
                return claim();
            }
            Thread.sleep(2000);
        }
        throw new IOException("время на привязку playit истекло");
    }

    /** ID агента для ключа или null, если ключ недействителен. */
    private static String agentId(String secret) throws IOException, InterruptedException {
        try {
            JsonObject resp = call("/v1/agents/rundata", new JsonObject(), secret);
            if (!"success".equals(resp.get("status").getAsString())) return null;
            return resp.getAsJsonObject("data").get("agent_id").getAsString();
        } catch (HttpStatusException e) {
            if (e.status == 400 || e.status == 401) return null;
            throw e;
        }
    }

    // ------------------------------------------------------------------ туннель

    /** Пока только что запущенный агент не отметился у сервиса, создание туннеля отвечает AgentVersionTooOld — ждём до минуты. */
    private static String tunnelWhenAgentReady(String secret, String agentId) throws IOException, InterruptedException {
        for (int i = 0; ; i++) {
            try {
                return ensureTunnel(secret, agentId);
            } catch (HttpStatusException e) {
                if (i >= 20 || stopRequested || !String.valueOf(e.getMessage()).contains("AgentVersionTooOld")) throw e;
                if (process != null && !process.isAlive()) throw new IOException("агент playit завершился, см. kcd/playit/playit.log");
                Thread.sleep(3000);
            }
        }
    }

    private static String ensureTunnel(String secret, String agentId) throws IOException, InterruptedException {
        String found = findTunnel(secret, agentId);
        if (found != null) {
            fixLocalPort(secret, agentId);
            return found;
        }

        JsonObject protocol = new JsonObject();
        protocol.addProperty("type", "tunnel-type");
        protocol.addProperty("details", "minecraft-java");
        // без local_port агент молча пропускает туннель (адрес *.tun.ply.gg без порта) — друзья ловят таймаут
        JsonArray fields = new JsonArray();
        fields.add(field("local_ip", "127.0.0.1"));
        fields.add(field("local_port", String.valueOf(HostSession.PORT)));
        JsonObject config = new JsonObject();
        config.add("fields", fields);
        JsonObject originData = new JsonObject();
        originData.addProperty("agent_id", agentId);
        originData.add("config", config);
        JsonObject origin = new JsonObject();
        origin.addProperty("type", "agent");
        origin.add("data", originData);
        JsonObject region = new JsonObject();
        region.addProperty("region", "global");
        JsonObject endpoint = new JsonObject();
        endpoint.addProperty("type", "region");
        endpoint.add("details", region);
        JsonObject req = new JsonObject();
        req.addProperty("name", "KCD");
        req.add("protocol", protocol);
        req.add("origin", origin);
        req.add("endpoint", endpoint);
        req.addProperty("enabled", true);
        JsonObject resp = call("/v1/tunnels/create", req, secret);
        if (!"success".equals(resp.get("status").getAsString())) throw new IOException("playit: не удалось создать туннель: " + resp.get("data"));

        for (int i = 0; i < 20 && !stopRequested; i++) {
            Thread.sleep(1500);
            found = findTunnel(secret, agentId);
            if (found != null) return found;
        }
        throw new IOException("playit: туннель создан, но адрес не появился");
    }

    private static JsonObject field(String name, String value) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        return f;
    }

    /** Туннели, созданные версиями мода до 0.0.4, — без local_port; дописываем. */
    private static void fixLocalPort(String secret, String agentId) throws IOException, InterruptedException {
        JsonObject run = call("/v1/agents/rundata", new JsonObject(), secret);
        JsonArray tunnels = run.getAsJsonObject("data").getAsJsonArray("tunnels");
        if (tunnels == null) return;
        for (JsonElement t : tunnels) {
            JsonObject tunnel = t.getAsJsonObject();
            if (!"minecraft-java".equals(str(tunnel, "tunnel_type"))) continue;
            boolean hasPort = false;
            JsonObject cfg = tunnel.getAsJsonObject("agent_config");
            if (cfg != null && cfg.has("fields")) {
                for (JsonElement f : cfg.getAsJsonArray("fields")) {
                    if ("local_port".equals(str(f.getAsJsonObject(), "name"))) hasPort = true;
                }
            }
            if (hasPort) continue;
            JsonObject req = new JsonObject();
            req.addProperty("tunnel_id", str(tunnel, "id"));
            req.addProperty("local_ip", "127.0.0.1");
            req.addProperty("local_port", HostSession.PORT);
            req.addProperty("agent_id", agentId);
            req.addProperty("enabled", true);
            call("/tunnels/update", req, secret);
            KcdMod.LOGGER.info("KCD: туннелю playit {} прописан local_port {}", str(tunnel, "id"), HostSession.PORT);
        }
    }

    private static String findTunnel(String secret, String agentId) throws IOException, InterruptedException {
        JsonObject resp = call("/v1/tunnels/list", new JsonObject(), secret);
        if (!"success".equals(resp.get("status").getAsString())) throw new IOException("playit: список туннелей недоступен");
        JsonArray tunnels = resp.getAsJsonObject("data").getAsJsonArray("tunnels");
        if (tunnels == null) return null;
        String fallback = null;
        for (JsonElement t : tunnels) {
            JsonObject tunnel = t.getAsJsonObject();
            if (!"minecraft-java".equals(str(tunnel, "tunnel_type"))) continue;
            String addr = displayAddress(tunnel);
            if (addr == null) continue;
            if (agentId.equals(originAgent(tunnel))) return addr;
            if (fallback == null && originAgent(tunnel) == null) fallback = addr;
        }
        return fallback;
    }

    private static String originAgent(JsonObject tunnel) {
        if (!tunnel.has("origin") || !tunnel.get("origin").isJsonObject()) return null;
        JsonObject origin = tunnel.getAsJsonObject("origin");
        if (!origin.has("details") || !origin.get("details").isJsonObject()) return null;
        return str(origin.getAsJsonObject("details"), "agent_id");
    }

    private static String displayAddress(JsonObject tunnel) {
        if (!tunnel.has("connect_addresses") || !tunnel.get("connect_addresses").isJsonArray()) return null;
        JsonArray addrs = tunnel.getAsJsonArray("connect_addresses");
        if (addrs.isEmpty()) return null;
        JsonObject first = addrs.get(0).getAsJsonObject();
        if (!first.has("value") || !first.get("value").isJsonObject()) return null;
        JsonObject value = first.getAsJsonObject("value");
        String type = str(first, "type");
        String a = str(value, "address");
        if (a == null) return null;
        if (("ip4".equals(type) || "ip6".equals(type)) && value.has("default_port")) return a + ":" + value.get("default_port").getAsInt();
        return a;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    // ------------------------------------------------------------------ HTTP

    private static final class HttpStatusException extends IOException {
        final int status;

        HttpStatusException(int status, String path, String body) {
            super("playit API " + path + ": HTTP " + status + " " + body);
            this.status = status;
        }
    }

    private static JsonObject call(String path, JsonObject body, String secret) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (secret != null) b.header("Authorization", "agent-key " + secret);
        HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new HttpStatusException(resp.statusCode(), path, resp.body());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }
}
