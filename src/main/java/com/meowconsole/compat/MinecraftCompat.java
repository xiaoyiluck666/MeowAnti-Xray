package com.meowconsole.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MinecraftCompat {
    private static final Class<?> ID_CLASS = findClass(
        "net.minecraft.resources.Identifier",
        "net.minecraft.resources.ResourceLocation"
    );

    private MinecraftCompat() {
    }

    public static String currentMinecraftVersionId() {
        Object version = net.minecraft.SharedConstants.getCurrentVersion();
        Object value = invokeNoArg(version, "id");
        if (value == null) {
            value = invokeNoArg(version, "getId");
        }
        if (value == null) {
            value = invokeNoArg(version, "getName");
        }
        return value == null ? "unknown" : value.toString();
    }

    public static ResourceKey<net.minecraft.world.level.Level> tryLevelKey(String rawQuery) {
        Object id = tryParseId(rawQuery);
        if (id == null) {
            return null;
        }
        try {
            Method create = ResourceKey.class.getMethod("create", ResourceKey.class, ID_CLASS);
            @SuppressWarnings("unchecked")
            ResourceKey<net.minecraft.world.level.Level> key =
                (ResourceKey<net.minecraft.world.level.Level>) create.invoke(null, net.minecraft.world.level.Level.OVERWORLD.registryKey(), id);
            return key;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create level resource key for " + rawQuery, exception);
        }
    }

    public static Optional<Block> findBlockById(String rawId) {
        Object id = parseId(rawId);
        if (id == null) {
            return Optional.empty();
        }
        try {
            Method getOptional = BuiltInRegistries.BLOCK.getClass().getMethod("getOptional", ID_CLASS);
            @SuppressWarnings("unchecked")
            Optional<Block> block = (Optional<Block>) getOptional.invoke(BuiltInRegistries.BLOCK, id);
            return block;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to resolve block id " + rawId, exception);
        }
    }

    public static int chunkX(ChunkPos chunkPos) {
        return readChunkCoordinate(chunkPos, "x");
    }

    public static int chunkZ(ChunkPos chunkPos) {
        return readChunkCoordinate(chunkPos, "z");
    }

    public static int minBuildY(ServerLevel level) {
        return invokeInt(level, "getMinY", "getMinBuildHeight");
    }

    public static int maxBuildY(ServerLevel level) {
        return invokeInt(level, "getMaxY", "getMaxBuildHeight");
    }

    public static boolean hasCommandPermission(CommandSourceStack source, int level) {
        Object legacy = invokeSingleArg(source, "hasPermission", Integer.valueOf(level));
        if (legacy instanceof Boolean allowed) {
            return allowed;
        }
        Object alternateLegacy = invokeSingleArg(source, "hasPermissions", Integer.valueOf(level));
        if (alternateLegacy instanceof Boolean allowed) {
            return allowed;
        }

        Object permissions = invokeNoArg(source, "permissions");
        Object permission = commandPermissionForLevel(level);
        if (permissions != null && permission != null) {
            Object allowed = invokeSingleArg(permissions, "hasPermission", permission);
            if (allowed instanceof Boolean result) {
                return result;
            }
        }
        return false;
    }

    public static boolean hasCommandPermission(ServerPlayer player, int level) {
        if (player == null) {
            return false;
        }
        Object legacy = invokeSingleArg(player, "hasPermissions", Integer.valueOf(level));
        if (legacy instanceof Boolean allowed) {
            return allowed;
        }
        Object alternateLegacy = invokeSingleArg(player, "hasPermission", Integer.valueOf(level));
        if (alternateLegacy instanceof Boolean allowed) {
            return allowed;
        }
        Object permissions = invokeNoArg(player, "permissions");
        Object permission = commandPermissionForLevel(level);
        if (permissions != null && permission != null) {
            Object allowed = invokeSingleArg(permissions, "hasPermission", permission);
            if (allowed instanceof Boolean result) {
                return result;
            }
        }
        Object source = invokeNoArg(player, "createCommandSourceStack");
        if (source instanceof CommandSourceStack commandSource) {
            return hasCommandPermission(commandSource, level);
        }
        return false;
    }

    public static long dayTime(ServerLevel level) {
        Object legacy = invokeNoArg(level, "getDayTime");
        if (legacy instanceof Number number) {
            return number.longValue();
        }
        Object current = invokeNoArg(level, "getOverworldClockTime");
        if (current instanceof Number number) {
            return number.longValue();
        }
        Object fallback = invokeNoArg(level, "getDefaultClockTime");
        if (fallback instanceof Number number) {
            return number.longValue();
        }

        Object manager = invokeNoArg(level, "clockManager");
        Object holder = overworldClockHolder(level);
        if (manager != null && holder != null) {
            Object value = invokeSingleArg(manager, "getTotalTicks", holder);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        throw new IllegalStateException("No supported day-time reader found on ServerLevel");
    }

    public static void setDayTime(ServerLevel level, long dayTime) {
        if (tryInvokeSingleArg(level, "setDayTime", Long.valueOf(dayTime))) {
            return;
        }

        Object manager = invokeNoArg(level, "clockManager");
        Object holder = overworldClockHolder(level);
        if (manager != null && holder != null && tryInvokeTwoArgs(manager, "setTotalTicks", holder, Long.valueOf(dayTime))) {
            return;
        }
        throw new IllegalStateException("No supported day-time writer found on ServerLevel");
    }

    public static void trackDebugChunkIfSupported(ServerLevel level, ServerPlayer player, ChunkPos chunkPos) {
        Object synchronizers = invokeNoArg(level, "debugSynchronizers");
        if (synchronizers == null) {
            return;
        }
        invokeSingleArg(synchronizers, "startTrackingChunk", player, chunkPos);
    }

    public static int paletteSerializedSize(Object palette) {
        Object noArg = invokeNoArg(palette, "getSerializedSize");
        if (noArg instanceof Number number) {
            return number.intValue();
        }
        try {
            Method method = palette.getClass().getMethod("getSerializedSize", Block.BLOCK_STATE_REGISTRY.getClass());
            Object value = method.invoke(palette, Block.BLOCK_STATE_REGISTRY);
            return ((Number) value).intValue();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read palette serialized size", exception);
        }
    }

    public static void writeLongArray(FriendlyByteBuf buffer, long[] raw) {
        if (invokeSingleArg(buffer, "writeFixedSizeLongArray", raw) != null) {
            return;
        }
        if (invokeSingleArg(buffer, "writeLongArray", raw) != null) {
            return;
        }
        throw new IllegalStateException("No supported long-array writer found on FriendlyByteBuf");
    }

    public static LevelChunkSection copySection(LevelChunkSection source) {
        Object copied = invokeNoArg(source, "copy");
        if (copied instanceof LevelChunkSection section) {
            return section;
        }

        try {
            Field statesField = findField(source.getClass(), "states");
            Field biomesField = findField(source.getClass(), "biomes");
            Object states = statesField.get(source);
            Object biomes = biomesField.get(source);
            Object copiedStates = requireNonNull(invokeNoArg(states, "copy"), "states.copy()");
            Object copiedBiomes = requireNonNull(invokeNoArg(biomes, "copy"), "biomes.copy()");

            for (Constructor<?> constructor : source.getClass().getDeclaredConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2
                    && parameterTypes[0].isAssignableFrom(copiedStates.getClass())
                    && parameterTypes[1].isAssignableFrom(copiedBiomes.getClass())) {
                    constructor.setAccessible(true);
                    return (LevelChunkSection) constructor.newInstance(copiedStates, copiedBiomes);
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to copy LevelChunkSection reflectively", exception);
        }

        throw new IllegalStateException("Unsupported LevelChunkSection copy strategy");
    }

    public static MobEffectInstance getJumpBoost(ServerPlayer player) {
        return getEffect(player, "JUMP_BOOST", "JUMP");
    }

    public static boolean isBoatEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        return instanceOf(entity,
            "net.minecraft.world.entity.vehicle.boat.Boat",
            "net.minecraft.world.entity.vehicle.Boat",
            "net.minecraft.world.entity.vehicle.AbstractBoat"
        );
    }

    public static ClickEvent openUrlClickEvent(URI uri) {
        return (ClickEvent) instantiateNested(
            "net.minecraft.network.chat.ClickEvent$OpenUrl",
            new Class<?>[]{URI.class},
            new Object[]{uri},
            ClickEvent.class,
            () -> instantiateDirect(
                ClickEvent.class,
                new Class<?>[]{ClickEvent.Action.class, String.class},
                new Object[]{ClickEvent.Action.OPEN_URL, uri.toString()}
            )
        );
    }

    public static ClickEvent copyToClipboardClickEvent(String value) {
        return (ClickEvent) instantiateNested(
            "net.minecraft.network.chat.ClickEvent$CopyToClipboard",
            new Class<?>[]{String.class},
            new Object[]{value},
            ClickEvent.class,
            () -> instantiateDirect(
                ClickEvent.class,
                new Class<?>[]{ClickEvent.Action.class, String.class},
                new Object[]{ClickEvent.Action.COPY_TO_CLIPBOARD, value}
            )
        );
    }

    public static HoverEvent showTextHoverEvent(Component text) {
        return (HoverEvent) instantiateNested(
            "net.minecraft.network.chat.HoverEvent$ShowText",
            new Class<?>[]{Component.class},
            new Object[]{text},
            HoverEvent.class,
            () -> instantiateDirect(
                HoverEvent.class,
                new Class<?>[]{HoverEvent.Action.class, Object.class},
                new Object[]{HoverEvent.Action.SHOW_TEXT, text}
            )
        );
    }

    public static ServerBossEvent createBossBar(
        UUID id,
        Component name,
        BossEvent.BossBarColor color,
        BossEvent.BossBarOverlay overlay
    ) {
        ServerBossEvent bossBar = instantiateDirect(
            ServerBossEvent.class,
            new Class<?>[]{UUID.class, Component.class, BossEvent.BossBarColor.class, BossEvent.BossBarOverlay.class},
            new Object[]{id, name, color, overlay}
        );
        if (bossBar != null) {
            return bossBar;
        }
        bossBar = instantiateDirect(
            ServerBossEvent.class,
            new Class<?>[]{Component.class, BossEvent.BossBarColor.class, BossEvent.BossBarOverlay.class},
            new Object[]{name, color, overlay}
        );
        if (bossBar != null) {
            return bossBar;
        }
        throw new IllegalStateException("Unsupported ServerBossEvent constructor");
    }

    private static MobEffectInstance getEffect(ServerPlayer player, String primaryField, String fallbackField) {
        Object effect = readStaticField(MobEffects.class, primaryField, fallbackField);
        if (effect == null) {
            return null;
        }
        Object value = invokeSingleArg(player, "getEffect", effect);
        return value instanceof MobEffectInstance instance ? instance : null;
    }

    private static int readChunkCoordinate(ChunkPos chunkPos, String coordinate) {
        Object accessor = invokeNoArg(chunkPos, coordinate);
        if (accessor instanceof Number number) {
            return number.intValue();
        }
        try {
            Field field = findField(chunkPos.getClass(), coordinate);
            return field.getInt(chunkPos);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read chunk coordinate " + coordinate, exception);
        }
    }

    private static int invokeInt(Object target, String primaryMethod, String fallbackMethod) {
        Object primary = invokeNoArg(target, primaryMethod);
        if (primary instanceof Number number) {
            return number.intValue();
        }
        Object fallback = invokeNoArg(target, fallbackMethod);
        if (fallback instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("Missing compatible integer method on " + target.getClass().getName());
    }

    private static Object commandPermissionForLevel(int level) {
        Class<?> permissions = findClassOrNull("net.minecraft.server.permissions.Permissions");
        if (permissions == null) {
            return null;
        }
        if (level >= 4) {
            return readStaticField(permissions, "COMMANDS_OWNER", "COMMANDS_OWNER");
        }
        if (level == 3) {
            return readStaticField(permissions, "COMMANDS_ADMIN", "COMMANDS_ADMIN");
        }
        if (level == 2) {
            return readStaticField(permissions, "COMMANDS_GAMEMASTER", "COMMANDS_GAMEMASTER");
        }
        return readStaticField(permissions, "COMMANDS_MODERATOR", "COMMANDS_MODERATOR");
    }

    private static Object overworldClockHolder(ServerLevel level) {
        Class<?> registries = findClassOrNull("net.minecraft.core.registries.Registries");
        Class<?> worldClocks = findClassOrNull("net.minecraft.world.clock.WorldClocks");
        if (registries == null || worldClocks == null) {
            return null;
        }

        Object worldClockRegistryKey = readStaticField(registries, "WORLD_CLOCK", "WORLD_CLOCK");
        Object overworldClockKey = readStaticField(worldClocks, "OVERWORLD", "OVERWORLD");
        Object registryAccess = invokeNoArg(level, "registryAccess");
        if (worldClockRegistryKey == null || overworldClockKey == null || registryAccess == null) {
            return null;
        }

        Object lookup = invokeSingleArg(registryAccess, "lookupOrThrow", worldClockRegistryKey);
        if (lookup == null) {
            return null;
        }
        return invokeSingleArg(lookup, "getOrThrow", overworldClockKey);
    }

    private static Object tryParseId(String rawQuery) {
        String normalized = normalizeId(rawQuery);
        Object parsed = invokeStaticSingleArg(ID_CLASS, "tryParse", normalized);
        return parsed != null ? parsed : parseId(normalized);
    }

    private static Object parseId(String rawQuery) {
        String normalized = normalizeId(rawQuery);
        Object parsed = invokeStaticSingleArg(ID_CLASS, "parse", normalized);
        if (parsed != null) {
            return parsed;
        }
        return invokeStaticSingleArg(ID_CLASS, "fromNamespaceAndPath", namespace(normalized), path(normalized));
    }

    private static String normalizeId(String rawQuery) {
        String value = rawQuery == null ? "" : rawQuery.strip().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return value;
        }
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static String namespace(String normalized) {
        int split = normalized.indexOf(':');
        return split >= 0 ? normalized.substring(0, split) : "minecraft";
    }

    private static String path(String normalized) {
        int split = normalized.indexOf(':');
        return split >= 0 ? normalized.substring(split + 1) : normalized;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeStaticSingleArg(Class<?> type, String methodName, Object arg) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            if (!acceptsArg(method.getParameterTypes()[0], arg)) {
                continue;
            }
            try {
                return method.invoke(null, arg);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke " + type.getName() + "." + methodName, exception);
            }
        }
        return null;
    }

    private static Object invokeStaticSingleArg(Class<?> type, String methodName, Object firstArg, Object secondArg) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!acceptsArg(parameterTypes[0], firstArg) || !acceptsArg(parameterTypes[1], secondArg)) {
                continue;
            }
            try {
                return method.invoke(null, firstArg, secondArg);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke " + type.getName() + "." + methodName, exception);
            }
        }
        return null;
    }

    private static Object invokeSingleArg(Object target, String methodName, Object arg) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            if (!acceptsArg(method.getParameterTypes()[0], arg)) {
                continue;
            }
            try {
                return method.invoke(target, arg);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke " + target.getClass().getName() + "." + methodName, exception);
            }
        }
        return null;
    }

    private static Object invokeSingleArg(Object target, String methodName, Object firstArg, Object secondArg) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!acceptsArg(parameterTypes[0], firstArg) || !acceptsArg(parameterTypes[1], secondArg)) {
                continue;
            }
            try {
                return method.invoke(target, firstArg, secondArg);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke " + target.getClass().getName() + "." + methodName, exception);
            }
        }
        return null;
    }

    private static boolean tryInvokeSingleArg(Object target, String methodName, Object arg) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            if (!acceptsArg(method.getParameterTypes()[0], arg)) {
                continue;
            }
            try {
                method.invoke(target, arg);
                return true;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke " + target.getClass().getName() + "." + methodName, exception);
            }
        }
        return false;
    }

    private static boolean tryInvokeTwoArgs(Object target, String methodName, Object firstArg, Object secondArg) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!acceptsArg(parameterTypes[0], firstArg) || !acceptsArg(parameterTypes[1], secondArg)) {
                continue;
            }
            try {
                method.invoke(target, firstArg, secondArg);
                return true;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke " + target.getClass().getName() + "." + methodName, exception);
            }
        }
        return false;
    }

    private static boolean acceptsArg(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(arg.getClass());
        }
        return (parameterType == boolean.class && arg instanceof Boolean)
            || (parameterType == byte.class && arg instanceof Byte)
            || (parameterType == short.class && arg instanceof Short)
            || (parameterType == int.class && arg instanceof Integer)
            || (parameterType == long.class && arg instanceof Long)
            || (parameterType == float.class && arg instanceof Float)
            || (parameterType == double.class && arg instanceof Double)
            || (parameterType == char.class && arg instanceof Character);
    }

    private static Object instantiateNested(
        String className,
        Class<?>[] parameterTypes,
        Object[] args,
        Class<?> expectedType,
        java.util.function.Supplier<Object> fallback
    ) {
        try {
            Class<?> nested = Class.forName(className);
            Constructor<?> constructor = nested.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(args);
            if (expectedType.isInstance(instance)) {
                return instance;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return fallback.get();
    }

    private static <T> T instantiateDirect(Class<T> type, Class<?>[] parameterTypes, Object[] args) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readStaticField(Class<?> type, String primaryName, String fallbackName) {
        try {
            Field field = type.getField(primaryName);
            return field.get(null);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = type.getField(fallbackName);
            return field.get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean instanceOf(Object target, String... classNames) {
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className);
                if (type.isInstance(target)) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return false;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Class<?> findClass(String... classNames) {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new IllegalStateException("Could not resolve any compatible resource id class");
    }

    private static Class<?> findClassOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Object requireNonNull(Object value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
