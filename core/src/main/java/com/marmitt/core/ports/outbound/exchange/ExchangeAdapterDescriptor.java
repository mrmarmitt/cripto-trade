package com.marmitt.core.ports.outbound.exchange;

import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;

public interface ExchangeAdapterDescriptor {

    String exchangeName();

    ExchangeStreamingPort streaming();

    ExchangeOrderPort orderPort();

    boolean hasUserStream();

    ExchangeUserStreamPort userStream();

    boolean hasUserStreamSession();

    UserStreamSessionPort userStreamSession();

    boolean hasOrderExecution();

    ExchangeOrderExecutionPort orderExecution();

    boolean hasOrderQuery();

    ExchangeOrderQueryPort orderQuery();

    boolean hasAccountQuery();

    ExchangeAccountQueryPort accountQuery();

    boolean hasBootReadiness();

    ExchangeBootReadinessPort bootReadiness();
}
