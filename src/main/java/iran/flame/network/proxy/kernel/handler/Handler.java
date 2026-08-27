package iran.flame.network.proxy.kernel.handler;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import iran.flame.network.proxy.Main;
import iran.flame.network.proxy.database.redis.Redis;
import iran.flame.network.proxy.commands.GamemodeCommand;
import iran.flame.network.proxy.utils.SchedulerUtil;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;

public class Handler {
    private static final String CHANNEL = "fnw";
    private static final String RESOLVE_CHANNEL = "fnw:resolve";
    private static final String DELIM = "\u0001";
    private final com.google.common.util.concurrent.Striped<Lock> serverLocks = com.google.common.util.concurrent.Striped.lock(64);
    private final com.google.common.util.concurrent.Striped<Lock> gamemodeLocks = com.google.common.util.concurrent.Striped.lock(64);    private final Main plugin;
    private final ProxyServer proxyServer;
    private final Redis redis;
    private final Logger logger;
    private final CommandManager commandManager;

    public Handler(Main plugin) {
        this.plugin = plugin;
        this.proxyServer = plugin.getServer();
        this.redis = plugin.getRedis();
        this.logger = plugin.getLogger();
        this.commandManager = proxyServer.getCommandManager();
    }

    public Handler init() {
        restoreFromRedis();
        redis.subscribe(CHANNEL, this::onMessage);
        startStaleServerCheck();
        return this;
    }

    private void startStaleServerCheck() {
        SchedulerUtil.runTimer(proxyServer, plugin, this::pruneStaleServers, 60L, 60L);
    }

    private Lock lockFor(String serverName) {
        return serverLocks.get(serverName);
    }

    private Lock lockForGamemode(String gamemode) {
        return gamemodeLocks.get(gamemode);
    }

    private void withGamemodeLocks(String gamemodeA, String gamemodeB, Runnable action) {
        if (gamemodeA == null && gamemodeB == null) {
            action.run();
            return;
        }
        if (gamemodeA == null || gamemodeA.equals(gamemodeB)) {
            Lock lock = lockForGamemode(gamemodeB);
            lock.lock();
            try { action.run(); } finally { lock.unlock(); }
            return;
        }
        if (gamemodeB == null) {
            Lock lock = lockForGamemode(gamemodeA);
            lock.lock();
            try { action.run(); } finally { lock.unlock(); }
            return;
        }

        String first = gamemodeA.compareTo(gamemodeB) <= 0 ? gamemodeA : gamemodeB;
        String second = gamemodeA.compareTo(gamemodeB) <= 0 ? gamemodeB : gamemodeA;

        Lock lock1 = lockForGamemode(first);
        Lock lock2 = lockForGamemode(second);
        lock1.lock();
        try {
            lock2.lock();
            try { action.run(); } finally { lock2.unlock(); }
        } finally {
            lock1.unlock();
        }
    }

    private void pruneStaleServers() {
        Set<String> gamemodeKeys = new java.util.HashSet<>();
        try (Jedis jedis = redis.getResource()) {
            if (jedis == null) return;
            String cursor = "0";
            ScanParams params = new ScanParams().match(gamemodeKey("*")).count(100);
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                cursor = result.getCursor();
                gamemodeKeys.addAll(result.getResult());
            } while (!"0".equals(cursor));
        } catch (Exception e) {
            logger.error("Error scanning gamemode keys for prune: {}", e.getMessage(), e);
            return;
        }

