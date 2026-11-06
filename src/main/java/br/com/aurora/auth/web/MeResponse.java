package br.com.aurora.auth.web;

/**
 * Quem está logada: e-mail (campo "username", o mesmo de antes), nome para exibir e perfil
 * ("ADMIN" ou "PSYCHOLOGIST"). Nada de dados de pacientes aqui.
 */
public record MeResponse(String username, String displayName, String role) {
}
