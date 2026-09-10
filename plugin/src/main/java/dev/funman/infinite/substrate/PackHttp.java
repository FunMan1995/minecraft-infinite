package dev.funman.infinite.substrate;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Serves client-mod jars from subs/&lt;name&gt;/client-mods and kits/&lt;uuid&gt;/client-mods
 * so the optional client can fetch a pack at a border instead of a corrupt-world warning.
 */
public final class PackHttp {
    private final JavaPlugin plugin;
    private final Substrate substrate;
    private final int port;
    private HttpServer server;

    public PackHttp(JavaPlugin plugin, Substrate substrate, int port) {
        this.plugin = plugin;
        this.substrate = substrate;
        this.port = port;
    }

    public int port() {
        return port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/packs", this::handle);
            server.setExecutor(runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
            server.start();
            plugin.getLogger().info("Pack HTTP on :" + port + " (client-mods for subs and kits)");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Pack HTTP did not start", ex);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public String manifestJson(String type, String id) {
        File mods = clientMods(type, id);
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"").append(esc(type)).append("\",\"id\":\"").append(esc(id)).append("\",");
        json.append("\"loadServerMods\":").append("sub".equals(type)).append(',');
        json.append("\"passClientMods\":true,\"clientMods\":[");
        File[] jars = mods == null ? null : mods.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars != null) {
            boolean first = true;
            for (File jar : jars) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"name\":\"").append(esc(jar.getName())).append("\",");
                json.append("\"size\":").append(jar.length()).append(',');
                json.append("\"sha256\":\"").append(sha256(jar)).append("\"}");
            }
        }
        json.append("]}");
        return json.toString();
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        // /packs/sub/<name>/manifest.json
        // /packs/sub/<name>/client-mods/<file>
        // /packs/kit/<uuid>/manifest.json
        // /packs/kit/<uuid>/client-mods/<file>
        String[] parts = path.split("/");
        if (parts.length < 5) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        String type = parts[2];
        String id = parts[3];
        if ("manifest.json".equals(parts[4])) {
            byte[] body = manifestJson(type, id).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            return;
        }
        if (parts.length >= 6 && "client-mods".equals(parts[4])) {
            File dir = clientMods(type, id);
            File jar = new File(dir, parts[5]).getCanonicalFile();
            if (dir == null || !jar.getPath().startsWith(dir.getCanonicalPath()) || !jar.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = Files.readAllBytes(jar.toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            return;
        }
        exchange.sendResponseHeaders(404, -1);
    }

    private File clientMods(String type, String id) {
        if ("sub".equals(type)) {
            Substrate.Sub sub = substrate.sub(id);
            return sub == null ? null : new File(sub.folder(substrate.host()), "client-mods");
        }
        if ("kit".equals(type)) {
            try {
                Substrate.Kit kit = substrate.kit(UUID.fromString(id));
                return kit == null ? null : kit.clientMods(substrate.host());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }

    private static String sha256(File file) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
