package com.stock.web;

import com.stock.service.ExcelExporter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

@RestController
public class ExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelExporter exporter;

    public ExportController(ExcelExporter exporter) { this.exporter = exporter; }

    /** GET /api/export.xlsx — увесь склад; ?categoryId=1 — лише один розділ. */
    @GetMapping("/api/export.xlsx")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long categoryId) throws IOException {
        var buf = new ByteArrayOutputStream();
        exporter.write(categoryId, buf); // спершу будуємо файл — якщо помилка, повернеться нормальний JSON
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stock-" + LocalDate.now() + ".xlsx\"")
                .body(buf.toByteArray());
    }
}
