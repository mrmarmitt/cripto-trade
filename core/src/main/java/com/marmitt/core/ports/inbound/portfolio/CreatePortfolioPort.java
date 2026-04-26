package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.portfolio.request.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.response.CreatePortfolioResponse;

public interface CreatePortfolioPort {

    /**
     * Cria um novo portfolio com estratégia e capital inicial
     *
     * @param request Dados para criação do portfolio
     * @return Response com dados do portfolio criado
     */
    CreatePortfolioResponse execute(CreatePortfolioRequest request);
}

