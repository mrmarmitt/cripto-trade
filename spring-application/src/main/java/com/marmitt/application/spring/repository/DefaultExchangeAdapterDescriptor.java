package com.marmitt.application.spring.repository;

import com.marmitt.core.exceptions.UnsupportedCapabilityException;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.ExchangeOrderPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;

public final class DefaultExchangeAdapterDescriptor implements ExchangeAdapterDescriptor {

    private final String exchangeName;
    private final ExchangeStreamingPort streaming;
    private final ExchangeOrderPort orderPort;
    private final ExchangeUserStreamPort userStream;
    private final UserStreamSessionPort userStreamSession;
    private final ExchangeOrderExecutionPort orderExecution;
    private final ExchangeOrderQueryPort orderQuery;
    private final ExchangeAccountQueryPort accountQuery;
    private final ExchangeBootReadinessPort bootReadiness;

    private DefaultExchangeAdapterDescriptor(Builder builder) {
        this.exchangeName = builder.exchangeName;
        this.streaming = builder.streaming;
        this.orderPort = builder.orderPort;
        this.userStream = builder.userStream;
        this.userStreamSession = builder.userStreamSession;
        this.orderExecution = builder.orderExecution;
        this.orderQuery = builder.orderQuery;
        this.accountQuery = builder.accountQuery;
        this.bootReadiness = builder.bootReadiness;
    }

    public static Builder builder(String exchangeName) {
        return new Builder(exchangeName);
    }

    @Override public String exchangeName() { return exchangeName; }

    @Override public ExchangeStreamingPort streaming() {
        if (streaming == null) throw new UnsupportedCapabilityException(exchangeName, "streaming");
        return streaming;
    }

    @Override public ExchangeOrderPort orderPort() {
        if (orderPort == null) throw new UnsupportedCapabilityException(exchangeName, "orderPort");
        return orderPort;
    }

    @Override public boolean hasUserStream() { return userStream != null; }

    @Override public ExchangeUserStreamPort userStream() {
        if (userStream == null) throw new UnsupportedCapabilityException(exchangeName, "userStream");
        return userStream;
    }

    @Override public boolean hasUserStreamSession() { return userStreamSession != null; }

    @Override public UserStreamSessionPort userStreamSession() {
        if (userStreamSession == null) throw new UnsupportedCapabilityException(exchangeName, "userStreamSession");
        return userStreamSession;
    }

    @Override public boolean hasOrderExecution() { return orderExecution != null; }

    @Override public ExchangeOrderExecutionPort orderExecution() {
        if (orderExecution == null) throw new UnsupportedCapabilityException(exchangeName, "orderExecution");
        return orderExecution;
    }

    @Override public boolean hasOrderQuery() { return orderQuery != null; }

    @Override public ExchangeOrderQueryPort orderQuery() {
        if (orderQuery == null) throw new UnsupportedCapabilityException(exchangeName, "orderQuery");
        return orderQuery;
    }

    @Override public boolean hasAccountQuery() { return accountQuery != null; }

    @Override public ExchangeAccountQueryPort accountQuery() {
        if (accountQuery == null) throw new UnsupportedCapabilityException(exchangeName, "accountQuery");
        return accountQuery;
    }

    @Override public boolean hasBootReadiness() { return bootReadiness != null; }

    @Override public ExchangeBootReadinessPort bootReadiness() {
        if (bootReadiness == null) throw new UnsupportedCapabilityException(exchangeName, "bootReadiness");
        return bootReadiness;
    }

    public static final class Builder {
        private final String exchangeName;
        private ExchangeStreamingPort streaming;
        private ExchangeOrderPort orderPort;
        private ExchangeUserStreamPort userStream;
        private UserStreamSessionPort userStreamSession;
        private ExchangeOrderExecutionPort orderExecution;
        private ExchangeOrderQueryPort orderQuery;
        private ExchangeAccountQueryPort accountQuery;
        private ExchangeBootReadinessPort bootReadiness;

        private Builder(String exchangeName) {
            this.exchangeName = exchangeName.toUpperCase();
        }

        public Builder streaming(ExchangeStreamingPort streaming) { this.streaming = streaming; return this; }
        public Builder orderPort(ExchangeOrderPort orderPort) { this.orderPort = orderPort; return this; }
        public Builder userStream(ExchangeUserStreamPort userStream) { this.userStream = userStream; return this; }
        public Builder userStreamSession(UserStreamSessionPort userStreamSession) { this.userStreamSession = userStreamSession; return this; }
        public Builder orderExecution(ExchangeOrderExecutionPort orderExecution) { this.orderExecution = orderExecution; return this; }
        public Builder orderQuery(ExchangeOrderQueryPort orderQuery) { this.orderQuery = orderQuery; return this; }
        public Builder accountQuery(ExchangeAccountQueryPort accountQuery) { this.accountQuery = accountQuery; return this; }
        public Builder bootReadiness(ExchangeBootReadinessPort bootReadiness) { this.bootReadiness = bootReadiness; return this; }

        public DefaultExchangeAdapterDescriptor build() {
            return new DefaultExchangeAdapterDescriptor(this);
        }
    }
}
