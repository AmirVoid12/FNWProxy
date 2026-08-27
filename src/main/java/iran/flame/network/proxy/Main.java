package iran.flame.network.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import iran.flame.network.proxy.database.config.Config;
import iran.flame.network.proxy.database.redis.Redis;
import iran.flame.network.proxy.kernel.alias.Alias;
import iran.flame.network.proxy.kernel.handler.Handler;
import iran.flame.network.proxy.kernel.limbo.Limbo;
import iran.flame.network.proxy.listeners.ConnectListener;
import iran.flame.network.proxy.listeners.DisconnectListener;
import iran.flame.network.proxy.listeners.KickListener;
import org.slf4j.Logger;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "flameproxy",
        name = "FNWProxy",
        version = "0.1",
        authors = {"AmirVoid12"}
)
public class Main {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private Redis redis;
    private Limbo limbo;
    private Handler handler;
    private final Map<UUID, String> lastServers = new ConcurrentHashMap<>();

    @Inject
    public Main(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Config config = new Config(dataDirectory);

        redis = new Redis(
                config.getString("database.redis.host", "127.0.0.1"),
                config.getInt("database.redis.port", 6379),
                config.getString("database.redis.user", ""),
                config.getString("database.redis.password", ""),
                10,
                3000,
                logger
        );

        handler = new Handler(this).init();

        this.limbo = new Limbo(server, redis);

        Alias alias = new Alias(server, this);
        //alias.register(new AccountCommand());

        server.getEventManager().register(this, new ConnectListener(server, this));
        server.getEventManager().register(this, new DisconnectListener(limbo));
        server.getEventManager().register(this, new KickListener(this));

        logger.info("FNWProxy loaded.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (redis != null) redis.close();
        logger.info("FNWProxy unloaded.");
    }

    public Map<UUID, String> getLastServers() {
        return lastServers;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Redis getRedis() {
        return redis;
    }

    public Limbo getLimbo() {
        return limbo;
    }

    public Handler getHandler() {
        return handler;
    }
}