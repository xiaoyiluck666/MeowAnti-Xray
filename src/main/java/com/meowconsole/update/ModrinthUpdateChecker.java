package com.meowconsole.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meowconsole.MeowConsoleMod;
import com.meowconsole.compat.MinecraftCompat;
import com.meowconsole.platform.LoaderType;
import com.meowconsole.platform.PlatformHelper;

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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

public final class ModrinthUpdateChecker {
    public static final String PROJECT_SLUG = "meowanti-xray";
    public static final String PROJECT_URL = "https://modrinth.com/mod/" + PROJECT_SLUG;

    private static final String MOD_ID = "meowantixray";
    private static final String API_URL = "https://api.modrinth.com/v2/project/" + PROJECT_SLUG + "/version";
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
                return null;
            });
    }

    public static String statusSummary() {
        return STATUS.get().summary();
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
        String url = stringMember(object, "url");
        if (url == null || url.isBlank()) {
            String id = stringMember(object, "id");
            url = id == null || id.isBlank() ? PROJECT_URL : "https://modrinth.com/mod/" + PROJECT_SLUG + "/version/" + id;
        }
        OffsetDateTime publishedAt = parseDate(stringMember(object, "date_published"));
        return new RemoteVersion(versionNumber, name == null ? versionNumber : name, versionType == null ? "release" : versionType, url, publishedAt);
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
        List<VersionPart> leftParts = versionParts(left);
        List<VersionPart> rightParts = versionParts(right);
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
            case AVAILABLE -> MeowConsoleMod.LOGGER.warn("{}", status.summary());
            case UP_TO_DATE -> MeowConsoleMod.LOGGER.info("{}", status.summary());
            case UNAVAILABLE -> MeowConsoleMod.LOGGER.info("{}", status.summary());
            case NOT_CHECKED, CHECKING -> {
            }
        }
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
                    + " (" + latest.versionType() + "). Download: " + latest.url();
                case UP_TO_DATE -> "[Anti-Xray] update check: " + currentVersion + " is up to date for "
                    + loader + " / MC " + minecraftVersion + ".";
                case UNAVAILABLE -> "[Anti-Xray] update check unavailable for " + loader + " / MC "
                    + minecraftVersion + ": " + reason + ".";
            };
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
