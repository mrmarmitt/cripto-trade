package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.runner.request.RecoverTransactionStatusRequest;
import com.marmitt.core.dto.runner.response.RecoverTransactionStatusResponse;

public interface RecoverTransactionStatusPort {

    RecoverTransactionStatusResponse execute(RecoverTransactionStatusRequest request);
}
