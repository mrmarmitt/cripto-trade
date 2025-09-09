package com.marmitt.core.domain.data;

/**
 * Interface sealed que marca todos os tipos de dados válidos que podem ser retornados
 * pelos processadores de mensagens. Isso garante type safety sem acoplar o core
 * aos adapters específicos.
 * 
 * Todos os tipos de resposta dos processadores devem implementar esta interface.
 */
public sealed interface ProcessorResponse 
    permits MarketData, OrderData, AccountData, TradeData, ErrorData {
}