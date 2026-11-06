package br.com.aurora.auth.persistence;

import br.com.aurora.auth.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** O e-mail já deve vir normalizado ({@link AppUser#normalizeEmail}). */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
