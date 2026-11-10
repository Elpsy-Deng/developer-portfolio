package dev.jackteng.portfolio.operations;

/** Docker readiness needs a real PG-backed response, without another runtime package. */
public final class HealthProbe {
    private HealthProbe() {}
    public static void main(String[] args) throws Exception {
        var connection = (java.net.HttpURLConnection) java.net.URI.create("http://127.0.0.1:19186/api/public/health").toURL().openConnection();
        connection.setConnectTimeout(2000); connection.setReadTimeout(4000);
        try { if (connection.getResponseCode() != 200) System.exit(1); }
        finally { connection.disconnect(); }
    }
}
