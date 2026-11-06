package br.com.aurora.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Conta de usuária. O e-mail é o login e fica sempre normalizado (sem espaços nas pontas, em
 * minúsculas), para "Ana@X.com " e "ana@x.com" serem a mesma conta.
 * As mensagens de erro nunca incluem o e-mail nem a senha.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_NAME_LENGTH = 150;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    @Id
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
        // JPA
    }

    public static AppUser create(String email, String passwordHash, String displayName, Role role,
            boolean mustChangePassword, Instant now) {
        String normalized = normalizeEmail(email);
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Nome inválido.");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Hash de senha obrigatório.");
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(now, "now");

        AppUser user = new AppUser();
        user.id = UUID.randomUUID();
        user.email = normalized;
        user.passwordHash = passwordHash;
        user.displayName = name;
        user.role = role;
        user.active = true;
        user.mustChangePassword = mustChangePassword;
        user.createdAt = now;
        return user;
    }

    /** Forma única do e-mail: sem espaços nas pontas e em minúsculas. Nulo vira texto vazio. */
    public static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    /** Conta desativada não entra mais; os dados continuam no banco. */
    public void deactivate() {
        this.active = false;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
