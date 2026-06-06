package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.runner.response.RunnerHaltResult;

import java.util.List;
import java.util.UUID;

public interface HaltRunnerPort {

    List<RunnerHaltResult> haltAll();

    RunnerHaltResult haltById(UUID id);
}
