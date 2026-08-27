package iran.flame.network.proxy.listeners;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import iran.flame.network.proxy.Main;
import iran.flame.network.proxy.enums.LimboType;
import iran.flame.network.proxy.kernel.balancer.Balancer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ConnectListener {
    private static final String LIMBO_SERVER_NAME = "limbo";
    private final ProxyServer server;
    private final Main plugin;
    private final Balancer balancer;

    public ConnectListener(ProxyServer server, Main plugin) {
        this.server = server;
        this.plugin = plugin;
        this.balancer = new Balancer(server, plugin.getRedis(), plugin.getLogger());
    }

    @Subscribe
    public EventTask onInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        return EventTask.resumeWhenComplete(
                resolveServer(uuid).thenAccept(chosen -> {
                    chosen.ifPresent(event::setInitialServer);

                    if (chosen.isEmpty()) {
                        plugin.getLogger().warn("No server available (lobby/limbo) for player {}", uuid);
                        player.disconnect(net.kyori.adventure.text.Component.text("No server available."));
                        return;
                    }

                    if (chosen.get().getServerInfo().getName().equals(LIMBO_SERVER_NAME)) {
                        plugin.getLimbo().publish(LimboType.EMPTY, uuid);
                    }
                })
        );
    }

    private CompletableFuture<Optional<RegisteredServer>> resolveServer(UUID uuid) {
        return findLastServerAsync(uuid).thenCompose(chosen -> {
            if (chosen.isPresent()) return CompletableFuture.completedFuture(chosen);
            Optional<RegisteredServer> lobby = balancer.pickLobby();
            if (lobby.isPresent()) return CompletableFuture.completedFuture(lobby);
            Optional<RegisteredServer> limbo = server.getServer(LIMBO_SERVER_NAME);
            return isOnlineAsync(limbo).thenApply(limboOnline -> limboOnline ? limbo : Optional.empty());
        });
    }

    private CompletableFuture<Optional<RegisteredServer>> findLastServerAsync(UUID uuid) {
        Map<UUID, String> lastServers = plugin.getLastServers();
        String lastServerName = lastServers.get(uuid);
        if (lastServerName == null) return CompletableFuture.completedFuture(Optional.empty());

        Optional<RegisteredServer> registered = server.getServer(lastServerName);
        if (registered.isEmpty()) {
            lastServers.remove(uuid);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return registered.get().ping()
                .orTimeout(2, TimeUnit.SECONDS)
                .handle((ping, err) -> {
                    if (err != null) {
                        lastServers.remove(uuid);
                        return Optional.empty();
                    }
                    return registered;
                });
    }

    private CompletableFuture<Boolean> isOnlineAsync(Optional<RegisteredServer> serverOpt) {
        return serverOpt.map(registeredServer -> registeredServer.ping()
                .orTimeout(2, TimeUnit.SECONDS)
                .handle((ping, err) -> err == null)).orElseGet(() -> CompletableFuture.completedFuture(false));
    }
}