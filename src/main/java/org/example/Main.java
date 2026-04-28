package org.example;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import java.awt.Color;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class Main {

    // ---------- Domain lists ----------

    private static final String[] DISPOSABLE_LISTS = {
        "https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/master/disposable_email_blocklist.conf",
        "https://raw.githubusercontent.com/martenson/disposable-email-domains/master/disposable_email_blocklist.conf",
        "https://raw.githubusercontent.com/disposable/disposable-email-domains/master/domains.txt"
    };

    private static final Set<String> EXTRA_DISPOSABLE = new HashSet<>(Arrays.asList(
        "tempmail.edu.pl", "petalmail.com", "dummyinbox.com", "gamemail.vip",
        "youzimail.com", "clowtmail.com", "tmail.cl", "fast-temp-mail.info",
        "deepmails.org", "shieldedpost.net", "vertexinbox.com", "temailz.com",
        "mailinator.com", "10minutemail.com", "guerrillamail.com", "throwaway.email",
        "tempinbox.com", "tempr.email", "fakeinbox.com", "yopmail.com"
    ));

    private static final String[] DISPOSABLE_SUFFIX_PATTERNS = {
        "tempmail.", "temp-mail.", "tmpmail.", "trashmail.", "throwawaymail.",
        "fakemail.", "10minutemail.", "guerrillamail.", "mailinator.",
        "burnermail.", "yopmail."
    };

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

    // Hand-curated typos. Removed hmail.com (real domain).
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
        KNOWN_TYPOS.put("infineon.con", "infineon.com");
        KNOWN_TYPOS.put("dummyinbox.xom", "dummyinbox.com");
    }

    // Common gibberish TLDs that indicate a typo of .com / .net etc.
    private static final Set<String> GIBBERISH_TLDS = new HashSet<>(Arrays.asList(
        "cpm", "ckm", "comd", "comm", "ocm", "xom", "vom", "con", "cim", "cok",
        "cm" // .cm is a real TLD (Cameroon) but rarely intentional
    ));

    private static final Pattern DOMAIN_RE =
            Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$");

    public static void main(String[] args) throws Exception {
        String inputFile  = "Domain.xlsx";
        String csvFile    = "Domain.csv";
        String pdfFile    = "Domain.pdf";

        log("Loading blocklists...");
        Set<String> disposable = downloadDisposableDomains();
        disposable.addAll(EXTRA_DISPOSABLE);
        log("Disposable domains loaded: " + disposable.size());

        Workbook workbook;
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            workbook = new XSSFWorkbook(fis);
        }

        Sheet inputSheet = workbook.getSheetAt(0);
        DataFormatter formatter = new DataFormatter();

        // Read all input domains
        List<String> domains = new ArrayList<>();
        for (int i = 1; i <= inputSheet.getLastRowNum(); i++) {
            Row row = inputSheet.getRow(i);
            if (row == null) continue;
            Cell cell = row.getCell(0);
            if (cell == null) continue;
            String raw = formatter.formatCellValue(cell).trim().toLowerCase();
            if (raw.isEmpty()) continue;
            if (raw.startsWith("row label") || raw.startsWith("grand total")) continue;
            String d = raw.contains("@") ? raw.substring(raw.indexOf('@') + 1) : raw;
            d = d.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
            domains.add(d);
        }
        log("Domains to classify: " + domains.size());

        // First pass — offline classification
        Map<String, String[]> results = new LinkedHashMap<>();
        Set<String> needsMx = new LinkedHashSet<>();
        for (String d : domains) {
            String[] r = classifyOffline(d, disposable);
            results.put(d, r);
            if (r[0].equals("UNKNOWN")) needsMx.add(d);
        }

        // Edit-distance typo pass: any still-UNKNOWN domain within edit distance 2
        // of a SAFE domain gets flagged as TYPO before MX is even checked.
        log("Running edit-distance typo detection...");
        for (String d : new ArrayList<>(needsMx)) {
            String suggestion = nearestSafeDomain(d);
            if (suggestion != null) {
                results.put(d, new String[]{"TYPO", "Likely typo of " + suggestion});
                needsMx.remove(d);
            }
        }

        // MX lookup pass for what remains UNKNOWN
        log("MX lookups for " + needsMx.size() + " domains...");
        Map<String, Boolean> mx = parallelMxLookups(needsMx);

        for (Map.Entry<String, Boolean> e : mx.entrySet()) {
            if (e.getValue()) {
                results.put(e.getKey(), new String[]{"VALID DOMAIN", "Has working MX/A record"});
            } else {
                results.put(e.getKey(), new String[]{"NO MX", "No mail records found"});
            }
        }

        // Final tallies
        int safe = 0, validDomain = 0, bad = 0, unknown = 0;
        for (String[] r : results.values()) {
            switch (r[0]) {
                case "SAFE":          safe++; break;
                case "VALID DOMAIN":  validDomain++; break;
                case "DISPOSABLE":
                case "TYPO":
                case "INVALID":
                case "NO MX":         bad++; break;
                default:              unknown++; break;
            }
        }

        // Write Excel (overwrite Domain.xlsx with Results sheet)
        writeExcel(workbook, inputFile, results);

        // Write CSV
        writeCsv(csvFile, results);

        // Write PDF
        writePdf(pdfFile, results, safe, validDomain, bad, unknown);

        // Logs: only summary, never per-domain
        log("");
        log("=========== SUMMARY ===========");
        log("SAFE          : " + safe);
        log("VALID DOMAIN  : " + validDomain);
        log("BAD           : " + bad);
        log("UNKNOWN       : " + unknown);
        log("Total         : " + (safe + validDomain + bad + unknown));
        log("================================");
        log("Files written: " + inputFile + ", " + csvFile + ", " + pdfFile);
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    // ---------- Classification ----------

    private static String[] classifyOffline(String domain, Set<String> disposable) {
        if (!DOMAIN_RE.matcher(domain).matches()) {
            return new String[]{"INVALID", "Bad domain syntax"};
        }
        if (KNOWN_TYPOS.containsKey(domain)) {
            return new String[]{"TYPO", "Likely typo of " + KNOWN_TYPOS.get(domain)};
        }
        // Gibberish TLD detection
        int lastDot = domain.lastIndexOf('.');
        if (lastDot != -1) {
            String tld = domain.substring(lastDot + 1);
            if (GIBBERISH_TLDS.contains(tld)) {
                return new String[]{"TYPO", "Suspicious TLD '." + tld + "' (likely typo)"};
            }
        }
        if (disposable.contains(domain)) {
            return new String[]{"DISPOSABLE", "On disposable-domain blocklist"};
        }
        for (String suffix : DISPOSABLE_SUFFIX_PATTERNS) {
            if (domain.contains(suffix)) {
                return new String[]{"DISPOSABLE", "Matches disposable pattern"};
            }
        }
        if (KNOWN_LEGIT.contains(domain)) {
            return new String[]{"SAFE", "Known legitimate provider"};
        }
        return new String[]{"UNKNOWN", "Pending checks"};
    }

    /** Returns nearest SAFE domain within edit distance 2, or null. */
    private static String nearestSafeDomain(String domain) {
        String best = null;
        int bestDist = 3;
        for (String safe : KNOWN_LEGIT) {
            // Skip if length difference is already > 2 (early prune)
            if (Math.abs(safe.length() - domain.length()) > 2) continue;
            int d = levenshtein(domain, safe);
            if (d > 0 && d < bestDist) {
                bestDist = d;
                best = safe;
            }
        }
        return (bestDist <= 2) ? best : null;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }

    private static Set<String> downloadDisposableDomains() {
        Set<String> all = new HashSet<>();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        for (String url : DISPOSABLE_LISTS) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    for (String line : resp.body().split("\\r?\\n")) {
                        String d = line.trim().toLowerCase();
                        if (!d.isEmpty() && !d.startsWith("#") && !d.startsWith("//")) {
                            all.add(d);
                        }
                    }
                }
            } catch (Exception ignore) {}
        }
        return all;
    }

    private static Map<String, Boolean> parallelMxLookups(Collection<String> domains) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();
        for (String d : domains) {
            futures.add(pool.submit(() -> results.put(d, hasMxRecord(d))));
        }
        pool.shutdown();
        for (Future<?> f : futures) {
            try { f.get(15, TimeUnit.SECONDS); } catch (Exception ignore) {}
        }
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (Exception ignore) {}
        return results;
    }

    private static boolean hasMxRecord(String domain) {
        for (String type : new String[]{"MX", "A"}) {
            try {
                Hashtable<String, String> env = new Hashtable<>();
                env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
                env.put(Context.PROVIDER_URL, "dns://8.8.8.8 dns://1.1.1.1");
                env.put("com.sun.jndi.dns.timeout.initial", "3000");
                env.put("com.sun.jndi.dns.timeout.retries", "2");
                DirContext ctx = new InitialDirContext(env);
                Attributes attrs = ctx.getAttributes(domain, new String[]{type});
                Attribute attr = attrs.get(type);
                ctx.close();
                if (attr != null && attr.size() > 0) return true;
            } catch (Exception ignore) {}
        }
        return false;
    }

    // ---------- Output writers ----------

    private static void writeExcel(Workbook wb, String path, Map<String, String[]> results)
            throws IOException {
        int idx = wb.getSheetIndex("Results");
        if (idx != -1) wb.removeSheetAt(idx);
        Sheet s = wb.createSheet("Results");

        CellStyle green     = makeStyle(wb, IndexedColors.LIGHT_GREEN);
        CellStyle paleGreen = makeStyle(wb, IndexedColors.LIGHT_TURQUOISE);
        CellStyle red       = makeStyle(wb, IndexedColors.ROSE);
        CellStyle yellow    = makeStyle(wb, IndexedColors.LIGHT_YELLOW);

        Row header = s.createRow(0);
        header.createCell(0).setCellValue("Domain");
        header.createCell(1).setCellValue("Status");
        header.createCell(2).setCellValue("Reason");

        int r = 1;
        for (Map.Entry<String, String[]> e : results.entrySet()) {
            Row row = s.createRow(r++);
            row.createCell(0).setCellValue(e.getKey());
            Cell statusCell = row.createCell(1);
            statusCell.setCellValue(e.getValue()[0]);
            statusCell.setCellStyle(styleFor(e.getValue()[0], green, paleGreen, red, yellow));
            row.createCell(2).setCellValue(e.getValue()[1]);
        }
        s.setColumnWidth(0, 10000);
        s.setColumnWidth(1, 4500);
        s.setColumnWidth(2, 16000);

        try (FileOutputStream fos = new FileOutputStream(path)) {
            wb.write(fos);
        }
        wb.close();
    }

    private static void writeCsv(String path, Map<String, String[]> results) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("Domain,Status,Reason");
            for (Map.Entry<String, String[]> e : results.entrySet()) {
                pw.println(csvEscape(e.getKey()) + "," + csvEscape(e.getValue()[0]) + "," + csvEscape(e.getValue()[1]));
            }
        }
    }

    private static String csvEscape(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static void writePdf(String path, Map<String, String[]> results,
                                 int safe, int valid, int bad, int unknown) throws Exception {
        Document doc = new Document(PageSize.A4, 36, 36, 48, 48);
        PdfWriter.getInstance(doc, new FileOutputStream(path));
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font h2Font    = new Font(Font.HELVETICA, 13, Font.BOLD);
        Font bodyFont  = new Font(Font.HELVETICA, 10);
        Font small     = new Font(Font.HELVETICA, 9);

        Paragraph title = new Paragraph("Domain Verification Report", titleFont);
        title.setSpacingAfter(4);
        doc.add(title);

        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"));
        Paragraph subtitle = new Paragraph("Generated: " + when, small);
        subtitle.setSpacingAfter(16);
        doc.add(subtitle);

        // Summary table
        doc.add(new Paragraph("Summary", h2Font));
        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(60);
        summary.setSpacingBefore(6);
        summary.setSpacingAfter(20);
        summary.setHorizontalAlignment(Element.ALIGN_LEFT);

        addSummaryRow(summary, "SAFE (known providers)",        String.valueOf(safe),        new Color(220, 252, 231));
        addSummaryRow(summary, "VALID DOMAIN (real companies)", String.valueOf(valid),       new Color(207, 250, 254));
        addSummaryRow(summary, "BAD (disposable/typo/no-MX)",   String.valueOf(bad),         new Color(254, 226, 226));
        addSummaryRow(summary, "UNKNOWN (manual review)",       String.valueOf(unknown),     new Color(254, 249, 195));
        addSummaryRow(summary, "TOTAL",                         String.valueOf(safe + valid + bad + unknown),
                new Color(241, 245, 249));
        doc.add(summary);

        // Full table
        doc.add(new Paragraph("Full Results", h2Font));
        PdfPTable table = new PdfPTable(new float[]{4, 2, 5});
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setHeaderRows(1);

        for (String h : new String[]{"Domain", "Status", "Reason"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 10, Font.BOLD)));
            c.setBackgroundColor(new Color(241, 245, 249));
            c.setPadding(6);
            table.addCell(c);
        }

        for (Map.Entry<String, String[]> e : results.entrySet()) {
            String status = e.getValue()[0];
            Color bg = colorFor(status);
            table.addCell(makeCell(e.getKey(), bodyFont, bg));
            table.addCell(makeCell(status, bodyFont, bg));
            table.addCell(makeCell(e.getValue()[1], bodyFont, bg));
        }
        doc.add(table);

        doc.close();
    }

    private static void addSummaryRow(PdfPTable t, String label, String count, Color bg) {
        Font labelFont = new Font(Font.HELVETICA, 11);
        Font countFont = new Font(Font.HELVETICA, 11, Font.BOLD);
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell c = new PdfPCell(new Phrase(count, countFont));
        l.setBackgroundColor(bg); c.setBackgroundColor(bg);
        l.setPadding(6); c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l); t.addCell(c);
    }

    private static PdfPCell makeCell(String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setPadding(4);
        return c;
    }

    private static Color colorFor(String status) {
        switch (status) {
            case "SAFE":         return new Color(220, 252, 231);
            case "VALID DOMAIN": return new Color(207, 250, 254);
            case "DISPOSABLE":
            case "TYPO":
            case "INVALID":
            case "NO MX":        return new Color(254, 226, 226);
            default:             return new Color(254, 249, 195);
        }
    }

    private static CellStyle makeStyle(Workbook wb, IndexedColors color) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static CellStyle styleFor(String status, CellStyle green, CellStyle paleGreen,
                                       CellStyle red, CellStyle yellow) {
        switch (status) {
            case "SAFE":         return green;
            case "VALID DOMAIN": return paleGreen;
            case "DISPOSABLE":
            case "TYPO":
            case "INVALID":
            case "NO MX":        return red;
            default:             return yellow;
        }
    }
}
