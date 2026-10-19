package com.marmitt.core.application.usecase.strategy;

import com.marmitt.core.dto.strategy.RemoteStrategyDefinition;
import com.marmitt.core.exceptions.StrategyUnavailableException;
import com.marmitt.core.ports.inbound.strategy.RegisterRemoteStrategiesPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort.RemoteStrategyIdentity;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registra estratégias remotas do catálogo (T41). Antes de registrar, confere a identidade que o
 * próprio serviço declara: mesmo {@code strategyId}, nome e versão do catálogo, e contrato de rede
 * com a versão maior suportada. Divergência ou serviço fora = entrada não registrada + ERROR; o
 * runner que aponta para ela fica em HOLD implícito (estratégia não encontrada) até a próxima tentativa.
 */
@Slf4j
public class RegisterRemoteStrategiesUseCase implements RegisterRemoteStrategiesPort {

    static final int SUPPORTED_CONTRACT_MAJOR = 1;

    private final StrategyRepositoryPort strategyRepository;
    private final StrategyEvaluationPort evaluation;

    public RegisterRemoteStrategiesUseCase(StrategyRepositoryPort strategyRepository,
                                           StrategyEvaluationPort evaluation) {
        this.strategyRepository = strategyRepository;
        this.evaluation = evaluation;
    }

    @Override
    public List<UUID> registerPending(List<RemoteStrategyDefinition> catalog) {
        List<UUID> registered = new ArrayList<>();
        for (RemoteStrategyDefinition definition : catalog) {
            if (strategyRepository.findById(definition.strategyRef()).isPresent()) {
                continue;
            }
            try {
                String divergence = divergence(definition, evaluation.describe(definition.strategyRef()));
                if (divergence != null) {
                    log.error("remoteStrategy: catalog entry NOT registered strategyRef={} - {}",
                            definition.strategyRef(), divergence);
                    continue;
                }
                strategyRepository.registerStrategy(new RemoteTradingStrategy(definition.strategyRef(),
                        definition.name(), definition.version(), evaluation));
                registered.add(definition.strategyRef());
                log.info("remoteStrategy: registered strategyRef={} name={} version={}",
                        definition.strategyRef(), definition.name(), definition.version());
            } catch (StrategyUnavailableException e) {
                log.error("remoteStrategy: catalog entry NOT registered strategyRef={} - service unavailable ({}): {}",
                        definition.strategyRef(), e.reason(), e.getMessage());
            } catch (RuntimeException e) {
                // Uma entrada com resposta inesperada nao pode impedir o registro das demais.
                log.error("remoteStrategy: catalog entry NOT registered strategyRef={} - unexpected failure: {}",
                        definition.strategyRef(), e.toString(), e);
            }
        }
        return registered;
    }

    private static String divergence(RemoteStrategyDefinition expected, RemoteStrategyIdentity served) {
        if (!expected.strategyRef().equals(served.strategyId())) {
            return "service declares strategyId " + served.strategyId();
        }
        if (!expected.name().equals(served.name()) || !expected.version().equals(served.version())) {
            return "service declares " + served.name() + " " + served.version()
                    + ", catalog expects " + expected.name() + " " + expected.version();
        }
        if (!isSupportedContract(served.contractVersion())) {
            return "unsupported contract version " + served.contractVersion()
                    + " (supported major " + SUPPORTED_CONTRACT_MAJOR + ")";
        }
        return null;
    }

    private static boolean isSupportedContract(String contractVersion) {
        if (contractVersion == null) {
            return false;
        }
        String major = contractVersion.split("\\.", 2)[0];
        return String.valueOf(SUPPORTED_CONTRACT_MAJOR).equals(major);
    }
}
