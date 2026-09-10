package dev.funman.infinite.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class TpaService {
    public record Request(UUID from, UUID to, long expiresAt) {}

    private final CombatTag combat;
    private final int timeoutSeconds;
    private final Map<UUID, Request> inbound = new ConcurrentHashMap<>();

    public TpaService(CombatTag combat, int timeoutSeconds) {
        this.combat = combat;
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean request(Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage(Component.text("You cannot TPA to yourself.", NamedTextColor.RED));
            return false;
        }
        if (combat.denyIfTagged(from, "TPA")) {
            return false;
        }
        inbound.put(to.getUniqueId(), new Request(
                from.getUniqueId(),
                to.getUniqueId(),
                System.currentTimeMillis() + timeoutSeconds * 1000L
        ));
        from.sendMessage(Component.text("TPA sent to " + to.getName() + ".", NamedTextColor.GREEN));
        to.sendMessage(Component.text(from.getName() + " requested to teleport to you. /tpaccept or /tpdeny", NamedTextColor.YELLOW));
        return true;
    }

    public Request take(Player target) {
        Request req = inbound.remove(target.getUniqueId());
        if (req == null || req.expiresAt() < System.currentTimeMillis()) {
            return null;
        }
        return req;
    }

    public CombatTag combat() {
        return combat;
    }
}
