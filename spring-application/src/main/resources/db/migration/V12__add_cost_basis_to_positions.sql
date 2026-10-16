-- T46: custo total (quote asset) das unidades detidas, fonte de verdade do custo baixado de
-- reserved na venda. Linhas existentes derivam o custo do preco medio.
-- Coluna anulavel de proposito: o dominio sempre grava o valor, e uma linha inserida sem ele
-- (fixture/script) cai no fallback quantity x average_price em vez de assumir custo zero.
ALTER TABLE positions ADD COLUMN cost_basis NUMERIC(30,8);

UPDATE positions SET cost_basis = ROUND(quantity * average_price, 8);
