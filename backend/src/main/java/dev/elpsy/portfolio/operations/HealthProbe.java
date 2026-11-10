package dev.elpsy.portfolio.operations;

/**
 * Transitional entry point: the deployed Compose files still start this class name.
 * Delegates to {@link dev.jackteng.portfolio.operations.HealthProbe}; remove once the deployment
 * configuration references the new package (controlled switch, see server operations).
 */
@Deprecated(forRemoval = true)
public final class HealthProbe {
    private HealthProbe() {}

    public static void main(String[] args) throws Exception {
        dev.jackteng.portfolio.operations.HealthProbe.main(args);
    }
}
