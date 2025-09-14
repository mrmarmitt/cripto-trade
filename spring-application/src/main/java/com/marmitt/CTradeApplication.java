package com.marmitt;

import com.marmitt.binance.processor.BinanceMessageProcessor;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.MessageProcessorRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.AdapterMessageProcessorPort;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CTradeApplication {

    @Autowired
    private MessageProcessorRepositoryPort messageProcessorRepository;
    @Autowired
    private ListenerRepositoryPort listenerRepository;


    @PostConstruct
    public void autoRegisterMessageProcessor(){
        AdapterMessageProcessorPort binanceMessageProcessor = new BinanceMessageProcessor();
        messageProcessorRepository.registerProcessor("BINANCE", binanceMessageProcessor);
    }

    @PostConstruct
    public void autoRegisterListener(){
        listenerRepository.clearAllListeners();
    }

    public static void main(String[] args) {
        SpringApplication.run(CTradeApplication.class, args);
    }
}