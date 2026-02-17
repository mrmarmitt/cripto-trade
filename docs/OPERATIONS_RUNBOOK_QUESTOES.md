# Questões Pendentes — Operations Runbook

## 9. Versionamento de Estratégias

**Pergunta:** Como o sistema lida com atualizações de uma estratégia em produção que tem posições abertas?

**Detalhamento necessário:**
- É possível atualizar o código da estratégia sem fechar posições?
- Como migrar o estado (ExecutionPolicy, AccountingPolicy) entre versões?
- Como fazer rollback se a nova versão tiver bugs?
- **Implicação:** Define o processo de deploy e a necessidade de compatibilidade entre versões de Strategy.

**Origem:** BLUEPRINT_QUESTOES.md #9

---

## 11. Persistência para Auditoria e Compliance

**Pergunta:** Quais dados devem ser persistidos indefinidamente para fins de auditoria, compliance e análise pós-trade?

**Detalhamento necessário:**
- Log de todas as alterações de estado de Transaction?
- Histórico completo de market data ou apenas snapshots?
- Por quanto tempo manter dados de ordens canceladas/rejeitadas?
- **Implicação:** Define a estratégia de retenção de dados e a separação entre dados quentes (operacionais) e frios (auditoria).

**Origem:** BLUEPRINT_QUESTOES.md #11

---

## Monitoramento, Health Checks e Observabilidade

**Pergunta:** Como monitorar se os componentes do sistema (StrategyRunner, Portfolio, Exchange Connection) estão funcionando corretamente?

**Detalhamento necessário:**
- Quais métricas por componente? (latência sinal→ordem, taxa de sucesso, saldo divergente, margem órfã, etc.)
- Quais thresholds disparam alertas?
- Health checks via REST endpoints ou métricas Prometheus?
- Qual é a diferença entre um Runner "lento", "travado" e "morto"? Como classificar?
- Como auditar a causa raiz de uma perda de capital após uma falha?
- **Implicação:** Define a estratégia de observabilidade e os pontos de integração com ferramentas de monitoramento.

**Origem:** NOVAS_PONDERACOES.md #6 + QUESTOES_SEM_CLASSIFICACAO.md #2 (enriquecido)

---

## Backup e Restore de Estado

**Pergunta:** Como fazer backup e restore do estado do sistema em caso de corrupção do banco de dados ou perda de dados?

**Detalhamento necessário:**
- Quais dados compõem o snapshot? (Balance, margens reservadas, positions abertas, transactions recentes)
- Frequência do backup? (automático via cron ou manual)
- Qual o processo de restore e validação de integridade?
- Como garantir consistência entre agregados após restore? (soma dos Runners ≈ Portfolio total)
- **Implicação:** Define a estratégia de disaster recovery e o processo de verificação de integridade pós-restore.

**Origem:** NOVAS_PONDERACOES.md #7

---

## SLA e Performance Expectations

**Pergunta:** Quais são os requisitos não-funcionais do sistema?

**Detalhamento necessário:**
- Latência máxima entre sinal e ordem enviada?
- Throughput máximo de sinais por Runner?
- Tempo máximo de reconciliação pós-falha?
- Disponibilidade esperada do sistema?
- **Implicação:** Define os SLAs operacionais e os critérios de aceitação para produção.

**Origem:** QUESTOES_PENDENTES.md #15

---

## Versionamento de Schema

**Pergunta:** Como lidar com mudanças no modelo de dados em produção?

**Detalhamento necessário:**
- Como migrar Positions/Transactions entre versões de schema?
- Backward compatibility é necessária?
- Estrutura do `StrategyOutputDto` é versionada?
- **Implicação:** Define a estratégia de migrations e compatibilidade entre versões do banco de dados.

**Origem:** QUESTOES_PENDENTES.md #16

---

## Security Boundaries — Isolamento de Runners

**Pergunta:** Como isolar Runners para que falhas ou bugs em um não afetem outros?

**Detalhamento necessário:**
- Um Runner com bug pode afetar outros Runners?
- Há limites de recursos por Runner (CPU, memória, threads)?
- Como prevenir que um Runner monopolize a conexão WebSocket?
- Um Runner pode, intencionalmente ou por bug, acessar ou modificar dados de outro Runner?
- Há limites individuais por Runner (número de ordens, posições simultâneas)?
- O que impede um Runner mal configurado de drenar todo o saldo do Portfolio?
- **Implicação:** Define os mecanismos de isolamento de recursos e fault containment entre Runners.

**Origem:** QUESTOES_PENDENTES.md #21 + QUESTOES_SEM_CLASSIFICACAO.md #5 (enriquecido)

---

## Gestão da Dead Letter Queue (DLQ)

**Pergunta:** Como o sistema gerencia as transações que caem na DLQ?

**Localização atual:** Mencionado na Seção 4.D.4 e 11.3.D do Blueprint

**Detalhamento necessário:**
- Quem consome a DLQ? É um processo automático ou manual?
- Qual é o protocolo para reinserir uma transação da DLQ de volta ao fluxo normal?
- Como evitar que a DLQ se torne um sumidouro de ordens órfãs sem solução?
- Há alerta quando a DLQ acumula itens não processados?
- Qual é a política de retenção da DLQ?
- **Implicação:** Define o processo operacional de tratamento de exceções e a cadência de auditoria.

**Origem:** QUESTOES_SEM_CLASSIFICACAO.md #10
