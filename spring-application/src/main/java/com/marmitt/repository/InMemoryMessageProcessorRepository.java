package com.marmitt.repository;

import com.marmitt.binance.processor.BinanceMessageProcessor;
import com.marmitt.core.ports.outbound.repository.MessageProcessorRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.AdapterMessageProcessorPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação em memória do repositório de message processors.
 * Gerencia processors específicos para cada exchange de forma thread-safe.
 */
@Repository
@Slf4j
public class InMemoryMessageProcessorRepository implements MessageProcessorRepositoryPort {
    
    private final Map<String, AdapterMessageProcessorPort> processors = new ConcurrentHashMap<>();

    @Override
    public void registerProcessor(String exchangeName, AdapterMessageProcessorPort processor) {
        if (exchangeName == null || exchangeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Exchange name cannot be null or empty");
        }
        
        if (processor == null) {
            throw new IllegalArgumentException("MessageProcessor cannot be null");
        }
        
        String normalizedName = exchangeName.toUpperCase().trim();
        processors.put(normalizedName, processor);
        
        log.info("Registered MessageProcessor for exchange: {}", normalizedName);
    }
    
    @Override
    public Optional<AdapterMessageProcessorPort> getProcessor(String exchangeName) {
        if (exchangeName == null || exchangeName.trim().isEmpty()) {
            return Optional.empty();
        }
        
        String normalizedName = exchangeName.toUpperCase().trim();
        return Optional.ofNullable(processors.get(normalizedName));
    }
    
    @Override
    public boolean hasProcessor(String exchangeName) {
        if (exchangeName == null || exchangeName.trim().isEmpty()) {
            return false;
        }
        
        String normalizedName = exchangeName.toUpperCase().trim();
        return processors.containsKey(normalizedName);
    }
    
    @Override
    public boolean removeProcessor(String exchangeName) {
        if (exchangeName == null || exchangeName.trim().isEmpty()) {
            return false;
        }
        
        String normalizedName = exchangeName.toUpperCase().trim();
        AdapterMessageProcessorPort removed = processors.remove(normalizedName);
        
        if (removed != null) {
            log.info("Removed MessageProcessor for exchange: {}", normalizedName);
            return true;
        }
        
        return false;
    }
    
    @Override
    public int getProcessorCount() {
        return processors.size();
    }
}