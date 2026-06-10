package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.TransactionType;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utilitário estático para geração e parsing de clientOrderId.
 * <p>
 * Formato: {@code v1r{shortCode}t{epochMillis}s{seq3d}{type}_{uuidN}}
 * onde N = 13 - shortCode.length(), garantindo total fixo de 36 chars.
 * <p>
 * Exemplo (shortCode=3): {@code v1rABCt1700000000000s001B_a3f9c2d14e}
 * <ul>
 *   <li>{@code v1} — versão do formato</li>
 *   <li>{@code r{shortCode}} — código curto do Runner (2-4 chars)</li>
 *   <li>{@code t{epochMillis}} — timestamp em milissegundos</li>
 *   <li>{@code s{seq3d}} — sequência de 3 dígitos (0-padded, wraps at 999)</li>
 *   <li>{@code {type}} — B=BUY, S=SELL</li>
 *   <li>{@code _{uuidN}} — N chars de um UUID sem hifens (9-11 chars)</li>
 * </ul>
 * <p>
 * Unicidade garantida pela combinação timestamp + seq + uuid parcial.
 * O prefixo {@code v1r{shortCode}} serve como chave de roteamento no Portfolio
 * para identificar qual Runner emitiu a ordem.
 * O comprimento total é sempre 36 chars, respeitando o limite da Binance WS API.
 *
 * @implNote Utilitário transitório — manter como fonte atual de geração/parse até a
 *           introdução de um Value Object rico dedicado no domínio compartilhado,
 *           com parse estruturado e suporte completo a roteamento de DLQ.
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.4.1</a>
 */
public final class ClientOrderId {

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private ClientOrderId() {
    }

    /**
     * Gera um novo clientOrderId único para o Runner e tipo informados.
     * O resultado sempre tem exatamente 36 chars (limite da Binance WS API).
     *
     * @param shortCode código curto do Runner (2-4 chars)
     * @param type      BUY ou SELL
     * @return clientOrderId no formato {@code v1r{shortCode}t{ts}s{seq}{type}_{uuidN}}
     */
    public static String generate(String shortCode, TransactionType type) {
        long ts = System.currentTimeMillis();
        int seq = SEQ.getAndUpdate(v -> (v + 1) % 1000);
        String typeChar = type == TransactionType.BUY ? "B" : "S";
        int uuidLen = 13 - shortCode.length();
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, uuidLen);
        return String.format("v1r%st%ds%03d%s_%s", shortCode, ts, seq, typeChar, uuid);
    }

    /**
     * Extrai o {@code shortCode} do Runner a partir do clientOrderId.
     * Retorna {@code null} se o formato não for reconhecido.
     *
     * @param clientOrderId clientOrderId no formato v1 esperado
     * @return shortCode (ex: "01f") ou {@code null}
     */
    public static String getRunnerShortCode(String clientOrderId) {
        if (clientOrderId == null || !clientOrderId.startsWith("v1r")) {
            return null;
        }
        int tIndex = clientOrderId.indexOf('t');
        if (tIndex < 3) {
            return null;
        }
        return clientOrderId.substring(3, tIndex);
    }

    /**
     * Valida se o clientOrderId está no formato v1 esperado.
     *
     * @param clientOrderId string a validar
     * @return {@code true} se o formato é válido
     */
    public static boolean isValid(String clientOrderId) {
        if (clientOrderId == null) return false;
        return clientOrderId.startsWith("v1r")
                && clientOrderId.contains("t")
                && clientOrderId.contains("_");
    }
}
