package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.TransactionType;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utilitário estático para geração e parsing de clientOrderId.
 * <p>
 * Formato: {@code v1r{shortCode}t{epochMillis}s{seq3d}{type}_{uuid12chars}}
 * <p>
 * Exemplo: {@code v1r01ft1700000000000s001B_a3f9c2d14e7b}
 * <ul>
 *   <li>{@code v1} — versão do formato</li>
 *   <li>{@code r{shortCode}} — código curto do Runner (2-4 chars)</li>
 *   <li>{@code t{epochMillis}} — timestamp em milissegundos</li>
 *   <li>{@code s{seq3d}} — sequência de 3 dígitos (0-padded, wraps at 999)</li>
 *   <li>{@code {type}} — B=BUY, S=SELL</li>
 *   <li>{@code _{uuid12chars}} — primeiros 12 chars de um UUID sem hifens</li>
 * </ul>
 * <p>
 * Unicidade garantida pela combinação timestamp + seq + uuid parcial.
 * O prefixo {@code v1r{shortCode}} serve como chave de roteamento no Portfolio
 * para identificar qual Runner emitiu a ordem.
 *
 * @implNote Utilitário transitório — a forma final descrita no Blueprint e IG 3.4.1 é
 *           {@link com.marmitt.core.domain.shared.ClientOrderId}, um {@code record} rico com
 *           {@code parse()}, todos os campos estruturados e suporte a roteamento de DLQ.
 *           Este utilitário deve ser substituído por {@code shared.ClientOrderId} quando
 *           o roteamento de callbacks e o DLQ forem implementados.
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.4.1</a>
 */
public final class ClientOrderId {

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private ClientOrderId() {
    }

    /**
     * Gera um novo clientOrderId único para o Runner e tipo informados.
     *
     * @param shortCode código curto do Runner (2-4 chars)
     * @param type      BUY ou SELL
     * @return clientOrderId no formato {@code v1r{shortCode}t{ts}s{seq}{type}_{uuid12}}
     */
    public static String generate(String shortCode, TransactionType type) {
        long ts = System.currentTimeMillis();
        int seq = SEQ.getAndUpdate(v -> (v + 1) % 1000);
        String typeChar = type == TransactionType.BUY ? "B" : "S";
        String uuid12 = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return String.format("v1r%st%ds%03d%s_%s", shortCode, ts, seq, typeChar, uuid12);
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
