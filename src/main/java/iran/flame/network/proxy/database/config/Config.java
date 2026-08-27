package iran.flame.network.proxy.database.config;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Config {
    private Map<String, Object> data;

    public Config(Path dataDirectory) {
        load(dataDirectory);
    }

    private void load(Path dataDirectory) {
        try {
            Path configPath = dataDirectory.resolve("config.yml");

            if (!Files.exists(configPath)) {
                Files.createDirectories(dataDirectory);
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    assert in != null;
                    Files.copy(in, configPath);
                }
            }

            try (InputStream in = Files.newInputStream(configPath)) {
                this.data = new Yaml().load(in);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.yml: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolve(String path) {
        String[] keys = path.split("\\.");
        Map<String, Object> current = data;

        for (int i = 0; i < keys.length - 1; i++) {
            Object next = current.get(keys[i]);
            if (!(next instanceof Map)) return null;
            current = (Map<String, Object>) next;
        }

        return current.get(keys[keys.length - 1]);
    }

    public String getString(String path) {
        Object val = resolve(path);
        return val != null ? val.toString() : null;
    }

    public String getString(String path, String fallback) {
        String val = getString(path);
        return val != null ? val : fallback;
    }

    public int getInt(String path) {
        Object val = resolve(path);
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    public int getInt(String path, int fallback) {
        Object val = resolve(path);
        if (val instanceof Number) return ((Number) val).intValue();
        return fallback;
    }

    public boolean getBoolean(String path) {
        Object val = resolve(path);
        if (val instanceof Boolean) return (Boolean) val;
        return false;
    }

    public boolean getBoolean(String path, boolean fallback) {
        Object val = resolve(path);
        if (val instanceof Boolean) return (Boolean) val;
        return fallback;
    }
}