package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.runner.CreateRunnerRequest;
import com.marmitt.core.dto.runner.CreateRunnerResponse;

public interface CreateRunnerPort {

    CreateRunnerResponse execute(CreateRunnerRequest request);
}
