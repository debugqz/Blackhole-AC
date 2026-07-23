package com.blackhole.check;

public final class CheckResult {

    private static final CheckResult CLEAN = new CheckResult(false, 0.0, null);

    private final boolean violated;
    private final double vlAmount;
    private final String details;

    private CheckResult(boolean violated, double vlAmount, String details) {
        this.violated = violated;
        this.vlAmount = vlAmount;
        this.details = details;
    }

    public static CheckResult clean() {
        return CLEAN;
    }

    public static CheckResult violation(double vlAmount, String details) {
        return new CheckResult(true, vlAmount, details);
    }

    public boolean isViolated() {
        return violated;
    }

    public double getVlAmount() {
        return vlAmount;
    }

    public String getDetails() {
        return details;
    }
}
