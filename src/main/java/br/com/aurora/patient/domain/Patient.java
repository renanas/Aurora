package br.com.aurora.patient.domain;

import br.com.aurora.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** Quem é atendido. Nunca é apagado: só inativado. */
@Entity
@Table(name = "patient")
public class Patient {

    private static final int MAX_NAME_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String whatsapp;

    @Column(name = "session_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal sessionPrice;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Patient() {
    }

    public Patient(String name, String whatsapp, BigDecimal sessionPrice, Instant now) {
        this.name = normalizeName(name);
        this.whatsapp = Whatsapp.of(whatsapp).digits();
        this.sessionPrice = normalizePrice(sessionPrice);
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Atualiza os dados. Retorna true se o valor da sessão mudou. */
    public boolean update(String name, String whatsapp, BigDecimal sessionPrice, Instant now) {
        String newName = normalizeName(name);
        String newWhatsapp = Whatsapp.of(whatsapp).digits();
        BigDecimal newPrice = normalizePrice(sessionPrice);

        boolean priceChanged = this.sessionPrice.compareTo(newPrice) != 0;
        this.name = newName;
        this.whatsapp = newWhatsapp;
        this.sessionPrice = newPrice;
        this.updatedAt = now;
        return priceChanged;
    }

    /** Retorna true se houve mudança (inativar um paciente já inativo não faz nada). */
    public boolean deactivate(Instant now) {
        if (!active) {
            return false;
        }
        active = false;
        updatedAt = now;
        return true;
    }

    /** Retorna true se houve mudança. */
    public boolean activate(Instant now) {
        if (active) {
            return false;
        }
        active = true;
        updatedAt = now;
        return true;
    }

    private static String normalizeName(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleException("INVALID_PATIENT_NAME", "O nome deve ter de 1 a 150 caracteres.");
        }
        return trimmed;
    }

    private static BigDecimal normalizePrice(BigDecimal raw) {
        if (raw == null || raw.signum() < 0) {
            throw new BusinessRuleException("INVALID_SESSION_PRICE", "O valor da sessão não pode ser negativo.");
        }
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public BigDecimal getSessionPrice() {
        return sessionPrice;
    }

    public boolean isActive() {
        return active;
    }
}
