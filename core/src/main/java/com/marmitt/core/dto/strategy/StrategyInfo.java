package com.marmitt.core.dto.strategy;

import com.marmitt.core.enums.StrategyStatus;

import java.time.Instant;
import java.util.Map;

public record StrategyInfo(
        String name,
        String version,
        String description,
        String className,
        boolean enabled,
        Map<String, Object> defaultConfiguration,
        StrategyStatus status,
        Instant lastExecuted,
        Instant createdAt
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public boolean isActive() {
        return enabled && StrategyStatus.ACTIVE.equals(status);
    }
    
    public boolean canExecute() {
        return enabled && (StrategyStatus.ACTIVE.equals(status) || StrategyStatus.IDLE.equals(status));
    }

    public static class Builder {
        private String name;
        private String version;
        private String description;
        private String className;
        private boolean enabled = false;
        private Map<String, Object> defaultConfiguration = Map.of();
        private StrategyStatus status = StrategyStatus.IDLE;
        private Instant lastExecuted;
        private Instant createdAt = Instant.now();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder defaultConfiguration(Map<String, Object> defaultConfiguration) {
            this.defaultConfiguration = defaultConfiguration;
            return this;
        }

        public Builder status(StrategyStatus status) {
            this.status = status;
            return this;
        }

        public Builder lastExecuted(Instant lastExecuted) {
            this.lastExecuted = lastExecuted;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public StrategyInfo build() {
            return new StrategyInfo(name, version, description, className, enabled,
                                  defaultConfiguration, status, lastExecuted, createdAt);
        }
    }
}