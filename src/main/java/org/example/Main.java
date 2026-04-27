package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        String filePath = "Domain.xlsx";

        Path debugDir = Paths.get("debug");
        Files.createDirectories(debugDir);

        boolean headless = "true".equalsIgnoreCase(System.getenv("HEADLESS"));
        System.out.println(">>> Mode: " + (headless ? "HEADLESS" : "NON-HEADLESS (visible window)"));
        System.out.println(">>> DISPLAY env: " + System.getenv("DISPLAY"));

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
        } else {
            // Even non-headless on Linux benefits from these flags
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
        }

        WebDriver driver = new ChromeDriver(options);

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
        header.createCell(0).setCellValue("Email");
        header.createCell(1).setCellValue("Status");

        System.out.println("=============================================================");
        System.out.printf("%-50s %-30s%n", "Email", "Status");
        System.out.println("=============================================================");

        int outputRowIndex = 1;
        int safe = 0, temporary = 0, unknown = 0;
        int failureCount = 0;
        final int MAX_DEBUG_CAPTURES = 30;

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
                System.out.println("Could not fetch status for: " + email);

                if (failureCount < MAX_DEBUG_CAPTURES) {
                    try {
                        String safeName = email.replaceAll("[^a-zA-Z0-9._-]", "_");
                        String prefix = String.format("fail_%03d_%s", failureCount + 1, safeName);

                        File png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                        Files.copy(png.toPath(),
                                debugDir.resolve(prefix + ".png"),
                                StandardCopyOption.REPLACE_EXISTING);

                        Files.writeString(debugDir.resolve(prefix + ".html"),
                                driver.getPageSource());

                        Files.writeString(debugDir.resolve(prefix + "_url.txt"),
                                driver.getCurrentUrl());

                        System.out.println("   >> saved debug/" + prefix + ".png");
                    } catch (Exception ignore) {}
                    failureCount++;
                }
            }

            Row outRow = outputSheet.createRow(outputRowIndex++);
            outRow.createCell(0).setCellValue(email);
            Cell statusCell = outRow.createCell(1);
            statusCell.setCellValue(status);

            String s = status.toLowerCase();
            String colorLabel;
            if (s.contains("safe") || s.contains("valid") || s.contains("deliverable") || s.contains("good")) {
                statusCell.setCellStyle(greenStyle); colorLabel = "GREEN"; safe++;
            } else if (s.contains("temporary") || s.contains("disposable") || s.contains("invalid") || s.contains("risky") || s.contains("failed")) {
                statusCell.setCellStyle(redStyle); colorLabel = "RED"; temporary++;
            } else {
                statusCell.setCellStyle(yellowStyle); colorLabel = "YELLOW"; unknown++;
            }

            System.out.printf("%-50s %-30s [%s]%n", email, status, colorLabel);
        }

        outputSheet.setColumnWidth(0, 12000);
        outputSheet.setColumnWidth(1, 8000);

        System.out.println("=============================================================");
        System.out.println("SUMMARY");
        System.out.println("=============================================================");
        System.out.printf("  GREEN  : %d%n", safe);
        System.out.printf("  RED    : %d%n", temporary);
        System.out.printf("  YELLOW : %d%n", unknown);
        System.out.printf("  Total  : %d%n", (safe + temporary + unknown));
        System.out.printf("  Debug  : %d captures saved%n", failureCount);
        System.out.println("=============================================================");

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        workbook.close();
        driver.quit();

        System.out.println("Output saved to: " + filePath);
    }
}
