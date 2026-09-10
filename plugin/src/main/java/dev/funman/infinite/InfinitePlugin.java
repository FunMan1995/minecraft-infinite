package dev.funman.infinite;

import dev.funman.infinite.command.InfiniteCommands;
import dev.funman.infinite.config.InfiniteConfig;
import dev.funman.infinite.game.CombatTag;
import dev.funman.infinite.game.DragonGate;
import dev.funman.infinite.game.GraveService;
import dev.funman.infinite.game.HomeStore;
import dev.funman.infinite.game.HubGuard;
import dev.funman.infinite.game.SpawnListener;
import dev.funman.infinite.game.TpaService;
import dev.funman.infinite.listener.BlackoutListener;
import dev.funman.infinite.listener.BorderCrossListener;
import dev.funman.infinite.listener.VanillaPortalListener;
import dev.funman.infinite.see.ChunkProjector;
import dev.funman.infinite.see.NmsChunkSender;
import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.stack.Topology;
import dev.funman.infinite.worldgen.BlankChunkGenerator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfinitePlugin extends JavaPlugin {
    private InfiniteApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        InfiniteConfig config = InfiniteConfig.load(getConfig());
        Topology topology = new Topology(config);
        StackWorlds worlds = new StackWorlds(this, config);
        api = new InfiniteApi(config, topology, worlds);
        getServer().getServicesManager().register(InfiniteApi.class, api, this, ServicePriority.Normal);

        DragonGate dragonGate = new DragonGate(this, worlds);
        CombatTag combat = new CombatTag(worlds, config.combatTagSeconds());
        HubGuard hub = new HubGuard(worlds);
        HomeStore homes = new HomeStore(this, worlds, config.maxHomes());
        TpaService tpa = new TpaService(combat, config.tpaTimeoutSeconds());

        getServer().getPluginManager().registerEvents(new BorderCrossListener(this, topology, worlds, dragonGate), this);
        getServer().getPluginManager().registerEvents(new VanillaPortalListener(topology, worlds), this);
        getServer().getPluginManager().registerEvents(new BlackoutListener(worlds), this);
        getServer().getPluginManager().registerEvents(dragonGate, this);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(new GraveService(this), this);
        getServer().getPluginManager().registerEvents(new SpawnListener(this, worlds), this);
        getServer().getPluginManager().registerEvents(hub, this);

        NmsChunkSender sender = new NmsChunkSender(getLogger());
        InfiniteCommands.register(this, api, combat, homes, tpa);

        getServer().getScheduler().runTask(this, () -> {
            worlds.attachDefaultWorlds();
            hub.start(this);
            new ChunkProjector(this, topology, worlds, sender).start();
            getLogger().info("Minecraft Infinite: seed 0 is one vanilla save (overworld+nether+end). Hub is blank.");
        });
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if ("blank".equalsIgnoreCase(id)) {
            return new BlankChunkGenerator();
        }
        var parsed = StackWorlds.parseWorldName(worldName);
        if (parsed != null && parsed.seedIndex() == 0) {
            return new BlankChunkGenerator();
        }
        return null;
    }

    public InfiniteApi api() {
        return api;
    }
}
