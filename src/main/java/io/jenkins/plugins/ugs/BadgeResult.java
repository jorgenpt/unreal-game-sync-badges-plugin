package io.jenkins.plugins.ugs;

public enum BadgeResult {
    STARTING("Starting", 0),
    FAILURE("Failure", 1),
    WARNING("Warning", 2),
    SUCCESS("Success", 3),
    SKIPPED("Skipped", 4);

    private final String displayName;
    private final int ugsValue;

    BadgeResult(String displayName, int ugsValue) {
        this.displayName = displayName;
        this.ugsValue = ugsValue;
    }

    public String getDisplayName() {
        return displayName;
    }
    public int getUGSValue() {
        return ugsValue;
    }
}
