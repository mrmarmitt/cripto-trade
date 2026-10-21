package com.marmitt.core.dto.operations;

import com.marmitt.core.enums.TimelineEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Um evento da linha do tempo (T59). Os vinculos ({@code portfolioId}, {@code runnerId},
 * {@code transactionId}, {@code strategyRef}) so vem preenchidos quando o tipo tem esse alvo.
 *
 * @param id       identificador do registro na tabela de origem; estavel, serve para descartar repetidos
 *                 ao paginar
 * @param origin   {@code MANUAL}, {@code AUTOMATIC} ou {@code BOOT}
 * @param actor    quem enviou o comando ({@code X-Operator}); so em {@code OPERATOR_ACTION}
 * @param from     estado anterior (Safe Mode ou runner)
 * @param to       estado novo (Safe Mode ou runner)
 * @param reason   motivo registrado na origem
 * @param detail   complemento por tipo (ex.: rota e status HTTP do comando, fase da falha do boot)
 */
public record TimelineEvent(
        String id,
        TimelineEventType type,
        Instant occurredAt,
        UUID portfolioId,
        UUID runnerId,
        UUID transactionId,
        UUID strategyRef,
        String origin,
        String actor,
        String from,
        String to,
        String reason,
        String detail
) {
}
