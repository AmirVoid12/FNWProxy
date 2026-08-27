package iran.flame.network.proxy.kernel.balancer;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import iran.flame.network.proxy.database.redis.Redis;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class Balancer {
    private static final String[] LOBBY_GAMEMODES = {"lobby-1", "lobby-2"};
    private final ProxyServer server;
    private final Redis redis;
    private final Logger logger;

    public Balancer(ProxyServer server, Redis redis, Logger logger) {
        this.server = server;
        this.redis = redis;
        this.logger = logger;
    }

    public Optional<RegisteredServer> pickLobby() {
        Set<String> candidates = fetchLobbyServerNames();

        RegisteredServer best = null;
        int bestCount = Integer.MAX_VALUE;

        for (String name : candidates) {
            Optional<RegisteredServer> registered = server.getServer(name);
            if (registered.isEmpty()) continue;

            RegisteredServer candidate = registered.get();
            if (!isOnline(candidate)) continue;

            int playerCount = candidate.getPlayersConnected().size();
            if (playerCount < bestCount) {
                bestCount = playerCount;
                best = candidate;
            }
        }

        return Optional.ofNullable(best);
    }

    private Set<String> fetchLobbyServerNames() {
        Set<String> names = new HashSet<>();

        Jedis jedis = redis.getResource();

        try (jedis) {
            if (jedis == null) {
                logger.error("Redis unavailable while resolving lobby servers");
                return names;
            }
            for (String gamemode : LOBBY_GAMEMODES) {
                Set<String> members = jedis.smembers(gamemodeKey(gamemode));
                if (members != null) {
                    names.addAll(members);
                }
            }
        }

        return names;
    }

    private boolean isOnline(RegisteredServer registeredServer) {
        try {
            registeredServer.ping().join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String gamemodeKey(String gamemode) {
        return "fnw:gamemode:" + gamemode;
    }
}