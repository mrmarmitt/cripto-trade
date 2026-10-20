package com.marmitt.core.ports.inbound.websocket;

/**
 * Garante que o market data e o stream de usuario que os runners operacionais precisam estejam
 * chegando (T56): abre conexao e assina o que falta. Idempotente; chamado no boot, periodicamente e
 * depois que um runner volta a operar.
 */
public interface EnsureMarketDataFeedPort {

    void ensure();
}
