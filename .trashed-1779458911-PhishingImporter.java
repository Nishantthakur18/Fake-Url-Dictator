import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

/**
 * ============================================================
 * FakeShield — Phishing Database Importer
 * ============================================================
 * This tool downloads the Phishing.Database from GitHub
 * and imports it directly into your MySQL database.
 *
 * Source: https://github.com/Phishing-Database/Phishing.Database
 * License: MIT — Free & Open Source
 *
 * HOW TO RUN:
 *   1. Open Command Prompt (CMD) in this folder
 *   2. Compile:  javac PhishingImporter.java
 *   3. Run:      java PhishingImporter
 * ============================================================
 */
public class PhishingImporter {

    // ── CONFIG — change these to match your setup ──────────────
    static final String DB_URL  = "jdbc:mysql://localhost:3306/fake_detector_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    static final String DB_USER = "root";
    static final String DB_PASS = "Shivam@4612"; // ← PUT YOUR PASSWORD HERE

    // GitHub raw file URLs
    static final String DOMAINS_URL = "https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt";
    static final String LINKS_URL   = "https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-links-ACTIVE.txt";

    static final int BATCH_SIZE = 500;

    // ── MAIN ───────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        System.out.println("============================================");
        System.out.println("  FakeShield — Phishing Database Importer  ");
        System.out.println("============================================");
        System.out.println("Source: github.com/Phishing-Database");
        System.out.println();

        // Step 1: Create table if not exists
        System.out.println("[1/4] Connecting to MySQL...");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            System.out.println("      ✅ Connected!");
            createTable(conn);

            // Step 2: Import domains
            System.out.println("[2/4] Downloading phishing DOMAINS from GitHub...");
            int domains = importFromUrl(conn, DOMAINS_URL, "DOMAIN");
            System.out.println("      ✅ Imported " + domains + " phishing domains!");

            // Step 3: Import links
            System.out.println("[3/4] Downloading phishing LINKS from GitHub...");
            int links = importFromUrl(conn, LINKS_URL, "URL");
            System.out.println("      ✅ Imported " + links + " phishing links!");

            // Step 4: Show stats
            System.out.println("[4/4] Checking database totals...");
            showStats(conn);

            System.out.println();
            System.out.println("============================================");
            System.out.println("  ✅ IMPORT COMPLETE!");
            System.out.println("  Total: " + (domains + links) + " phishing entries");
            System.out.println("  Your FakeShield app will now detect these!");
            System.out.println("============================================");
        }
    }

    // ── Create phishing_entries table ─────────────────────────
    static void createTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS phishing_entries (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                value       VARCHAR(2048) NOT NULL,
                domain      VARCHAR(500)  DEFAULT NULL,
                entry_type  ENUM('DOMAIN','URL') NOT NULL,
                source      VARCHAR(100) DEFAULT 'Phishing.Database/GitHub',
                active      BOOLEAN DEFAULT TRUE,
                imported_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uq_value (value(512)),
                INDEX idx_domain (domain(200)),
                INDEX idx_type   (entry_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
            System.out.println("      ✅ Table ready!");
        }
    }

    // ── Download file and batch-insert into MySQL ──────────────
    static int importFromUrl(Connection conn, String fileUrl, String entryType) throws Exception {
        URL url = new URI(fileUrl).toURL();
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestMethod("GET");
        http.setConnectTimeout(15000);
        http.setReadTimeout(120000);
        http.setRequestProperty("User-Agent", "FakeShield-Importer/1.0");

        if (http.getResponseCode() != 200) {
            throw new RuntimeException("HTTP " + http.getResponseCode() + " — Cannot reach GitHub");
        }

        String insertSql = "INSERT IGNORE INTO phishing_entries (value, domain, entry_type) VALUES (?, ?, ?)";
        conn.setAutoCommit(false);

        int count = 0;
        List<String[]> batch = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(http.getInputStream()));
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String domain = extractDomain(line);
                batch.add(new String[]{line, domain, entryType});
                count++;

                if (batch.size() >= BATCH_SIZE) {
                    executeBatch(ps, batch, conn);
                    batch.clear();
                    if (count % 50000 == 0) {
                        System.out.println("      ... " + count + " entries processed so far");
                    }
                }
            }

            // insert remaining
            if (!batch.isEmpty()) {
                executeBatch(ps, batch, conn);
            }
            conn.commit();
        }

        conn.setAutoCommit(true);
        http.disconnect();
        return count;
    }

    static void executeBatch(PreparedStatement ps, List<String[]> batch, Connection conn) throws SQLException {
        for (String[] row : batch) {
            ps.setString(1, row[0]);
            ps.setString(2, row[1]);
            ps.setString(3, row[2]);
            ps.addBatch();
        }
        ps.executeBatch();
        conn.commit();
    }

    // ── Show stats after import ────────────────────────────────
    static void showStats(Connection conn) throws SQLException {
        String sql = "SELECT entry_type, COUNT(*) as cnt FROM phishing_entries GROUP BY entry_type";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            long total = 0;
            while (rs.next()) {
                long cnt = rs.getLong("cnt");
                System.out.println("      " + rs.getString("entry_type") + ": " + String.format("%,d", cnt));
                total += cnt;
            }
            System.out.println("      TOTAL: " + String.format("%,d", total) + " entries");
        }
    }

    // ── Extract root domain from URL or raw domain ─────────────
    static String extractDomain(String value) {
        try {
            if (!value.startsWith("http")) value = "https://" + value;
            URI uri = new URI(value);
            String host = uri.getHost();
            if (host == null) return value;
            return host.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return value;
        }
    }
}
