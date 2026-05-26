# Proposta de design para conexão e manutenção de user stream WebSocket

## Problema central

O design atual separa três responsabilidades em ports distintos:
- `UserStreamSessionPort` — constrói a URL
- `UserStreamCredentialPort` — gerencia credencial (obtain/revoke + scheduler interno)
- Use cases do core — orquestram a sequência

O problema é que a credencial e a conexão têm ciclos de vida acoplados, mas o design os trata como independentes:
- Se o WebSocket cair inesperadamente (não via `disconnect()`), o scheduler do `BinanceCredentialAdapter` continua rodando com um listen key órfão.
- Se o keepalive falhar repetidamente, o core não sabe — não há escalação.
- O scheduler é implicitamente long-lived: não tem owner claro de quem encerra o thread pool.

---

## O que o mercado faz

Sistemas de trading em produção (Alpaca, Coinbase SDK, ccxt, python-binance) convergem para um padrão chamado **Managed Connection** ou **Connection Supervisor**. A ideia central:

> A sessão é um objeto com tempo de vida igual ao da conexão. Ela nasce no connect e morre no disconnect. Nenhum recurso sobrevive à sessão.

Isso elimina a categoria de bugs de recursos órfãos.

---

## O que eu faria

### 1. `UserStreamSession` — objeto de sessão escopado à conexão

Em vez de ports separados para URL e credencial, um único objeto `UserStreamSession` é criado a cada tentativa de conexão e descartado ao desconectar.

```
UserStreamSession
├── open()     → obtém credencial, constrói URL, retorna URL pronta
├── close()    → cancela keepalive, revoga credencial
└── onEvent()  → observa eventos da sessão (opcional)
```

A fábrica que cria a sessão é o port core:

```
UserStreamSessionFactory
└── createSession(UUID connectionId) → UserStreamSession
```

O core chama `factory.createSession(id)`, obtém a sessão, chama `session.open()`, conecta o WebSocket, e guarda a referência da sessão. No disconnect, chama `session.close()`. A sessão encapsula tudo — URL, credencial, scheduler — e o scheduler vive dentro dela, não em um bean Spring de longa duração.

**Por que isso é melhor:**
- O scheduler nasce e morre com a sessão. Sem órfãos.
- O core guarda uma referência explícita ao objeto que precisa fechar.
- Testar é trivial: `createSession()` retorna um mock que não faz I/O.

---

### 2. Heartbeat monitor separado do credential keepalive

O design atual não distingue dois tipos de "manter vivo":
- **Credential keepalive**: HTTP PUT a cada 30min para estender o listen key (específico da Binance).
- **Connection heartbeat**: ping/pong no nível do WebSocket para detectar conexões zumbi.

Conexões podem parecer ativas no TCP mas estar mortas (sem dados fluindo). Isso é invisível ao scheduler do listen key.

O que eu adicionaria: um `HeartbeatMonitor` dentro do core WebSocket, genérico, que envia um ping periódico e espera pong dentro de um timeout. Se o pong não chegar, emite um evento de falha e dispara reconexão. Isso é independente de exchange e cobre todos os adapters.

---

### 3. Reconexão orientada a eventos, não a polling

O design atual trata reconexão como responsabilidade do caller (quem chamou connect). O mercado usa um modelo onde a própria infraestrutura de conexão emite eventos:

```
ConnectionEvent
├── Connected
├── Disconnected(cause)
├── CredentialRefreshed
├── HeartbeatMissed(count)
└── ReconnectScheduled(attempt, delayMs)
```

O core assina esses eventos e reage (logar, atualizar estado, notificar estratégias). Quem decide reconectar é a política de reconexão, não o use case. O use case de connect é chamado apenas na primeira conexão — o restante é automático.

---

## Comparação com o design atual

| Aspecto | Design atual | Proposta |
|---|---|---|
| Scheduler ownership | Bean Spring long-lived | Escopado à sessão |
| Recursos órfãos | Possível (disconnect inesperado) | Impossível por design |
| Heartbeat | Não existe | HeartbeatMonitor genérico |
| Reconexão | Manual / caller | Orientada a eventos |
| Testabilidade | Mock de 3 ports | Mock de 1 factory |
| Suporte a outras exchanges | Requer impl. dos 3 ports | Só a factory muda |

---

## Trade-off principal

A proposta é mais expressiva mas requer uma mudança estrutural maior: `UserStreamSession` é um objeto com estado e tempo de vida gerenciado, o que é diferente de ports stateless. Isso pode conflitar com a ideia de manter tudo como interfaces simples no core. O design atual é mais simples de entender e já é uma grande melhora sobre o estado anterior — a proposta aqui seria o próximo passo natural se o sistema crescer para múltiplas exchanges com comportamentos de sessão distintos.
