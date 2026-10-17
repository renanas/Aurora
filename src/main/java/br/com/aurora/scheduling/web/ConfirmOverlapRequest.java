package br.com.aurora.scheduling.web;

/** Corpo de reabrir e desfazer reagendamento: só diz se a psicóloga já confirmou o aviso de conflito. */
public record ConfirmOverlapRequest(boolean confirmOverlap) {
}
