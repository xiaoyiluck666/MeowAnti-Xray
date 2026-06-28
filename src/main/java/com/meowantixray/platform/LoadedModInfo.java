package com.meowantixray.platform;

import java.nio.file.Path;
import org.eclipse.jdt.annotation.Nullable;

public record LoadedModInfo(
    String id,
    String version,
    @Nullable Path originPath,
    boolean userProvided
) {
    public LoadedModInfo(String id, String version, @Nullable Path originPath, boolean userProvided) {
        this.id = id;
        this.version = version;
        this.originPath = originPath;
        this.userProvided = userProvided;
    }

    public LoadedModInfo(String id, String version, boolean userProvided) {
        this(id, version, null, userProvided);
    }
}
