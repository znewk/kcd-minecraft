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
    private static final String EXE_URL = "https://github.com/playit-cloud/playit-agent/releases/download/v1.0.10/playit-windows-x86_64-signed.exe";
    private static final String EXE_SHA256 = "2dbdaad119844cbbc062cc9774b8b462afa5f1b4b7832a9fc5ef4676cae887cf";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();

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
            address = ensureTunnel(secret, agentId);

            startProcess(exe, dir, secret);
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
        HttpResponse<InputStream> resp = HTTP.send(HttpRequest.newBuilder(URI.create(EXE_URL)).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new IOException("не удалось скачать playit (HTTP " + resp.statusCode() + ")");
        try (InputStream in = resp.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
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
            req.addProperty("version", "kcd-" + KcdMod.version());
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

    private static String ensureTunnel(String secret, String agentId) throws IOException, InterruptedException {
        String found = findTunnel(secret, agentId);
        if (found != null) return found;

        JsonObject protocol = new JsonObject();
        protocol.addProperty("type", "tunnel-type");
        protocol.addProperty("details", "minecraft-java");
        JsonObject config = new JsonObject();
        config.add("fields", new JsonArray());
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
