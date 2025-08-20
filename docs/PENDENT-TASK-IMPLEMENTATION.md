# Tarefas pendentes 

## 2 - Investigar API de ordens de negociação da implementação do MOCK
A resposta da API de performace indica muitas operações abertas.
A resposta da API mostra divergencia entre os valores totalTrades, openTrades e closedTrades
'''
[
    {
        "strategyName": "PairTradingStrategy",
        "totalPnL": 3.3504620729,
        "realizedPnL": -0.0981835,
        "unrealizedPnL": 3.4486455729,
        "totalReturn": 0.03,
        "totalCommission": 0,
        "totalTrades": 97,
        "openTrades": 39,
        "closedTrades": 15,
        "winningTrades": 3,
        "losingTrades": 10,
        "winRate": 0.030927835051546393,
        "lossRate": 0.10309278350515463,
        "avgWin": 0.0003,
        "avgLoss": -0.0099,
        "avgPnL": 0.0345,
        "profitFactor": 0.0091,
        "maxDrawdown": 0,
        "maxDrawdownPercentage": null,
        "currentDrawdown": null,
        "sharpeRatio": -0.9249,
        "sortinoRatio": null,
        "calmarRatio": null,
        "avgHoldingPeriod": "PT30S",
        "maxHoldingPeriod": "PT1M4S",
        "minHoldingPeriod": "PT3S",
        "firstTradeDate": "2025-08-19T17:07:52.913543",
        "lastTradeDate": "2025-08-19T17:17:20.909271",
        "totalActiveTime": "PT9M27.995728S",
        "bestTrade": 0.00051339,
        "worstTrade": -0.01842799,
        "bestTradeReturn": 0,
        "worstTradeReturn": -0.02,
        "todaysPnL": 1.00864656,
        "weekPnL": 1.00864656,
        "monthPnL": 1.00864656,
        "allocatedCapital": null,
        "utilizedCapital": null,
        "capitalUtilization": null,
        "volatility": 0.008,
        "beta": null,
        "alpha": null,
        "performanceScore": 20,
        "performingWell": false,
        "performanceGrade": "D"
    }
]
'''
## 3 - Investigar logs vs resposta de performance de estrategia de trade

## 4 - Segregar documentação
Parece que a documentação para a strategy esta em dois README.md. No principal e no src/main/java/com/marmitt/ctrade/application/strategy/README.md. Pode mover toda a documentação sobre implementação de novas estrategias, parametros, funcionamentos mais especificos  para o
src/main/java/com/marmitt/ctrade/application/strategy/README.md. Após isso, referenciar o o src/main/java/com/marmitt/ctrade/application/strategy/README.md no README.md principal. Referenciar o src/main/java/com/marmitt/ctrade/application/strategy/PairTradingStrategy-README.md no README.md principal      
tmb



