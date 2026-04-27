package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        // Same file for input AND output — overwritten in place
        String filePath = "Domain.xlsx";

        // Headless Chrome so it works on GitHub Actions runners (no display)
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);

        // Read the file fully into memory FIRST, then close the stream,
        // so we can overwrite the same file later.
        Workbook workbook;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            workbook = new XSSFWorkbook(fis);
        }

        Sheet inputSheet = workbook.getSheetAt(0);

        // Remove any old "Results" sheet so we start fresh
        int existingResults = workbook.getSheetIndex("Results");
        if (existingResults != -1) {
            workbook.removeSheetAt(existingResults);
        }
        Sheet outputSheet = workbook.createSheet("Results");

        DataFormatter formatter = new DataFormatter();

        // Cell styles
        CellStyle greenStyle = workbook.createCellStyle();
        greenStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        greenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle redStyle = workbook.createCellStyle();
        redStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle yellowStyle = workbook.createCellStyle();
        yellowStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Header
        Row header = outputSheet.createRow(0);
        header.createCell(0).setCellValue("Email");
        header.createCell(1).setCellValue("Status");

        System.out.println("=============================================================");
        System.out.printf("%-50s %-30s%n", "Email", "Status");
        System.out.println("=============================================================");

        int outputRowIndex = 1;
        int safe = 0, temporary = 0, unknown = 0;

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        for (int i = 1; i <= inputSheet.getLastRowNum(); i++) {
            Row row = inputSheet.getRow(i);
            if (row == null) continue;

            Cell emailCell = row.getCell(0);
            if (emailCell == null) continue;

            String email = formatter.formatCellValue(emailCell).trim();
            if (email.isEmpty()) continue;

            String status = "UNKNOWN";

            try {
                driver.get("https://verifymail.io/email/" + email);

                WebElement statusElement = wait.until(
                        ExpectedConditions.visibilityOfElementLocated(
                                By.xpath("//*[@data-aos='fade-down']/h2[1]")
                        )
                );
                status = statusElement.getText().trim();

            } catch (Exception e) {
                status = "FAILED TO LOAD";
                System.out.println("Could not fetch status for: " + email + " -> " + e.getMessage());
            }

            Row outRow = outputSheet.createRow(outputRowIndex++);
            outRow.createCell(0).setCellValue(email);

            Cell statusCell = outRow.createCell(1);
            statusCell.setCellValue(status);

            String statusLower = status.toLowerCase();
            String colorLabel;

            if (statusLower.contains("safe") || statusLower.contains("valid") ||
                    statusLower.contains("deliverable") || statusLower.contains("good")) {
                statusCell.setCellStyle(greenStyle);
                colorLabel = "GREEN";
                safe++;
            } else if (statusLower.contains("temporary") || statusLower.contains("disposable") ||
                    statusLower.contains("invalid") || statusLower.contains("risky") ||
                    statusLower.contains("failed")) {
                statusCell.setCellStyle(redStyle);
                colorLabel = "RED";
                temporary++;
            } else {
                statusCell.setCellStyle(yellowStyle);
                colorLabel = "YELLOW";
                unknown++;
            }

            System.out.printf("%-50s %-30s [%s]%n", email, status, colorLabel);
        }

        outputSheet.setColumnWidth(0, 12000);
        outputSheet.setColumnWidth(1, 8000);

        System.out.println("=============================================================");
        System.out.println("SUMMARY");
        System.out.println("=============================================================");
        System.out.printf("  GREEN  (Safe/Valid)       : %d%n", safe);
        System.out.printf("  RED    (Temporary/Invalid): %d%n", temporary);
        System.out.printf("  YELLOW (Unknown/Other)    : %d%n", unknown);
        System.out.printf("  Total Processed           : %d%n", (safe + temporary + unknown));
        System.out.println("=============================================================");

        // Overwrite the same file
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();

        driver.quit();

        System.out.println("Output saved to: " + filePath);
    }
}
