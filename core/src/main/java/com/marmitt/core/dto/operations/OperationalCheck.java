package com.marmitt.core.dto.operations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Resultado de uma verificacao do estado operacional (T55).
 *
 * @param id       identificador estavel da verificacao (ex.: {@code DLQ_UNRESOLVED})
 * @param status   {@code OK} quando nada foi encontrado
 * @param summary  o que foi encontrado, em uma linha
 * @param subjects o que esta afetado (portfolio, runner, transacao, simbolo...), um mapa por sujeito
 * @param since    desde quando o problema existe, quando se sabe (o mais antigo dos sujeitos)
 * @param runbook  secao do runbook com diagnostico e acao
 */
public record OperationalCheck(String id,
                               OperationalSeverity status,
                               String summary,
                               List<Map<String, Object>> subjects,
                               Instant since,
                               String runbook) {

    public OperationalCheck {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
    }
}