        for (String gamemodeKeyStr : gamemodeKeys) {
            String modeName = gamemodeKeyStr.substring(gamemodeKey("").length());

            Set<String> members;
            try (Jedis jedis = redis.getResource()) {
                if (jedis == null) continue;
                members = jedis.smembers(gamemodeKeyStr);
            } catch (Exception e) {
                logger.error("Error reading members for gamemode {}: {}", modeName, e.getMessage(), e);
                continue;
            }
            if (members == null || members.isEmpty()) continue;

            List<String> staleServers = new java.util.ArrayList<>();
            try (Jedis jedis = redis.getResource()) {
                if (jedis == null) continue;
                for (String serverName : members) {
                    if (!jedis.exists(serverKey(serverName))) {
                        staleServers.add(serverName);
                    }
                }
            } catch (Exception e) {
                logger.error("Error checking stale servers for gamemode {}: {}", modeName, e.getMessage(), e);
                continue;
            }
            if (staleServers.isEmpty()) continue;

            try (Jedis jedis = redis.getResource()) {
                if (jedis == null) continue;
                for (String serverName : staleServers) {
                    Lock lock = lockFor(serverName);
                    lock.lock();
                    try {
                        if (!jedis.exists(serverKey(serverName))) {
                            proxyServer.getServer(serverName).ifPresent(rs ->
                                    proxyServer.unregisterServer(rs.getServerInfo()));

                            withGamemodeLocks(modeName, null, () -> {
                                if (jedis.srem(gamemodeKeyStr, serverName) > 0) {
                                    handlePossibleCommandRemoval(jedis, modeName);
                                }
                            });

                            logger.warn("Pruned stale server {} (expired in Redis, no heartbeat)", serverName);
                        }
                    } catch (Exception e) {
                        logger.error("Error pruning server {}: {}", serverName, e.getMessage(), e);
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
    }

    private void restoreFromRedis() {
        Jedis jedis = redis.getResource();
        if (jedis == null) {
            logger.error("Redis unavailable, could not restore registered servers on startup");
            return;
        }

        int restored = 0;
        try (jedis) {
            String cursor = "0";
            ScanParams params = new ScanParams().match("fnw:server:*").count(100);

            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                cursor = result.getCursor();

                for (String key : result.getResult()) {
                    Map<String, String> data = jedis.hgetAll(key);
                    if (data == null || data.isEmpty()) continue;

                    String serverName = key.substring("fnw:server:".length());
                    String ip = data.get("ip");
                    String portRaw = data.get("port");
                    String gamemode = data.get("gamemode");

                    if (ip == null || portRaw == null || gamemode == null) {
                        logger.warn("Skipping malformed server entry for key {}", key);
                        continue;
                    }

                    int port;
                    try {
                        port = Integer.parseInt(portRaw);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid port '{}' for server {} during restore", portRaw, serverName);
                        continue;
                    }

                    InetSocketAddress address = new InetSocketAddress(ip, port);
                    ServerInfo serverInfo = new ServerInfo(serverName, address);

                    proxyServer.getServer(serverName).ifPresent(rs -> proxyServer.unregisterServer(rs.getServerInfo()));
                    proxyServer.registerServer(serverInfo);
                    withGamemodeLocks(gamemode, null, () -> {
                        jedis.sadd(gamemodeKey(gamemode), serverName);
                        ensureCommandRegistered(gamemode);
                    });

                    restored++;
                }
            } while (!"0".equals(cursor));
        } catch (Exception e) {
            logger.error("Error restoring registered servers from Redis: {}", e.getMessage(), e);
        }

        logger.info("Restored {} server(s) from Redis on startup", restored);
    }

    private void onMessage(String channel, String message) {
        if (message == null || message.isEmpty()) return;

        String[] parts = message.split(DELIM, -1);
        if (parts.length < 2 || !"FNW".equalsIgnoreCase(parts[0])) return;

        String command = parts[1].toUpperCase();

        try {
            switch (command) {
                case "REGISTER" -> handleRegister(parts);
                case "UNREGISTER" -> handleUnregister(parts);
                case "CONNECT" -> handleConnect(parts);
                default -> logger.warn("Unknown FNW command: {}", command);
            }
        } catch (Exception e) {
            logger.error("Error handling message '{}': {}", message, e.getMessage(), e);
        }
    }

    private void handleRegister(String[] parts) {
        if (parts.length < 7) {
            logger.warn("Invalid REGISTER payload, expected 7 parts, got {}", parts.length);
            return;
        }

        String ip = parts[2];
        if (ip.isEmpty()) {
            logger.warn("Invalid empty ip for server {}", parts[6]);
            return;
        }
        String portRaw = parts[3];
        String gamemode = parts[4];
        String serverType = parts[5];
        String serverName = parts[6];

        int port;
        try {
            port = Integer.parseInt(portRaw);
            if (port < 0 || port > 65535) throw new NumberFormatException("out of range");
        } catch (NumberFormatException e) {
            logger.error("Invalid port '{}' for server {}", portRaw, serverName);
            return;
        }

        Jedis jedis = redis.getResource();

        Lock lock = lockFor(serverName);
        lock.lock();
        try {
            try (jedis) {
                if (jedis == null) {
                    logger.error("Redis unavailable, could not process register for {}", serverName);
                    return;
                }
                Map<String, String> oldData = jedis.hgetAll(serverKey(serverName));
                boolean existed = oldData != null && !oldData.isEmpty();

                String oldPort = existed ? oldData.get("port") : null;
                String oldGamemode = existed ? oldData.get("gamemode") : null;

                String oldIp = existed ? oldData.get("ip") : null;
                boolean portChanged = !existed || !portRaw.equals(oldPort) || !ip.equals(oldIp);
                boolean gamemodeChanged = !existed || !gamemode.equals(oldGamemode);

                InetSocketAddress address = new InetSocketAddress(ip, port);
                ServerInfo serverInfo = new ServerInfo(serverName, address);

                if (portChanged) {
                    proxyServer.getServer(serverName).ifPresent(rs -> proxyServer.unregisterServer(rs.getServerInfo()));
                    proxyServer.registerServer(serverInfo);
                }

                withGamemodeLocks(oldGamemode, gamemode, () -> {
                    if (gamemodeChanged && oldGamemode != null) {
                        jedis.srem(gamemodeKey(oldGamemode), serverName);
                        handlePossibleCommandRemoval(jedis, oldGamemode);
                    }

                    if (gamemodeChanged || portChanged) {
                        jedis.sadd(gamemodeKey(gamemode), serverName);
                        ensureCommandRegistered(gamemode);
                    }
                });

                Map<String, String> data = new HashMap<>();
                data.put("ip", ip);
                data.put("port", portRaw);
                data.put("gamemode", gamemode);
                data.put("type", serverType);
                jedis.hset(serverKey(serverName), data);
                jedis.expire(serverKey(serverName), 90L);

                if (!existed) {
                    logger.info("Registered server {} ({}:{}) gamemode={} type={}", serverName, ip, port, gamemode, serverType);
                } else if (portChanged || gamemodeChanged) {
                    logger.info("Updated server {} ({}:{}) gamemode={} type={} (portChanged={}, gamemodeChanged={})",
                            serverName, ip, port, gamemode, serverType, portChanged, gamemodeChanged);
                } else {
                    logger.debug("Heartbeat received for server {} (no changes)", serverName);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void handleUnregister(String[] parts) {
        if (parts.length < 3) {
            logger.warn("Invalid UNREGISTER payload, expected 3 parts, got {}", parts.length);
            return;
        }

        String serverName = parts[2];

        Jedis jedis = redis.getResource();
        String gamemode;
        Lock lock = lockFor(serverName);
        lock.lock();
        try {
            if (jedis != null) {
                try {
                    Map<String, String> data = jedis.hgetAll(serverKey(serverName));
                    gamemode = (data != null) ? data.get("gamemode") : null;
                    jedis.del(serverKey(serverName));
                    if (gamemode != null) {
                        String finalGamemode = gamemode;
                        withGamemodeLocks(finalGamemode, null, () -> {
                            jedis.srem(gamemodeKey(finalGamemode), serverName);
                            handlePossibleCommandRemoval(jedis, finalGamemode);
                        });
                    }
                } finally {
                    jedis.close();
                }
            } else {
                logger.error("Redis unavailable, could not clean up Redis entries for {}", serverName);
            }
        } finally {
            lock.unlock();
        }

        Optional<RegisteredServer> registered = proxyServer.getServer(serverName);
        if (registered.isPresent()) {
            proxyServer.unregisterServer(registered.get().getServerInfo());
            logger.info("Unregistered server {}", serverName);
        } else {
            logger.warn("Tried to unregister {} but it was not registered.", serverName);
        }
    }

    private void handleConnect(String[] parts) {
        if (parts.length < 5) {
            logger.warn("Invalid CONNECT payload, expected 5 parts, got {}", parts.length);
            return;
        }

        String username = parts[2];
        String rawUuid = parts[3];
        String gamemode = parts[4];

        Jedis jedis = redis.getResource();
        if (jedis == null) {
            logger.error("Redis unavailable, could not resolve CONNECT for {} ({})", username, gamemode);
            return;
        }

        String serverName;
        try (jedis) {
            Set<String> candidates = jedis.smembers(gamemodeKey(gamemode));
            if (candidates == null || candidates.isEmpty()) {
                logger.warn("No servers available for gamemode {} (requested by {})", gamemode, username);
                redis.publish(RESOLVE_CHANNEL, "RESOLVE" + DELIM + rawUuid + DELIM + "NONE");
                return;
            }
            List<String> list = new java.util.ArrayList<>(candidates);
            java.util.Collections.shuffle(list, ThreadLocalRandom.current());

            String chosen = null;
            for (String candidate : list) {
                if (jedis.exists(serverKey(candidate))) {
                    chosen = candidate;
                    break;
                }

                Lock candidateLock = lockFor(candidate);
                candidateLock.lock();
                try {
                    if (jedis.exists(serverKey(candidate))) {
                        chosen = candidate;
                        break;
                    }
                    withGamemodeLocks(gamemode, null, () -> {
                        if (jedis.srem(gamemodeKey(gamemode), candidate) > 0) {
                            handlePossibleCommandRemoval(jedis, gamemode);
                        }
                    });
                } finally {
                    candidateLock.unlock();
                }
            }

            if (chosen == null) {
                logger.warn("No live servers available for gamemode {} (requested by {})", gamemode, username);
                redis.publish(RESOLVE_CHANNEL, "RESOLVE" + DELIM + rawUuid + DELIM + "NONE");
                return;
            }
            serverName = chosen;
        }

        redis.publish(RESOLVE_CHANNEL, "RESOLVE" + DELIM + rawUuid + DELIM + serverName);
        logger.info("Resolved CONNECT for {} ({}) -> gamemode={} server={}", username, rawUuid, gamemode, serverName);
    }

    private void ensureCommandRegistered(String gamemode) {
        String name = gamemode.toLowerCase();

        if (commandManager.hasCommand(name)) return;

        CommandMeta meta = commandManager.metaBuilder(name)
                .plugin(plugin)
                .build();

        GamemodeCommand command = new GamemodeCommand(proxyServer, redis, logger, name);
        commandManager.register(meta, command);

        logger.info("Registered gamemode command /{}", name);
    }

    private void handlePossibleCommandRemoval(Jedis jedis, String gamemode) {
        String name = gamemode.toLowerCase();

        long remaining = jedis.scard(gamemodeKey(gamemode));
        if (remaining == 0 && commandManager.hasCommand(name)) {
            commandManager.unregister(name);
            logger.info("Unregistered gamemode command /{} (no servers left)", name);
        }
    }

    private String serverKey(String serverName) {
        return "fnw:server:" + serverName;
    }

    private String gamemodeKey(String gamemode) {
        return "fnw:gamemode:" + gamemode;
    }
}