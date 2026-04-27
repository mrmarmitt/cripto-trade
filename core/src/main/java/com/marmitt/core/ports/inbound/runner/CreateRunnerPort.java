package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.runner.request.CreateRunnerRequest;
import com.marmitt.core.dto.runner.response.CreateRunnerResponse;

public interface CreateRunnerPort {

    CreateRunnerResponse execute(CreateRunnerRequest request);
}

