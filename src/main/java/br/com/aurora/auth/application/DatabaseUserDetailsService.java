package br.com.aurora.auth.application;

import br.com.aurora.auth.domain.AppUser;
import br.com.aurora.auth.persistence.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca a conta pelo e-mail (normalizado) para o login. A mensagem de "não encontrada" é fixa:
 * nunca inclui o e-mail digitado, e o Spring responde igual para conta inexistente e senha errada.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public DatabaseUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByEmail(AppUser.normalizeEmail(username))
                .map(AuroraUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada."));
    }
}
