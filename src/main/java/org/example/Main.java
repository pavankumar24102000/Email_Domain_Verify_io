package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class Main {

    // High-confidence community-maintained disposable email blocklists.
    // We deliberately omit lists that include "fake/scam" domains (too noisy)
    // and stick to lists that focus on disposable/temporary mailboxes.
    private static final String[] DISPOSABLE_LISTS = {
        "https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/master/disposable_email_blocklist.conf",
        "https://raw.githubusercontent.com/martenson/disposable-email-domains/master/disposable_email_blocklist.conf",
        "https://raw.githubusercontent.com/disposable/disposable-email-domains/master/domains.txt"
    };

    // Hand-curated extras for disposable domains the public lists miss.
    // Add anything here that's clearly disposable but escaped detection.
    private static final Set<String> EXTRA_DISPOSABLE = new HashSet<>(Arrays.asList(
        "tempmail.edu.pl", "petalmail.com", "dummyinbox.com", "gamemail.vip",
        "youzimail.com", "clowtmail.com", "tmail.cl", "fast-temp-mail.info",
        "deepmails.org", "shieldedpost.net", "vertexinbox.com", "temailz.com",
        "mailinator.com", "10minutemail.com", "guerrillamail.com", "throwaway.email",
        "tempinbox.com", "tempr.email", "fakeinbox.com", "yopmail.com"
    ));

    // Suffix patterns - if a domain matches any of these, we treat as disposable.
    // Tuned to avoid false positives on real companies.
    private static final String[] DISPOSABLE_SUFFIX_PATTERNS = {
        "tempmail.", "temp-mail.", "tmpmail.", "trashmail.", "throwawaymail.",
        "fakemail.", "10minutemail.", "guerrillamail.", "mailinator.",
        "disposable.", "burnermail.", "yopmail."
    };

    // Well-known legitimate generic email providers.
    private static final Set<String> KNOWN_LEGIT = new HashSet<>(Arrays.asList(
        "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com",
        "yahoo.com", "yahoo.co.uk", "yahoo.co.in", "yahoo.com.au", "yahoo.com.sg",
        "yahoo.com.my", "yahoo.com.hk", "yahoo.com.tw", "yahoo.co.jp", "yahoo.co.kr",
        "yahoo.co.nz", "yahoo.co.id", "yahoo.de", "yahoo.fr", "yahoo.it", "yahoo.in",
        "yahoo.se", "yahoo.ca", "ymail.com", "rocketmail.com", "myyahoo.com",
        "icloud.com", "me.com", "mac.com", "aol.com", "msn.com", "live.co.uk",
        "live.com.au", "live.com.my", "live.fr", "live.ca", "live.cn", "live.com.pt",
        "live.com.sg", "outlook.com.au", "outlook.de", "outlook.in", "outlook.jp",
        "outlook.my", "hotmail.co.uk", "hotmail.co.jp", "hotmail.co.nz", "hotmail.co.th",
        "hotmail.com.au", "hotmail.com.tw", "hotmail.de", "hotmail.fr", "hotmail.it",
        "hotmail.my", "qq.com", "163.com", "126.com", "foxmail.com", "naver.com",
        "daum.net", "hanmail.net", "nate.com", "gmx.com", "gmx.de", "gmx.co.uk",
        "web.de", "mail.com", "fastmail.fm", "proton.me", "tutamail.com",
        "zohomail.com", "zohomail.eu", "zohocorp.com", "yandex.com", "ukr.net",
        "comcast.net", "verizon.net", "att.net", "btinternet.com", "btopenworld.com",
        "wanadoo.fr", "orange.fr", "free.fr", "laposte.net", "freenet.de", "arcor.de",
        "libero.it", "uol.com.br", "abv.bg", "seznam.cz", "telia.com", "tpg.com.au",
        "bigpond.com", "bigpond.net.au", "optusnet.com.au", "iinet.net.au",
        "internode.on.net", "westnet.com.au", "ozemail.com.au", "aussiebroadband.com.au",
        "xtra.co.nz", "talk21.com", "talktalk.net", "globalnet.co.uk", "doctors.org.uk",
        "ziggo.nl", "upcmail.nl", "caiway.nl", "online.no", "sunrise.ch",
        "consultant.com", "usa.com", "my.com", "startmail.com", "riseup.net",
        "tutanota.com"
    ));

    private static final Map<String, String> KNOWN_TYPOS = new HashMap<>();
    static {
        KNOWN_TYPOS.put("gmail.cim",  "gmail.com");
        KNOWN_TYPOS.put("gmail.con",  "gmail.com");
        KNOWN_TYPOS.put("gmail.cok",  "gmail.com");
        KNOWN_TYPOS.put("gmail.comd", "gmail.com");
        KNOWN_TYPOS.put("gmaill.com", "gmail.com");
        KNOWN_TYPOS.put("gmsil.com",  "gmail.com");
        KNOWN_TYPOS.put("gamil.com",  "gmail.com");
        KNOWN_TYPOS.put("gmaio.com",  "gmail.com");
        KNOWN_TYPOS.put("gmailk.com", "gmail.com");
        KNOWN_TYPOS.put("g.mail.com", "gmail.com");
        KNOWN_TYPOS.put("gmail.com.au", "gmail.com");
        KNOWN_TYPOS.put("gmail.com.my", "gmail.com");
        KNOWN_TYPOS.put("gmail.my.com", "gmail.com");
        KNOWN_TYPOS.put("hmail.com", "gmail.com");
        KNOWN_TYPOS.put("outlook.cm",  "outlook.com");
        KNOWN_TYPOS.put("outlook.con", "outlook.com");
        KNOWN_TYPOS.put("outlok.in",   "outlook.com");
        KNOWN_TYPOS.put("oulook.com",  "outlook.com");
        KNOWN_TYPOS.put("iutlook.com", "outlook.com");
        KNOWN_TYPOS.put("hotmail.con", "hotmail.com");
        KNOWN_TYPOS.put("hotmail.co.jk", "hotmail.co.jp");
        KNOWN_TYPOS.put("yahoo.con", "yahoo.com");
        KNOWN_TYPOS.put("icould.com", "icloud.com");
        KNOWN_TYPOS.put("qq.cpm", "qq.com");
        KNOWN_TYPOS.put("163.ckm", "163.com");
        KNOWN_TYPOS.put("bigpong.com", "bigpond.com");
        KNOWN_TYPOS.put("bigpond.net.su", "bigpond.net.au");
        KNOWN_TYPOS.put("crowns-hk.cpm", "crowns-hk.com");
        KNOWN_TYPOS.put("infineon.con", "infineon.com");
        KNOWN_TYPOS.put("yahoo.con", "yahoo.com");
        KNOWN_TYPOS.put("dummyinbox.xom", "dummyinbox.com");
    }

    private static final Pattern DOMAIN_RE =
            Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$");

    public static void main(String[] args) throws Exception {
        String filePath = "Domain.xlsx";

        System.out.println("Downloading disposable-domain blocklists...");
        Set<String> disposable = downloadDisposableDomains();
        disposable.addAll(EXTRA_DISPOSABLE);
        System.out.println("  Total disposable domains loaded: " + disposable.size() + "\n");

        Workbook workbook;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            workbook = new XSSFWorkbook(fis);
        }

        Sheet inputSheet = workbook.getSheetAt(0);
        int existingResults = workbook.getSheetIndex("Results");
        if (existingResults != -1) workbook.removeSheetAt(existingResults);
        Sheet outputSheet = workbook.createSheet("Results");

        DataFormatter formatter = new DataFormatter();

        CellStyle greenStyle = makeStyle(workbook, IndexedColors.LIGHT_GREEN);
        CellStyle paleGreenStyle = makeStyle(workbook, IndexedColors.LIGHT_TURQUOISE);
        CellStyle redStyle = makeStyle(workbook, IndexedColors.ROSE);
        CellStyle yellowStyle = makeStyle(workbook, IndexedColors.LIGHT_YELLOW);

        Row header = outputSheet.createRow(0);
        header.createCell(0).setCellValue("Domain");
        header.createCell(1).setCellValue("Status");
        header.createCell(2).setCellValue("Reason");

        // Read all input domains first
        List<String> domains = new ArrayList<>();
        for (int i = 1; i <= inputSheet.getLastRowNum(); i++) {
            Row row = inputSheet.getRow(i);
            if (row == null) continue;
            Cell cell = row.getCell(0);
            if (cell == null) continue;
            String raw = formatter.formatCellValue(cell).trim().toLowerCase();
            if (raw.isEmpty()) continue;
            if (raw.startsWith("row label") || raw.startsWith("grand total")) continue;
            String domain = raw.contains("@") ? raw.substring(raw.indexOf('@') + 1) : raw;
            domain = domain.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
            domains.add(domain);
        }

        // First pass: classify everything we can without DNS
        // Collect domains that need MX lookup
        Map<String, String[]> initialResults = new LinkedHashMap<>(); // domain -> [status, reason]
        Set<String> needsMxCheck = new LinkedHashSet<>();

        for (String domain : domains) {
            String[] r = classifyOffline(domain, disposable);
            initialResults.put(domain, r);
            if (r[0].equals("UNKNOWN")) {
                needsMxCheck.add(domain);
            }
        }

        // Second pass: parallel MX lookups for the unknown ones
        System.out.println("Performing MX lookups for " + needsMxCheck.size() + " unknown domains...");
        Map<String, Boolean> mxResults = parallelMxLookups(needsMxCheck);
        System.out.println("MX lookups complete.\n");

        // Now write the output
        System.out.println("=============================================================");
        System.out.printf("%-40s %-15s %-30s%n", "Domain", "Status", "Reason");
        System.out.println("=============================================================");

        int outputRowIndex = 1;
        int safe = 0, validDomain = 0, bad = 0, unknown = 0;

        for (Map.Entry<String, String[]> e : initialResults.entrySet()) {
            String domain = e.getKey();
            String status = e.getValue()[0];
            String reason = e.getValue()[1];
            CellStyle style;

            // If we did an MX check on this one, refine the result
            if (status.equals("UNKNOWN") && mxResults.containsKey(domain)) {
                if (mxResults.get(domain)) {
                    status = "VALID DOMAIN";
                    reason = "Has working MX record (real email-capable domain)";
                } else {
                    status = "NO MX";
                    reason = "No MX record - domain cannot receive email";
                }
            }

            switch (status) {
                case "SAFE":          style = greenStyle;     safe++; break;
                case "VALID DOMAIN":  style = paleGreenStyle; validDomain++; break;
                case "DISPOSABLE":
                case "TYPO":
                case "INVALID":
                case "NO MX":         style = redStyle;       bad++; break;
                default:              style = yellowStyle;    unknown++; break;
            }

            Row outRow = outputSheet.createRow(outputRowIndex++);
            outRow.createCell(0).setCellValue(domain);
            Cell statusCell = outRow.createCell(1);
            statusCell.setCellValue(status);
            statusCell.setCellStyle(style);
            outRow.createCell(2).setCellValue(reason);

            System.out.printf("%-40s %-15s %-30s%n", domain, status, reason);
        }

        outputSheet.setColumnWidth(0, 10000);
        outputSheet.setColumnWidth(1, 4500);
        outputSheet.setColumnWidth(2, 16000);

        System.out.println("=============================================================");
        System.out.println("SUMMARY");
        System.out.println("=============================================================");
        System.out.printf("  SAFE          : %d  (known good providers)%n", safe);
        System.out.printf("  VALID DOMAIN  : %d  (real companies with working email)%n", validDomain);
        System.out.printf("  BAD           : %d  (disposable/typo/invalid/no MX)%n", bad);
        System.out.printf("  UNKNOWN       : %d  (manual review)%n", unknown);
        System.out.printf("  Total         : %d%n", (safe + validDomain + bad + unknown));
        System.out.println("=============================================================");

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
        System.out.println("Output saved to: " + filePath);
    }

    private static CellStyle makeStyle(Workbook wb, IndexedColors color) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static String[] classifyOffline(String domain, Set<String> disposable) {
        if (!DOMAIN_RE.matcher(domain).matches()) {
            return new String[]{"INVALID", "Bad domain syntax"};
        }
        if (KNOWN_TYPOS.containsKey(domain)) {
            return new String[]{"TYPO", "Likely typo of " + KNOWN_TYPOS.get(domain)};
        }
        if (disposable.contains(domain)) {
            return new String[]{"DISPOSABLE", "On disposable-domain blocklist"};
        }
        for (String suffix : DISPOSABLE_SUFFIX_PATTERNS) {
            if (domain.contains(suffix)) {
                return new String[]{"DISPOSABLE", "Matches disposable-domain pattern"};
            }
        }
        if (KNOWN_LEGIT.contains(domain)) {
            return new String[]{"SAFE", "Known legitimate provider"};
        }
        return new String[]{"UNKNOWN", "Pending MX check"};
    }

    private static Set<String> downloadDisposableDomains() {
        Set<String> all = new HashSet<>();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        for (String url : DISPOSABLE_LISTS) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    int before = all.size();
                    for (String line : resp.body().split("\\r?\\n")) {
                        String d = line.trim().toLowerCase();
                        if (!d.isEmpty() && !d.startsWith("#") && !d.startsWith("//")) {
                            all.add(d);
                        }
                    }
                    System.out.println("  + " + (all.size() - before) + " from " + url);
                } else {
                    System.out.println("  ! HTTP " + resp.statusCode() + " from " + url);
                }
            } catch (Exception e) {
                System.out.println("  ! Failed: " + url + " (" + e.getMessage() + ")");
            }
        }
        return all;
    }

    /** Parallel MX lookups across many domains. Returns a map domain -> hasMx. */
    private static Map<String, Boolean> parallelMxLookups(Collection<String> domains) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();

        for (String d : domains) {
            futures.add(pool.submit(() -> {
                results.put(d, hasMxRecord(d));
            }));
        }
        pool.shutdown();
        for (Future<?> f : futures) {
            try { f.get(15, TimeUnit.SECONDS); } catch (Exception ignore) {}
        }
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (Exception ignore) {}
        return results;
    }

    /** True if the domain has any MX (or fallback A) record. */
    private static boolean hasMxRecord(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put(Context.PROVIDER_URL, "dns://8.8.8.8 dns://1.1.1.1");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "2");

            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute mx = attrs.get("MX");
            ctx.close();
            if (mx != null && mx.size() > 0) return true;
        } catch (Exception ignore) {}

        // Fallback: some domains accept email without explicit MX (uses A record)
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put(Context.PROVIDER_URL, "dns://8.8.8.8 dns://1.1.1.1");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "2");

            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"A"});
            Attribute a = attrs.get("A");
            ctx.close();
            return a != null && a.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
