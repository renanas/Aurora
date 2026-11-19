package br.com.aurora.auth.application;

/** O que o servidor reconfere a cada requisição de quem está logada: a conta ainda vale e a senha precisa ser trocada? */
public record AccountState(boolean active, boolean mustChangePassword) {
}
