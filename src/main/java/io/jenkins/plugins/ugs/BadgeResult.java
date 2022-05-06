package io.jenkins.plugins.ugs;


/**
 * Controls when the slack notification gets sent in case of the matrix project.
 */
public enum BadgeResult {
    STARTING("Starting"),
    FAILURE("Failure"),
    WARNING("Warning"),
    SUCCESS("Success"),
    SKIPPED("Skipped");

    private final String ugsString;

    BadgeResult(String ugsString) {
        this.ugsString = ugsString;
    }

    public String getUGSString() {
        return ugsString;
    }
}
