package dev.funman.infinite;

import dev.funman.infinite.command.InfiniteCommands;
import dev.funman.infinite.config.InfiniteConfig;
import dev.funman.infinite.listener.BlackoutListener;
import dev.funman.infinite.listener.BorderCrossListener;
import dev.funman.infinite.listener.VanillaPortalListener;
import dev.funman.infinite.see.ChunkProjector;
import dev.funman.infinite.see.NmsChunkSender;
import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.stack.Topology;
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
        worlds.attachDefaultWorlds();
        api = new InfiniteApi(config, topology, worlds);
        getServer().getServicesManager().register(InfiniteApi.class, api, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new BorderCrossListener(this, topology, worlds), this);
        getServer().getPluginManager().registerEvents(new VanillaPortalListener(topology, worlds), this);
        getServer().getPluginManager().registerEvents(new BlackoutListener(worlds), this);

        NmsChunkSender sender = new NmsChunkSender(getLogger());
        new ChunkProjector(this, topology, worlds, sender).start();

        InfiniteCommands.register(this, api);
        getLogger().info("Minecraft Infinite stack online. /infinite for position.");
    }

    public InfiniteApi api() {
        return api;
    }
}
