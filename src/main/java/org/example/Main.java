package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

public class Main {

    // Public, community-maintained disposable email domain lists.
    // These are the same lists most paid services use under the hood.
    private static final String[] DISPOSABLE_LISTS = {
        "https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/master/disposable_email_blocklist.conf",
        "https://raw.githubusercontent.com/7c/fakefilter/main/txt/data.txt",
        "https://raw.githubusercontent.com/martenson/disposable-email-domains/master/disposable_email_blocklist.conf"
    };

    // Well-known legitimate email providers (gmail, outlook, etc.)
    // We pre-populate this so our list of "safe" domains is comprehensive.
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

    // Common typos of well-known providers - flag these as suspicious
    private static final Map<String, String> KNOWN_TYPOS = new HashMap<>();
    static {
        // gmail typos
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
        // outlook typos
        KNOWN_TYPOS.put("outlook.cm",  "outlook.com");
        KNOWN_TYPOS.put("outlook.con", "outlook.com");
        KNOWN_TYPOS.put("outlok.in",   "outlook.com");
        KNOWN_TYPOS.put("oulook.com",  "outlook.com");
        KNOWN_TYPOS.put("iutlook.com", "outlook.com");
        // hotmail typos
        KNOWN_TYPOS.put("hotmail.con", "hotmail.com");
        KNOWN_TYPOS.put("hotmail.co.jk", "hotmail.co.jp");
        // yahoo typos
        KNOWN_TYPOS.put("yahoo.con", "yahoo.com");
        // icloud typos
        KNOWN_TYPOS.put("icould.com", "icloud.com");
        // qq typo
        KNOWN_TYPOS.put("qq.cpm", "qq.com");
        // 163 typo
        KNOWN_TYPOS.put("163.ckm", "163.com");
        // misc
        KNOWN_TYPOS.put("bigpong.com", "bigpond.com");
        KNOWN_TYPOS.put("bigpond.net.su", "bigpond.net.au");
        KNOWN_TYPOS.put("crowns-hk.cpm", "crowns-hk.com");
    }

    // Domain syntax pattern (simple but effective)
    private static final Pattern DOMAIN_RE =
            Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$");

    public static void main(String[] args) throws Exception {
        String filePath = "Domain.xlsx";

        System.out.println("Downloading disposable-domain blocklists...");
        Set<String> disposable = downloadDisposableDomains();
        System.out.println("  Loaded " + disposable.size() + " disposable domains.\n");

        Workbook workbook;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            workbook = new XSSFWorkbook(fis);
        }

        Sheet inputSheet = workbook.getSheetAt(0);

        int existingResults = workbook.getSheetIndex("Results");
        if (existingResults != -1) workbook.removeSheetAt(existingResults);
        Sheet outputSheet = workbook.createSheet("Results");

        DataFormatter formatter = new DataFormatter();

        CellStyle greenStyle = workbook.createCellStyle();
        greenStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        greenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle redStyle = workbook.createCellStyle();
        redStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle yellowStyle = workbook.createCellStyle();
        yellowStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row header = outputSheet.createRow(0);
        header.createCell(0).setCellValue("Domain");
        header.createCell(1).setCellValue("Status");
        header.createCell(2).setCellValue("Reason");

        System.out.println("=============================================================");
        System.out.printf("%-40s %-15s %-30s%n", "Domain", "Status", "Reason");
        System.out.println("=============================================================");

        int outputRowIndex = 1;
        int safe = 0, bad = 0, unknown = 0;

        for (int i = 1; i <= inputSheet.getLastRowNum(); i++) {
            Row row = inputSheet.getRow(i);
            if (row == null) continue;

            Cell cell = row.getCell(0);
            if (cell == null) continue;

            String raw = formatter.formatCellValue(cell).trim().toLowerCase();
            if (raw.isEmpty()) continue;

            // If somebody put a full email in, take just the domain part
            String domain = raw.contains("@") ? raw.substring(raw.indexOf('@') + 1) : raw;
            domain = domain.replaceFirst("^https?://", "").replaceFirst("/.*$", "");

            String status;
            String reason;
            CellStyle style;

            if (!DOMAIN_RE.matcher(domain).matches()) {
                status = "INVALID";
                reason = "Bad domain syntax";
                style = redStyle;
                bad++;
            } else if (KNOWN_TYPOS.containsKey(domain)) {
                status = "TYPO";
                reason = "Likely typo of " + KNOWN_TYPOS.get(domain);
                style = redStyle;
                bad++;
            } else if (disposable.contains(domain)) {
                status = "DISPOSABLE";
                reason = "On disposable-domain blocklist";
                style = redStyle;
                bad++;
            } else if (KNOWN_LEGIT.contains(domain)) {
                status = "SAFE";
                reason = "Known legitimate provider";
                style = greenStyle;
                safe++;
            } else {
                status = "UNKNOWN";
                reason = "Not on any list (probably company domain)";
                style = yellowStyle;
                unknown++;
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
        outputSheet.setColumnWidth(1, 4000);
        outputSheet.setColumnWidth(2, 14000);

        System.out.println("=============================================================");
        System.out.println("SUMMARY");
        System.out.println("=============================================================");
        System.out.printf("  SAFE       : %d  (known good providers)%n", safe);
        System.out.printf("  BAD        : %d  (disposable, typos, invalid)%n", bad);
        System.out.printf("  UNKNOWN    : %d  (probably company domains - review manually)%n", unknown);
        System.out.printf("  Total      : %d%n", (safe + bad + unknown));
        System.out.println("=============================================================");

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
        System.out.println("Output saved to: " + filePath);
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
                    System.out.println("  + " + (all.size() - before) + " new from " + url);
                } else {
                    System.out.println("  ! HTTP " + resp.statusCode() + " from " + url);
                }
            } catch (Exception e) {
                System.out.println("  ! Failed: " + url + " (" + e.getMessage() + ")");
            }
        }
        return all;
    }
}
