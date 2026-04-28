package com.marmitt.core.ports.inbound.boot;

import com.marmitt.core.dto.boot.BootExecutionCommand;
import com.marmitt.core.ports.outbound.boot.BootExecutionObserverPort;

public interface RunBootSequencePort {
    void execute(BootExecutionCommand command, BootExecutionObserverPort observer);
}
