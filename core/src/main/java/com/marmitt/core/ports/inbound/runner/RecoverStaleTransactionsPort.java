package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.runner.request.RecoverStaleTransactionsRequest;
import com.marmitt.core.dto.runner.response.RecoverStaleTransactionsResponse;

public interface RecoverStaleTransactionsPort {

    RecoverStaleTransactionsResponse execute(RecoverStaleTransactionsRequest request);
}
