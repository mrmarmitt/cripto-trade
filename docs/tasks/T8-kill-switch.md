# T8 — Kill Switch

**Complexidade:** Média  
**Responsável:** Codex  
**Dependências:** nenhuma  
**Status:** Concluído

---

## Descrição

Durante staging com capital real (mesmo testnet), é necessário ter um mecanismo para interromper a operação imediatamente sem derrubar o processo. Restartar a aplicação não é suficiente — o boot recovery vai reprocessar as ordens pendentes, que pode não ser o comportamento desejado em uma situação de emergência.

O kill switch coloca todos os runners ativos em `HALTED`, impedindo que novos sinais sejam processados e que novas ordens sejam enviadas. Runners já em `HALTED` permanecem inalterados.

---

## Escopo técnico

**Endpoints a criar:**

| Endpoint                              | Ação                                              |
|---------------------------------------|---------------------------------------------------|
| `POST /api/admin/runners/halt`        | Coloca todos os runners `ACTIVE` em `HALTED`     |
| `POST /api/admin/runners/{id}/halt`   | Coloca um runner específico em `HALTED`           |
| `GET /api/admin/runners/status`       | Lista todos os runners com seus status atuais     |

**Fluxo do halt global:**
1. Buscar todos os runners com status `ACTIVE` via `StrategyRunnerRepositoryPort`
2. Para cada runner, transitar para `HALTED`
3. Persistir o novo status
4. Retornar lista de runners afetados com status anterior e novo

**Regras de negócio:**
- Runners em `HALTED`, `RECONCILING` ou qualquer outro estado não-`ACTIVE` não são afetados pelo halt global
- O halt não cancela ordens abertas na exchange — apenas impede novas ordens
- O halt não libera reservas de capital — o capital permanece reservado até conciliação normal
- Runners HALTED pelo kill switch devem ser reativados manualmente (sem reativação automática no boot)

**Arquivos a criar/modificar:**
- `spring-application/.../controller/AdminController.java` — novo ou expandir se já existir
- Usar `StrategyRunnerRepositoryPort` e o use case de query/update já existente

**Segurança mínima para staging:**
- Os endpoints `/api/admin/**` devem exigir um header de autenticação simples configurável via propriedade (`admin.api.key`). Não é necessário implementar OAuth ou JWT nesta tarefa.

---

## Critérios de aceitação

1. `POST /api/admin/runners/halt` coloca todos os runners `ACTIVE` em `HALTED` e retorna JSON com a lista de runners afetados (id, nome, status anterior, status novo).
2. `POST /api/admin/runners/halt` não afeta runners que já estão em `HALTED` ou outros estados.
3. `POST /api/admin/runners/{id}/halt` coloca o runner específico em `HALTED`; retorna 404 se o runner não existir.
4. Após o halt, um sinal de preço recebido para um runner `HALTED` é descartado sem processar e sem lançar exceção.
5. `GET /api/admin/runners/status` retorna a lista completa de runners com id, status e timestamp de última atualização.
6. Os endpoints `/api/admin/**` retornam HTTP 401 se o header `X-Admin-Key` estiver ausente ou incorreto.
7. A operação de halt é persistida no banco — um restart da aplicação não reverte runners para `ACTIVE`.
## Testes de integração obrigatórios

Usar a infraestrutura de integração existente (Spring Boot Test + banco real). Verificar persistência e comportamento após restart.

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| Halt global com múltiplos runners ACTIVE | Todos os runners transitam para HALTED; resposta JSON lista runners afetados com status anterior e novo; `strategy_runners.status = HALTED` no banco |
| Halt global com runners em estados mistos | Apenas runners ACTIVE são afetados; runners HALTED/RECONCILING permanecem inalterados |
| Halt global idempotente (segunda chamada) | Resposta retorna lista vazia de afetados; nenhum erro; banco permanece consistente |
| Halt individual de runner existente | Runner específico vai para HALTED; outros runners não são afetados |
| Halt individual de runner inexistente | HTTP 404 retornado; nenhum efeito no banco |
| Sinal de preço após halt | Sinal descartado sem processamento; nenhuma ordem enviada; nenhuma exceção lançada |
| Restart após halt | Runners permanecem HALTED após reinicialização da aplicação (persistência verificada) |
| Requisição sem header `X-Admin-Key` | HTTP 401 retornado; nenhuma ação executada |
