package com.meowantixray.metrics;

import com.meowantixray.compat.MinecraftCompat;
import com.meowantixray.platform.PlatformHelper;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import net.minecraft.server.MinecraftServer;

/** Minimal bStats v2 client shared by the Fabric and NeoForge entry points. */
public final class BStatsMetrics {
    private static final String REPORT_URL = "https://bStats.org/api/v2/data/%s";
    private static final long PERIOD_MINUTES = 30L;
    private static final Object LOCK = new Object();
    private static ScheduledExecutorService scheduler;
    private static MinecraftServer server;
    private static String platform;
    private static String serverUuid;
    private static boolean enabled;
    private static int serviceId;
    private static final AtomicInteger playerCount = new AtomicInteger();

    private BStatsMetrics() {
    }

    public static void start(MinecraftServer minecraftServer, String loaderPlatform) {
        synchronized (LOCK) {
            if (scheduler != null) {
                return;
            }
            server = minecraftServer;
            platform = loaderPlatform;
            Properties config = loadConfig();
            enabled = Boolean.parseBoolean(config.getProperty("enabled", "true"));
            serviceId = parseServiceId(config.getProperty("service-id", "0"));
            serverUuid = config.getProperty("server-uuid");
            if (serverUuid == null || serverUuid.isBlank()) {
                serverUuid = UUID.randomUUID().toString();
                config.setProperty("server-uuid", serverUuid);
                saveConfig(config);
            }
            if (!enabled || serviceId <= 0) {
                MeowAntiXrayMetricsLog.info(serviceId <= 0
                    ? "bStats metrics disabled until config/meowantixray-bstats.properties has a valid service-id."
                    : "bStats metrics disabled in config.");
                return;
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "MeowAntiXray-bStats");
                thread.setDaemon(true);
                return thread;
            });
            long initialDelay = 3L + ThreadLocalRandom.current().nextLong(3L);
            long secondDelay = ThreadLocalRandom.current().nextLong(30L);
            scheduler.schedule(BStatsMetrics::submitSafely, initialDelay, TimeUnit.MINUTES);
            scheduler.scheduleAtFixedRate(BStatsMetrics::submitSafely,
                initialDelay + secondDelay, PERIOD_MINUTES, TimeUnit.MINUTES);
            MeowAntiXrayMetricsLog.info("bStats metrics enabled.");
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
            server = null;
            serverUuid = null;
            playerCount.set(0);
        }
    }

    /** Called from the server thread so the metrics worker never touches live game state. */
    public static void recordServerTick(MinecraftServer minecraftServer) {
        if (minecraftServer == server) {
            playerCount.set(minecraftServer.getPlayerList().getPlayerCount());
        }
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        Path path = PlatformHelper.configDir().resolve("meowantixray-bstats.properties");
        try {
            if (Files.exists(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    properties.load(input);
                }
            } else {
                properties.setProperty("enabled", "true");
                properties.setProperty("service-id", "0");
                properties.setProperty("server-uuid", UUID.randomUUID().toString());
                saveConfig(properties);
            }
        } catch (IOException exception) {
            MeowAntiXrayMetricsLog.warn("Could not read bStats config; using in-memory defaults.", exception);
        }
        return properties;
    }

    private static void saveConfig(Properties properties) {
        Path path = PlatformHelper.configDir().resolve("meowantixray-bstats.properties");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "bStats anonymous usage metrics for Meow Anti-Xray");
            }
        } catch (IOException exception) {
            MeowAntiXrayMetricsLog.warn("Could not write bStats config.", exception);
        }
    }

    private static void submitSafely() {
        try {
            submit();
        } catch (Throwable exception) {
            MeowAntiXrayMetricsLog.warn("Could not submit bStats metrics data.", exception);
        }
    }

    private static void submit() throws IOException {
        MinecraftServer currentServer = server;
        if (!enabled || serviceId <= 0 || currentServer == null || serverUuid == null) {
            return;
        }
        int players = playerCount.get();
        String json = "{\"serverUUID\":" + quote(serverUuid)
            + ",\"metricsVersion\":\"3.2.1\",\"playerAmount\":" + players
            + ",\"minecraftVersion\":" + quote(MinecraftCompat.currentMinecraftVersionId())
            + ",\"modVersion\":" + quote(PlatformHelper.modVersion("meowantixray", "unknown"))
            + ",\"serverSoftware\":" + quote(platformName(platform))
            + ",\"javaVersion\":" + quote(System.getProperty("java.version", "unknown"))
            + ",\"osName\":" + quote(System.getProperty("os.name", "unknown"))
            + ",\"osArch\":" + quote(System.getProperty("os.arch", "unknown"))
            + ",\"coreCount\":" + Runtime.getRuntime().availableProcessors()
            + ",\"service\":{\"id\":" + serviceId + ",\"customCharts\":[]}}";

        byte[] compressed = gzip(json.getBytes(StandardCharsets.UTF_8));
        HttpURLConnection connection = (HttpURLConnection) URI.create(String.format(REPORT_URL, "other"))
            .toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Encoding", "gzip");
        connection.setRequestProperty("User-Agent", "MeowAntiXray-bStats/1");
        connection.setFixedLengthStreamingMode(compressed.length);
        try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
            output.write(compressed);
        }
        int status = connection.getResponseCode();
        if (status >= 400) {
            throw new IOException("bStats returned HTTP " + status);
        }
        connection.disconnect();
    }

    private static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(input);
        }
        return output.toByteArray();
    }

    private static String platformName(String value) {
        return "neoforge".equalsIgnoreCase(value) ? "NeoForge" : "Fabric";
    }

    private static int parseServiceId(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static final class MeowAntiXrayMetricsLog {
        private static void info(String message) {
            com.meowantixray.MeowAntiXrayMod.LOGGER.info(message);
        }

        private static void warn(String message, Throwable exception) {
            com.meowantixray.MeowAntiXrayMod.LOGGER.warn(message, exception);
        }
    }
}
