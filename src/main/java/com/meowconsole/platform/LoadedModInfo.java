package com.meowconsole.platform;

import java.nio.file.Path;

public record LoadedModInfo(
    String id,
    String version,
    Path originPath,
    boolean userProvided
) {
}
