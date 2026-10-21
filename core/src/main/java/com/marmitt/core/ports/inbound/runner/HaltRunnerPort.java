package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.domain.runner.RunnerStatusCause;
import com.marmitt.core.dto.runner.response.RunnerTransitionResult;

import java.util.List;
import java.util.UUID;

/**
 * Para runners (kill switch). A causa diz quem pediu e por que, e vai para o historico do runner (T59).
 */
public interface HaltRunnerPort {

    List<RunnerTransitionResult> haltAll(RunnerStatusCause cause);

    RunnerTransitionResult haltById(UUID id, RunnerStatusCause cause);
}
