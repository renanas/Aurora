package br.com.aurora.scheduling.domain;

public enum Frequency {
    WEEKLY(7),
    BIWEEKLY(14);

    private final int stepDays;

    Frequency(int stepDays) {
        this.stepDays = stepDays;
    }

    public int stepDays() {
        return stepDays;
    }
}
