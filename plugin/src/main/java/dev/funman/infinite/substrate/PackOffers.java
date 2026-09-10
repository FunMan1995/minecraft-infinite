package dev.funman.infinite.substrate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** At a claimed seed, offer client-mods instead of a corrupt-pack warning. */
public final class PackOffers implements Listener {
    public static final String CHANNEL = "infinite:kit";

    private final JavaPlugin plugin;
    private final Substrate substrate;
    private final PackHttp http;
    private final Map<UUID, String> last = new ConcurrentHashMap<>();

    public PackOffers(JavaPlugin plugin, Substrate substrate, PackHttp http) {
        this.plugin = plugin;
        this.substrate = substrate;
        this.http = http;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public void offer(Player player, int seed) {
        Substrate.SeedPlace place = substrate.placeOf(seed);
        if (place.place() == Substrate.Place.VANILLA) {
            return;
        }
        String key = place.place().name() + ":" + place.id();
        if (key.equals(last.get(player.getUniqueId()))) {
            return;
        }
        last.put(player.getUniqueId(), key);
        String type = place.place() == Substrate.Place.KIT ? "kit" : "sub";
        boolean serverMods = place.place() == Substrate.Place.SUB && substrate.serverModsForSeed(seed) != null;
        String json = http.manifestJson(type, place.id());
        String host = plugin.getConfig().getString("kit-public-host", "127.0.0.1");
        String url = "http://" + host + ":" + http.port() + "/packs/" + type + "/" + place.id() + "/";
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "This seed is " + type + " " + place.id()
                        + (serverMods ? " (loads server-mods + passes client-mods)." : " (passes client-mods only).")
                        + " Grab: " + url,
                net.kyori.adventure.text.format.NamedTextColor.AQUA
        ));
        byte[] payload = (url + "\n" + json).getBytes(StandardCharsets.UTF_8);
        if (payload.length < 32000) {
            player.sendPluginMessage(plugin, CHANNEL, payload);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        var worlds = plugin.getServer().getServicesManager().load(dev.funman.infinite.InfiniteApi.class);
        if (worlds == null) {
            return;
        }
        int from = worlds.worlds().locate(event.getFrom()).seedIndex();
        int to = worlds.worlds().locate(event.getTo()).seedIndex();
        if (from != to) {
            offer(event.getPlayer(), to);
        }
    }
}
