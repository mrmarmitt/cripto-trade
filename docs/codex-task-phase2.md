• 1. Fechar contrato da Phase 2

- Definir pass/fail/skipped por cenário.
- Definir política final (WARN_ONLY dev, FAIL_FAST prod).

2. Implementar Zombie Detection (Portfolio)

- Consultar ordens abertas na exchange (listAllOpenOrders).
- Cruzar com transactions locais.
- Classificar órfãs/inválidas.
- Encaminhar para ação definida (DLQ/alerta/bloqueio).

3. Implementar ReservationTTL Worker no boot

- Inicializar worker na Phase 2.
- Expirar reservas zumbi (PENDING sem exchangeOrderId acima do TTL).
- Garantir idempotência.

4. Introduzir cutoff temporal

- Usar lastReconciliationAt para delimitar escopo da varredura.
- Evitar falso positivo em ordens históricas.

5. Hardening operacional

- Métricas da Phase 2 (checks, falhas, drift, zombies).
- Logs estruturados com portfolioId, exchange, code, drift.
- Alertas mínimos para FAIL_FAST.

6. Testes de integração (mock-first)

- Sanity OK, drift em WARN_ONLY, drift em FAIL_FAST.
- Zombie detectado e roteado corretamente.
- TTL expirando somente o que deve.
- Garantir gate para Phase 3.

7. Critério de “Phase 2 concluída”

- Todos os cenários acima cobertos e passando.
- Sem dependência manual para boot saudável no mock.
- Comportamento determinístico em restart com dados sujos. 