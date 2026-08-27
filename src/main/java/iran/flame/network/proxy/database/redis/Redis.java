package iran.flame.network.proxy.database.redis;

import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class Redis {
    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final int maxRetries;
    private final long retryDelayMs;
    private final Logger logger;
    private JedisPool pool;
    private JedisPubSub activePubSub;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger attempts = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public Redis(String host, int port, String user, String password, int maxRetries, long retryDelayMs, Logger logger) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.logger = logger;
        initPool();
    }

    private void initPool() {
        try {
            if (pool != null && !pool.isClosed()) pool.close();

            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(10);
            config.setMaxIdle(5);
            config.setMinIdle(1);
            config.setTestOnBorrow(true);
            config.setTestOnReturn(true);
            config.setTestWhileIdle(true);
            config.setBlockWhenExhausted(true);

            boolean hasAuth = password != null && !password.isEmpty();

            if (hasAuth) {
                String resolvedUser = (user != null && !user.isEmpty()) ? user : null;
                this.pool = new JedisPool(config, host, port, 2000, resolvedUser, password);
            } else {
                this.pool = new JedisPool(config, host, port);
            }

            connected.set(true);
            logger.info("Connected to {}:{}", host, port);
        } catch (Exception e) {
            connected.set(false);
            logger.error("Failed to init pool: {}", e.getMessage(), e);
        }
    }

    private boolean isPoolAlive() {
        return pool != null && !pool.isClosed();
    }

    public void publish(String channel, String message) {
        if (channel == null || message == null) return;
        if (!isPoolAlive()) {
            logger.warn("Publish failed — pool is not available.");
            return;
        }

        try (Jedis jedis = pool.getResource()) {
            if (jedis == null) return;
            jedis.publish(channel, message);
        } catch (JedisException e) {
            logger.error("Publish error: {}", e.getMessage(), e);
        }
    }

    public void subscribe(String channel, BiConsumer<String, String> handler) {
        if (channel == null || handler == null) return;

        Thread thread = new Thread(() -> {
            while (running.get()) {
                if (!isPoolAlive()) {
                    logger.warn("Pool unavailable, reinitializing...");
                    initPool();
                }

                try (Jedis jedis = pool.getResource()) {
                    if (jedis == null) throw new JedisConnectionException("Jedis resource is null");

                    activePubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String ch, String message) {
                            if (ch == null || message == null) return;
                            try {
                                handler.accept(ch, message);
                            } catch (Exception e) {
                                logger.error("Handler error on channel {}: {}", ch, e.getMessage(), e);
                            }
                        }

                        @Override
                        public void onSubscribe(String ch, int subscribedChannels) {
                            connected.set(true);
                            attempts.set(0);
                            logger.info("Subscribed to channel: {}", ch);
                        }

                        @Override
                        public void onUnsubscribe(String ch, int subscribedChannels) {
                            logger.warn("Unsubscribed from channel: {}", ch);
                        }
                    };

                    jedis.subscribe(activePubSub, channel);

                } catch (JedisConnectionException e) {
                    connected.set(false);
                    int attempt = attempts.incrementAndGet();
                    logger.error("Connection lost (attempt {}/{}): {}", attempt, maxRetries, e.getMessage(), e);

                    if (attempt >= maxRetries) {
                        logger.error("Max retries reached. Stopping subscriber.");
                        break;
                    }

                    try {
                        Thread.sleep(retryDelayMs * Math.min(attempt, 5));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry sleep interrupted: {}", ie.getMessage(), ie);
                        break;
                    }

                } catch (Exception e) {
                    logger.error("Unexpected subscriber error: {}", e.getMessage(), e);
                    break;
                }
            }
        });

        thread.setName("Redis-Subscriber");
        thread.setDaemon(true);
        thread.start();

        scheduler.scheduleAtFixedRate(this::healthCheck, 30, 30, TimeUnit.SECONDS);
    }

    private void healthCheck() {
        if (!running.get()) return;
        if (!isPoolAlive()) {
            logger.warn("Health check failed — reinitializing pool...");
            initPool();
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            if (jedis == null || !"PONG".equals(jedis.ping())) {
                logger.warn("Ping failed — connection may be broken.");
                connected.set(false);
            }
        } catch (Exception e) {
            connected.set(false);
            logger.error("Health check error: {}", e.getMessage(), e);
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public Jedis getResource() {
        if (!isPoolAlive()) return null;
        try {
            return pool.getResource();
        } catch (Exception e) {
            logger.error("getResource error: {}", e.getMessage(), e);
            return null;
        }
    }

    public void close() {
        running.set(false);
        scheduler.shutdownNow();

        if (activePubSub != null && activePubSub.isSubscribed()) {
            try {
                activePubSub.unsubscribe();
            } catch (Exception e) {
                logger.error("Error while unsubscribing: {}", e.getMessage(), e);
            }
        }

        if (isPoolAlive()) {
            try {
                pool.close();
            } catch (Exception e) {
                logger.error("Error while closing pool: {}", e.getMessage(), e);
            }
        }

        logger.info("Shut down cleanly.");
    }
}