package br.com.aurora.auth.web;

/** Quem está logado. Só o nome de usuário; nada de dados de pacientes aqui. */
public record MeResponse(String username) {
}
