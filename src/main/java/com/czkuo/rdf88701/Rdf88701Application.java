package com.czkuo.rdf88701;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@SpringBootApplication
@EnableScheduling
public class Rdf88701Application {

    public static void main(String[] args) {
        // Step 1: 取得執行磁碟根目錄與 log 路徑
        String userDir = System.getProperty("user.dir");   // 例如 D:\rdf88701
        String driveRoot = userDir.substring(0, 3);        // 例如 D:\
        String logDirPath = driveRoot + "logs/rdf88701";   // 例如 D:\logs\rdf88701

        // Step 2: 設定系統屬性給 logback 使用
        System.setProperty("LOG_PATH", logDirPath);
        System.setProperty("APP_NAME", "rdf88701");

        // Step 3: 確保目錄存在
        try {
            Files.createDirectories(Path.of(logDirPath));
            System.out.println("Log directory ensured at: " + logDirPath);
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + logDirPath);
            e.printStackTrace();
        }

        // Step 4: 啟動 Spring Boot
        SpringApplication.run(Rdf88701Application.class, args);
    }
}
