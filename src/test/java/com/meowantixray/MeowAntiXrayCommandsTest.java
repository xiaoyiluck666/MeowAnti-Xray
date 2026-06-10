package com.meowantixray;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeowAntiXrayCommandsTest {
    @Test
    void compactCommandOutputJoinsLinesForConsoleAndRcon() {
        assertEquals(
            "status: enabled=true || async: enabled=true, workerThreads=2 || dimensions: none",
            MeowAntiXrayCommands.formatCompactCommandOutput(List.of(
                " status: enabled=true ",
                "",
                "async: enabled=true, workerThreads=2",
                "dimensions: none"
            ))
        );
    }
}
