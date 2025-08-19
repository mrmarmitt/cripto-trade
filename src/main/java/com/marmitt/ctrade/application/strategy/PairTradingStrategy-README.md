# PairTradingStrategy

## 📊 O que é o PairTradingStrategy?

O `PairTradingStrategy` é uma estratégia de **arbitragem estatística** que monitora a relação de preços entre **dois ativos correlacionados** (ex: BTC/USDT e ETH/USDT) e negocia quando essa relação sai do padrão histórico.

## 🔧 Como Funciona

### 1. **Dois TradingPairs Monitorados**
- **Pair1**: BTC/USDT (configurável via parâmetro `pair1`)  
- **Pair2**: ETH/USDT (configurável via parâmetro `pair2`)

### 2. **Cálculo do Spread**
```java
spread = price1 / price2  // Ex: BTC_price / ETH_price = 45000 / 3000 = 15.0
```

### 3. **Análise Estatística (Z-Score)**
- Mantém histórico de spreads (últimos 50 valores por padrão)
- Calcula média e desvio padrão do spread histórico
- Calcula Z-Score: `(spread_atual - média_histórica) / desvio_padrão`

### 4. **Sinais de Trading**
- **Z-Score > +2.0**: Spread muito alto → **VENDE Pair1** (BTC está "caro" vs ETH)
- **Z-Score < -2.0**: Spread muito baixo → **COMPRA Pair1** (BTC está "barato" vs ETH)
- **-2.0 ≤ Z-Score ≤ +2.0**: **HOLD** (relação dentro do padrão normal)

## ⚡ Comportamento Atual

### **Importante: Estratégia Simplificada**
A estratégia atual **só gera sinais para o Pair1** (BTC/USDT), mas **usa o Pair2** (ETH/USDT) como **referência** para decidir se BTC está caro ou barato em relação ao ETH.

**Operações realizadas:**
- Quando BTC está "caro" vs ETH → **VENDE BTC/USDT**
- Quando BTC está "barato" vs ETH → **COMPRA BTC/USDT**
- **Não** gera ordens para ETH/USDT

**O que NÃO faz:**
- Não opera simultaneamente nos dois pares
- Não compra USDT usando BTC
- Não implementa exit strategy automática
- Não gerencia posições existentes

## 📈 Exemplo Prático

### Cenário:
```
BTC/USDT: $45,000
ETH/USDT: $3,000  
Spread atual: 45000/3000 = 15.0
Média histórica: 14.2
Desvio padrão: 0.3
Z-Score: (15.0 - 14.2) / 0.3 = +2.67
```

### Ação da Estratégia:
```java
// Z-Score +2.67 > threshold +2.0
// Estratégia gera: SELL BTC/USDT
StrategySignal.sell(
    pair: "BTC/USDT",
    quantity: 0.002222, // $100 / $45000
    price: 45000,
    reason: "Spread above upper threshold (z-score: 2.67). Selling BTC/USDT"
)
```

## ⚙️ Configuração

### Parâmetros Disponíveis (`application-strategies.yml`):

```yaml
strategies:
  pair-trading:
    enabled: true
    parameters:
      pair1: "BTC/USDT"              # Primeiro par (alvo das operações)
      pair2: "ETH/USDT"              # Segundo par (referência)
      upperThreshold: 2.0            # Z-Score para VENDA
      lowerThreshold: -2.0           # Z-Score para COMPRA
      maxHistorySize: 50             # Tamanho do histórico de spreads
      tradingAmount: 100.0           # Valor em USD por operação
    max-order-value: 1000.00
    min-order-value: 10.00
    risk-limit: 0.02
```

### Parâmetros Explicados:

- **`pair1`**: Par principal que será negociado (ex: "BTC/USDT")
- **`pair2`**: Par de referência para cálculo do spread (ex: "ETH/USDT")
- **`upperThreshold`**: Z-Score limite superior (padrão: +2.0) - acima disso, VENDE pair1
- **`lowerThreshold`**: Z-Score limite inferior (padrão: -2.0) - abaixo disso, COMPRA pair1
- **`maxHistorySize`**: Quantidade de spreads mantidos no histórico (padrão: 50)
- **`tradingAmount`**: Valor em USD a ser negociado por operação (padrão: $100)

## 🎯 Objetivo da Estratégia

### **Mean Reversion (Reversão à Média)**
A estratégia assume que a relação de preços BTC/ETH eventualmente voltará ao padrão histórico, gerando lucro quando isso acontecer.

### Lógica:
1. **BTC "caro" vs ETH** → Vende BTC esperando que o preço caia
2. **BTC "barato" vs ETH** → Compra BTC esperando que o preço suba
3. **Lucro** surge quando a relação volta ao normal

## ⚠️ Limitações Atuais

### 1. **Pair Trading Incompleto**
- Deveria operar **BTC e ETH simultaneamente** para hedge completo
- Implementação atual só opera um lado (BTC/USDT)

### 2. **Sem Exit Strategy**
- Não implementa lógica para fechar posições
- Não define quando sair de uma posição lucrativa

### 3. **Sem Gestão de Portfolio**
- Não rastreia posições existentes
- Não considera exposição atual aos ativos

### 4. **Sem Stop Loss/Take Profit**
- Não implementa proteção contra perdas
- Não realiza lucros automaticamente

## 🔮 Pair Trading Completo (Não Implementado)

### Como deveria funcionar:
```java
// Z-Score > +2.0: BTC caro vs ETH
VENDE BTC/USDT + COMPRA ETH/USDT (valores equivalentes)

// Z-Score < -2.0: BTC barato vs ETH  
COMPRA BTC/USDT + VENDE ETH/USDT (valores equivalentes)

// Z-Score próximo de 0: Fecha posições
FECHA todas as posições abertas (realiza lucro/prejuízo)
```

## 📊 Monitoramento

### Logs Típicos:
```bash
[INFO] PairTradingStrategy - initialized with pairs: BTC/USDT and ETH/USDT, thresholds: [-2.0, 2.0]
[DEBUG] PairTradingStrategy - Spread: 15.0, Z-Score: 2.67, Thresholds: [-2.0, 2.0]
[INFO] TradingOrchestrator - Strategy PairTradingStrategy generated signal: SELL for pair BTC/USDT
```

### Métodos de Monitoramento:
```java
strategy.getCurrentZScore()  // Z-Score atual
strategy.getHistorySize()    // Tamanho do histórico
strategy.getPair1()          // Primeiro par
strategy.getPair2()          // Segundo par
```

## 🧪 Testabilidade

A estratégia é totalmente testável através de:
- Injeção de `MarketData` com preços específicos
- Configuração de parâmetros via `StrategyConfig`
- Verificação de sinais gerados
- Análise do histórico de spreads

## 🚀 Próximas Melhorias

1. **Implementar pair trading completo** (operar ambos os pares)
2. **Adicionar exit strategy** (quando fechar posições)
3. **Implementar stop loss/take profit**
4. **Gestão de portfolio** (rastrear posições abertas)
5. **Correlação dinâmica** (ajustar thresholds baseado na correlação)
6. **Multiple pairs** (operar múltiplos pares simultaneamente)

---

**Implementação atual**: Versão simplificada que usa ETH apenas como indicador de referência para decisões de BTC/USDT.