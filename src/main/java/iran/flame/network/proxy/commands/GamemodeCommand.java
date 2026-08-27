package iran.flame.network.proxy.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import iran.flame.network.proxy.database.redis.Redis;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import java.util.Optional;
import java.util.Set;

public class GamemodeCommand implements SimpleCommand {
    private final ProxyServer proxyServer;
    private final Redis redis;
    private final Logger logger;
    private final String gamemode;

    public GamemodeCommand(ProxyServer proxyServer, Redis redis, Logger logger, String gamemode) {
        this.proxyServer = proxyServer;
        this.redis = redis;
        this.logger = logger;
        this.gamemode = gamemode;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }

        Set<String> serverNames = fetchServersForGamemode();

        if (serverNames == null || serverNames.isEmpty()) {
            player.sendMessage(Component.text("Server \"" + gamemode + "\" is currently offline.", NamedTextColor.RED));
            return;
        }

        Optional<RegisteredServer> target = pickBestServer(serverNames);

        if (target.isEmpty()) {
            player.sendMessage(Component.text("Server \"" + gamemode + "\" is currently offline.", NamedTextColor.RED));
            return;
        }

        player.createConnectionRequest(target.get()).fireAndForget();
    }

    private Set<String> fetchServersForGamemode() {
        Jedis jedis = redis.getResource();

        try (jedis) {
            if (jedis == null) {
                logger.error("Redis unavailable while resolving gamemode {}", gamemode);
                return null;
            }
            return jedis.smembers(gamemodeKey(gamemode));
        }
    }

    private Optional<RegisteredServer> pickBestServer(Set<String> serverNames) {
        RegisteredServer best = null;
        int bestCount = Integer.MAX_VALUE;

        for (String name : serverNames) {
            Optional<RegisteredServer> registered = proxyServer.getServer(name);
            if (registered.isEmpty()) continue;

            RegisteredServer candidate = registered.get();
            int playerCount = candidate.getPlayersConnected().size();

            if (playerCount < bestCount) {
                bestCount = playerCount;
                best = candidate;
            }
        }

        return Optional.ofNullable(best);
    }

    private String gamemodeKey(String gamemode) {
        return "fnw:gamemode:" + gamemode;
    }

    @Override public boolean hasPermission(Invocation invocation) {
        return true;
    }

    public String getGamemode() {
        return gamemode;
    }
}