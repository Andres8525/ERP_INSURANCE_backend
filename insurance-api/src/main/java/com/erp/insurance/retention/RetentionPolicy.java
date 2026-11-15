package com.erp.insurance.retention;

import com.erp.insurance.retention.RetentionDtos.RetentionDecision;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

record RetentionPolicy(int archiveAfterMonths, int retentionYears) {

    record Verdict(RetentionDecision decision, String reason, int inactiveMonths) {}

    Verdict evaluate(boolean inUse, LocalDate lastActivity, String holdReason, LocalDate today) {
        int inactiveMonths = lastActivity == null || lastActivity.isAfter(today)
                ? 0 : (int) ChronoUnit.MONTHS.between(lastActivity, today);
        if (inUse) return new Verdict(RetentionDecision.KEEP, "Registro vigente o en trámite", inactiveMonths);
        if (holdReason != null) return new Verdict(RetentionDecision.LEGAL_HOLD, holdReason, inactiveMonths);
        if (inactiveMonths >= retentionYears * 12) {
            return new Verdict(RetentionDecision.DELETE,
                    "Plazo de retención de " + retentionYears + " años cumplido: eliminar definitivamente", inactiveMonths);
        }
        if (inactiveMonths >= archiveAfterMonths) {
            return new Verdict(RetentionDecision.ARCHIVE,
                    "Sin actividad hace " + describe(inactiveMonths) + ": mover a almacenamiento de archivo", inactiveMonths);
        }
        return new Verdict(RetentionDecision.KEEP, "Actividad reciente", inactiveMonths);
    }

    static String describe(int months) {
        if (months < 24) return months + " meses";
        int years = months / 12;
        return years + " años";
    }
}
