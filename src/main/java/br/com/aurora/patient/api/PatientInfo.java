package br.com.aurora.patient.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O que outros módulos podem saber de um paciente. O nome existe para montar telas;
 * por isso o toString o omite, para que ele não vaze em logs.
 */
public record PatientInfo(UUID id, String name, BigDecimal sessionPrice, boolean active) {

    @Override
    public String toString() {
        return "PatientInfo[id=" + id + ", active=" + active + "]";
    }
}
