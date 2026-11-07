package br.com.aurora.shared.security;

import java.util.UUID;

/**
 * Quem está logada nesta requisição. É a única ponte entre os módulos de negócio e a autenticação:
 * o módulo {@code auth} a implementa, e {@code patient} e {@code scheduling} só conhecem esta interface
 * (nunca o Spring Security). Todo dado de paciente, recorrência e sessão pertence a uma psicóloga, e cada
 * uma só enxerga o que é seu.
 *
 * Não existe "quem está logada" fora de uma requisição (por exemplo, no job das 03:00): quem roda fora
 * dela usa o dono já gravado no registro (o da recorrência), nunca esta interface.
 */
public interface CurrentPsychologist {

    UUID id();
}
