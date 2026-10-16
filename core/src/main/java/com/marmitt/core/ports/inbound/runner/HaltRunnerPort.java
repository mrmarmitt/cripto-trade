package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.runner.response.RunnerTransitionResult;

import java.util.List;
import java.util.UUID;

public interface HaltRunnerPort {

    List<RunnerTransitionResult> haltAll();

    RunnerTransitionResult haltById(UUID id);
}
