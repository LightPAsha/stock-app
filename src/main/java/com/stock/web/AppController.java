package com.stock.web;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class AppController {

    private final ConfigurableApplicationContext ctx;

    public AppController(ConfigurableApplicationContext ctx) { this.ctx = ctx; }

    /** Лаунчер перевіряє, чи програма вже запущена. */
    @Auth.Public
    @GetMapping("/api/ping")
    public Map<String, String> ping() { return Map.of("app", "stock-app"); }

    /** Кнопка "Вийти" в інтерфейсі. Дозволено лише з самого Pi. */
    @PostMapping("/api/shutdown")
    public ResponseEntity<Map<String, String>> shutdown(HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        boolean local = "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
        if (!local) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "The app can only be closed on the Raspberry Pi itself"));
        Thread t = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            System.exit(SpringApplication.exit(ctx, () -> 0));
        }, "shutdown");
        t.start();
        return ResponseEntity.ok(Map.of("status", "bye"));
    }
}
