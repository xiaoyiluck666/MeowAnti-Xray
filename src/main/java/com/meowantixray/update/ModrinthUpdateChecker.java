package com.meowantixray.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meowantixray.MeowAntiXrayMod;
import com.meowantixray.compat.MinecraftCompat;
import com.meowantixray.platform.LoaderType;
import com.meowantixray.platform.PlatformHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import org.eclipse.jdt.annotation.NonNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

public final class ModrinthUpdateChecker {
    public static final String PROJECT_SLUG = "meowanti-xray";
    public static final String PROJECT_URL = "https://modrinth.com/project/" + PROJECT_SLUG;

    private static final String MOD_ID = "meowantixray";
    private static final String API_URL = "https://api.modrinth.com/v2/project/" + PROJECT_SLUG + "/version";
    private static final String DOWNLOAD_HOVER_TEXT = "Open Meow Anti-Xray on Modrinth";
    private static final int MAX_CHANGE_LINES = 3;
    private static final int MAX_CHANGE_LINE_LENGTH = 120;
    private static final AtomicReference<UpdateStatus> STATUS = new AtomicReference<>(UpdateStatus.notChecked());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new UpdateThreadFactory());
    private static volatile CompletableFuture<Void> activeCheck;

    private ModrinthUpdateChecker() {
    }

    public static void checkAsync() {
        if (activeCheck != null && !activeCheck.isDone()) {
            return;
        }

        String currentVersion = PlatformHelper.modVersion(MOD_ID, "unknown");
        LoaderType loaderType = PlatformHelper.loaderType();
        String minecraftVersion = MinecraftCompat.currentMinecraftVersionId();
        STATUS.set(UpdateStatus.checking(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion));
        activeCheck = CompletableFuture
            .supplyAsync(() -> check(currentVersion, loaderType, minecraftVersion), EXECUTOR)
            .thenAccept(status -> {
                STATUS.set(status);
                logStatus(status);
                notifyOnlineAdmins(status);
            })
            .exceptionally(exception -> {
                UpdateStatus status = UpdateStatus.unavailable(
                    currentVersion,
                    loaderType.modrinthLoaderId(),
                    minecraftVersion,
                    "request failed: " + exception.getMessage()
                );
                STATUS.set(status);
                logStatus(status);
                notifyOnlineAdmins(status);
                return null;
            });
    }

    public static String statusSummary() {
        return STATUS.get().summary();
    }

    public static void notifyAdminIfUpdateAvailable(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        UpdateStatus status = STATUS.get();
        if (status.state() == State.AVAILABLE && MeowAntiXrayMod.isAdminLikePlayer(player)) {
            status.sendAdminMessages(player);
        }
    }

    public static void sendStatusTo(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        STATUS.get().sendStatusMessages(player);
    }

    public static void shutdown() {
        CompletableFuture<Void> check = activeCheck;
        if (check != null) {
            check.cancel(true);
        }
        STATUS.set(UpdateStatus.notChecked());
    }

    private static UpdateStatus check(String currentVersion, LoaderType loaderType, String minecraftVersion) {
        if ("unknown".equals(currentVersion)) {
            return UpdateStatus.unavailable(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion, "current version is unknown");
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
        HttpRequest request = HttpRequest.newBuilder(updateUri(loaderType, minecraftVersion))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .header("User-Agent", "MeowAnti-Xray/" + currentVersion + " (https://github.com/xiaoyiluck666/MeowAnti-Xray)")
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return UpdateStatus.unavailable(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion, "request failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return UpdateStatus.unavailable(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion, "request interrupted");
        }

        if (response.statusCode() == 404) {
            return UpdateStatus.unavailable(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion, "Modrinth project is not public yet");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return UpdateStatus.unavailable(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion, "Modrinth returned HTTP " + response.statusCode());
        }

        Optional<RemoteVersion> latest = latestVersion(response.body());
        if (latest.isEmpty()) {
            return UpdateStatus.unavailable(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion, "no compatible Modrinth versions found");
        }

        RemoteVersion remote = latest.get();
        if (compareVersions(remote.versionNumber(), currentVersion) > 0) {
            return UpdateStatus.available(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion, remote);
        }
        return UpdateStatus.upToDate(currentVersion, loaderType.modrinthLoaderId(), minecraftVersion, remote);
    }

    private static URI updateUri(LoaderType loaderType, String minecraftVersion) {
        String loaderFilter = encodeJsonArray(List.of(loaderType.modrinthLoaderId()));
        String versionFilter = encodeJsonArray(List.of(minecraftVersion));
        return URI.create(API_URL + "?loaders=" + loaderFilter + "&game_versions=" + versionFilter);
    }

    private static String encodeJsonArray(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        json.append(']');
        return URLEncoder.encode(json.toString(), StandardCharsets.UTF_8);
    }

    private static Optional<RemoteVersion> latestVersion(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) {
            return Optional.empty();
        }

        RemoteVersion latest = null;
        JsonArray versions = root.getAsJsonArray();
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            RemoteVersion candidate = remoteVersion(element.getAsJsonObject());
            if (candidate == null) {
                continue;
            }
            if (latest == null || candidate.isNewerThan(latest)) {
                latest = candidate;
            }
        }
        return Optional.ofNullable(latest);
    }

    private static RemoteVersion remoteVersion(JsonObject object) {
        String versionNumber = stringMember(object, "version_number");
        if (versionNumber == null || versionNumber.isBlank()) {
            return null;
        }
        String name = stringMember(object, "name");
        String versionType = stringMember(object, "version_type");
        String changelog = stringMember(object, "changelog");
        String url = stringMember(object, "url");
        if (url == null || url.isBlank()) {
            String id = stringMember(object, "id");
            url = id == null || id.isBlank() ? PROJECT_URL : PROJECT_URL + "/version/" + id;
        }
        String downloadUrl = primaryDownloadUrl(object).orElse(url);
        OffsetDateTime publishedAt = parseDate(stringMember(object, "date_published"));
        return new RemoteVersion(
            versionNumber,
            name == null ? versionNumber : name,
            versionType == null ? "release" : versionType,
            url,
            downloadUrl,
            releaseNotes(changelog),
            publishedAt
        );
    }

    private static Optional<String> primaryDownloadUrl(JsonObject object) {
        JsonElement filesElement = object.get("files");
        if (filesElement == null || !filesElement.isJsonArray()) {
            return Optional.empty();
        }

        String firstUrl = null;
        for (JsonElement fileElement : filesElement.getAsJsonArray()) {
            if (!fileElement.isJsonObject()) {
                continue;
            }
            JsonObject file = fileElement.getAsJsonObject();
            String url = stringMember(file, "url");
            if (url == null || url.isBlank()) {
                continue;
            }
            if (firstUrl == null) {
                firstUrl = url;
            }
            JsonElement primaryElement = file.get("primary");
            if (primaryElement != null && !primaryElement.isJsonNull() && primaryElement.getAsBoolean()) {
                return Optional.of(url);
            }
        }
        return Optional.ofNullable(firstUrl);
    }

    private static List<String> releaseNotes(String changelog) {
        if (changelog == null || changelog.isBlank()) {
            return List.of("See the Modrinth release page for details.");
        }

        List<String> notes = new ArrayList<>();
        for (String rawLine : changelog.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = cleanMarkdownLine(rawLine);
            if (line.isBlank()) {
                continue;
            }
            notes.add(truncate(line, MAX_CHANGE_LINE_LENGTH));
            if (notes.size() >= MAX_CHANGE_LINES) {
                break;
            }
        }
        return notes.isEmpty() ? List.of("See the Modrinth release page for details.") : List.copyOf(notes);
    }

    private static String cleanMarkdownLine(String rawLine) {
        String line = rawLine.strip();
        while (line.startsWith("#") || line.startsWith("-") || line.startsWith("*") || line.startsWith(">")) {
            line = line.substring(1).strip();
        }
        if (line.startsWith("[")) {
            int close = line.indexOf(']');
            if (close >= 0 && close + 1 < line.length() && line.charAt(close + 1) == '(') {
                int end = line.indexOf(')', close + 2);
                if (end > close) {
                    line = line.substring(1, close) + line.substring(end + 1);
                }
            }
        }
        return line.replace("`", "").strip();
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).stripTrailing() + "...";
    }

    private static String stringMember(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static OffsetDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.MIN;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.MIN;
        }
    }

    static int compareVersions(String left, String right) {
        List<VersionPart> leftParts = versionParts(normalizeVersion(left));
        List<VersionPart> rightParts = versionParts(normalizeVersion(right));
        int length = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < length; i++) {
            VersionPart leftPart = i < leftParts.size() ? leftParts.get(i) : VersionPart.ZERO;
            VersionPart rightPart = i < rightParts.size() ? rightParts.get(i) : VersionPart.ZERO;
            int compared = leftPart.compareTo(rightPart);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        int buildIndex = version.indexOf('+');
        return buildIndex >= 0 ? version.substring(0, buildIndex) : version;
    }

    private static List<VersionPart> versionParts(String version) {
        List<VersionPart> parts = new ArrayList<>();
        for (String rawPart : version.toLowerCase(Locale.ROOT).split("[^0-9a-z]+")) {
            if (rawPart.isBlank()) {
                continue;
            }
            if (rawPart.chars().allMatch(Character::isDigit)) {
                parts.add(VersionPart.number(Integer.parseInt(rawPart)));
            } else {
                parts.add(VersionPart.text(rawPart));
            }
        }
        return parts;
    }

    private static void logStatus(UpdateStatus status) {
        switch (status.state()) {
            case AVAILABLE -> MeowAntiXrayMod.LOGGER.warn("{}", status.summary());
            case UP_TO_DATE -> MeowAntiXrayMod.LOGGER.info("{}", status.summary());
            case UNAVAILABLE -> MeowAntiXrayMod.LOGGER.info("{}", status.summary());
            case NOT_CHECKED, CHECKING -> {
            }
        }
    }

    private static void notifyOnlineAdmins(UpdateStatus status) {
        if (status.state() != State.AVAILABLE) {
            return;
        }
        DedicatedServer server = MeowAntiXrayMod.currentServer();
        if (server == null || !server.isRunning()) {
            return;
        }
        server.executeIfPossible(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                notifyAdminIfUpdateAvailable(player);
            }
        });
    }

    private static Component link(String text, String url) {
        String linkText = requireText(text, "text");
        String linkUrl = requireText(url, "url");
        URI uri;
        try {
            uri = URI.create(linkUrl);
        } catch (IllegalArgumentException exception) {
            return literal(linkText + ": " + linkUrl).withStyle(ChatFormatting.YELLOW);
        }
        return literal(linkText)
            .withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(Boolean.TRUE)
                .withClickEvent(MinecraftCompat.openUrlClickEvent(uri))
                .withHoverEvent(MinecraftCompat.showTextHoverEvent(literal(DOWNLOAD_HOVER_TEXT))));
    }

    private static void sendSystemMessage(ServerPlayer player, Component message) {
        player.sendSystemMessage(Objects.requireNonNull(message, "message"));
    }

    private static @NonNull String requireText(String value, String name) {
        return Objects.requireNonNull(value, name);
    }

    private static MutableComponent literal(String text) {
        String safeText = requireText(text, "text");
        return Objects.requireNonNull(Component.literal(safeText), "literal");
    }

    private enum State {
        NOT_CHECKED,
        CHECKING,
        AVAILABLE,
        UP_TO_DATE,
        UNAVAILABLE
    }

    private record RemoteVersion(
        String versionNumber,
        String name,
        String versionType,
        String url,
        String downloadUrl,
        List<String> releaseNotes,
        OffsetDateTime publishedAt
    ) {
        boolean isNewerThan(RemoteVersion other) {
            int versionCompared = compareVersions(versionNumber, other.versionNumber);
            if (versionCompared != 0) {
                return versionCompared > 0;
            }
            return publishedAt.isAfter(other.publishedAt);
        }
    }

    private record UpdateStatus(
        State state,
        String currentVersion,
        String loader,
        String minecraftVersion,
        RemoteVersion latest,
        String reason
    ) {
        static UpdateStatus notChecked() {
            return new UpdateStatus(State.NOT_CHECKED, "unknown", "unknown", "unknown", null, null);
        }

        static UpdateStatus checking(String currentVersion, String loader, String minecraftVersion) {
            return new UpdateStatus(State.CHECKING, currentVersion, loader, minecraftVersion, null, null);
        }

        static UpdateStatus available(String currentVersion, String loader, String minecraftVersion, RemoteVersion latest) {
            return new UpdateStatus(State.AVAILABLE, currentVersion, loader, minecraftVersion, latest, null);
        }

        static UpdateStatus upToDate(String currentVersion, String loader, String minecraftVersion, RemoteVersion latest) {
            return new UpdateStatus(State.UP_TO_DATE, currentVersion, loader, minecraftVersion, latest, null);
        }

        static UpdateStatus unavailable(String currentVersion, String loader, String minecraftVersion, String reason) {
            return new UpdateStatus(State.UNAVAILABLE, currentVersion, loader, minecraftVersion, null, reason);
        }

        String summary() {
            return switch (state) {
                case NOT_CHECKED -> "[Anti-Xray] update check has not run yet.";
                case CHECKING -> "[Anti-Xray] checking Modrinth updates for " + currentVersion + " (" + loader + ", MC " + minecraftVersion + ")...";
                case AVAILABLE -> "[Anti-Xray] update available: " + currentVersion + " -> " + latest.versionNumber()
                    + " (" + latest.versionType() + "). Download: " + latest.downloadUrl();
                case UP_TO_DATE -> "[Anti-Xray] update check: " + currentVersion + " is up to date for "
                    + loader + " / MC " + minecraftVersion + ".";
                case UNAVAILABLE -> "[Anti-Xray] update check unavailable for " + loader + " / MC "
                    + minecraftVersion + ": " + reason + ".";
            };
        }

        void sendStatusMessages(ServerPlayer player) {
            if (state != State.AVAILABLE) {
                sendSystemMessage(player, literal(summary()));
                return;
            }
            sendAdminMessages(player);
        }

        void sendAdminMessages(ServerPlayer player) {
            if (state != State.AVAILABLE) {
                return;
            }
            sendSystemMessage(player, literal("[Anti-Xray] New version available: ")
                .withStyle(ChatFormatting.GOLD)
                .append(literal(currentVersion + " -> " + latest.versionNumber()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(literal(" (" + loader + ", MC " + minecraftVersion + ")").withStyle(ChatFormatting.GRAY)));
            sendSystemMessage(player, literal("[Anti-Xray] Changes:").withStyle(ChatFormatting.GOLD));
            for (String note : latest.releaseNotes()) {
                sendSystemMessage(player, literal("[Anti-Xray] - " + note).withStyle(ChatFormatting.GRAY));
            }
            MutableComponent download = literal("[Anti-Xray] Download: ").withStyle(ChatFormatting.GOLD);
            download.append(Objects.requireNonNull(link(latest.downloadUrl(), latest.downloadUrl()), "download link"));
            sendSystemMessage(player, download);
            MutableComponent releasePage = literal("[Anti-Xray] Release page: ").withStyle(ChatFormatting.GOLD);
            releasePage.append(Objects.requireNonNull(link(latest.url(), latest.url()), "release link"));
            sendSystemMessage(player, releasePage);
        }
    }

    private record VersionPart(boolean number, int numberValue, String textValue) implements Comparable<VersionPart> {
        private static final VersionPart ZERO = number(0);

        static VersionPart number(int value) {
            return new VersionPart(true, value, "");
        }

        static VersionPart text(String value) {
            return new VersionPart(false, 0, value);
        }

        @Override
        public int compareTo(VersionPart other) {
            if (number && other.number) {
                return Integer.compare(numberValue, other.numberValue);
            }
            if (number != other.number) {
                return number ? 1 : -1;
            }
            return textValue.compareTo(other.textValue);
        }
    }

    private static final class UpdateThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MeowAntiXray-ModrinthUpdateChecker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
