package iran.flame.network.proxy.kernel.limbo;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import iran.flame.network.proxy.database.redis.Redis;
import iran.flame.network.proxy.enums.LimboType;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Limbo {
    private static final String REDIS_CHANNEL = "fnw:limbo";
    private static final String AFK_CHANNEL = "fnw:afk";
    private static final String LIMBO_SERVER_NAME = "limbo";

    private final ProxyServer server;
    private final Redis redis;
    private final ConcurrentHashMap<UUID, RegisteredServer> previousServers = new ConcurrentHashMap<>();

    public Limbo(ProxyServer server, Redis redis) {
        this.server = server;
        this.redis = redis;
        listen();
        listenAfk();
    }

    private void listen() {
        redis.subscribe(REDIS_CHANNEL, (channel, message) -> {
            int separator = message.indexOf(':');
            if (separator == -1) return;

            String type = message.substring(0, separator);
            String rawUuid = message.substring(separator + 1);

            if (!type.equals("RETURN")) return;

            try {
                UUID uuid = UUID.fromString(rawUuid);
                server.getPlayer(uuid).ifPresent(this::sendToLastServer);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    private void listenAfk() {
        redis.subscribe(AFK_CHANNEL, (channel, message) -> {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;

            String type = parts[0];
            String rawUuid = parts[2];

            if (!type.equals("AFK")) return;

            try {
                UUID uuid = UUID.fromString(rawUuid);
                Optional<Player> playerOpt = server.getPlayer(uuid);
                if (playerOpt.isEmpty()) return;

                Player player = playerOpt.get();
                Optional<RegisteredServer> limboServerOpt = server.getServer(LIMBO_SERVER_NAME);
                if (limboServerOpt.isEmpty()) return;

                send(player, limboServerOpt.get(), LimboType.AFK);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    public void send(Player player, RegisteredServer limboServer, LimboType type) {
        player.getCurrentServer()
                .map(ServerConnection::getServer)
                .ifPresent(s -> previousServers.put(player.getUniqueId(), s));

        player.createConnectionRequest(limboServer).fireAndForget();

        publish(type, player.getUniqueId());
    }

    public void publish(LimboType type, UUID uuid) {
        redis.publish(REDIS_CHANNEL, type.name() + ":" + uuid);
    }

    public void removePlayer(UUID uuid) {
        previousServers.remove(uuid);
    }

    private void sendToLastServer(Player player) {
        RegisteredServer previous = previousServers.remove(player.getUniqueId());
        if (previous != null) {
            player.createConnectionRequest(previous).fireAndForget();
        }
    }
}