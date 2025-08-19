package com.marmitt.ctrade.infrastructure.config;

/**
 * Enum que define os tipos de exchange disponíveis para WebSocket.
 * Usado para configurar qual implementação deve ser carregada.
 */
public enum ExchangeType {
    
    /**
     * Implementação mock para desenvolvimento e testes.
     * Simula preços e operações sem conectar a uma exchange real.
     */
    MOCK,
    
    /**
     * Implementação real da Binance.
     * Conecta ao WebSocket da Binance para dados de mercado reais.
     */
    BINANCE
}