package com.stock;

import com.stock.service.ExcelExporter;
import com.stock.service.StockException;
import com.stock.service.StockService;
import com.stock.web.Dto.CreatePart;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        "logging.file.name="})
class StockServiceTest {

    @Autowired StockService service;
    @Autowired ExcelExporter exporter;

    @Test
    void cannotGoBelowZero() {
        Long cat = service.createCategory("Test-A").id();
        var p = service.createPart(new CreatePart(cat, "Prop", 1, 0), "t");
        service.adjust(p.id(), -1, null, "t");
        assertThrows(StockException.Conflict.class, () -> service.adjust(p.id(), -1, null, "t"));
    }

    @Test
    void concurrentClicksAreSafe() throws Exception {
        Long cat = service.createCategory("Test-B").id();
        var p = service.createPart(new CreatePart(cat, "Motor", 0, 0), "t");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 100; i++) pool.submit(() -> service.adjust(p.id(), 1, null, "t"));
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(100, service.listParts(cat).get(0).quantity());
        assertEquals(50, service.history(p.id(), 50).size());
    }

    @Test
    void excelHasSheetPerCategory() throws Exception {
        var out = new ByteArrayOutputStream();
        exporter.write(null, out);
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals("All", wb.getSheetAt(0).getSheetName());
            assertNotNull(wb.getSheet("Drone"));
            assertEquals("Gimbal", wb.getSheet("Drone").getRow(3).getCell(1).getStringCellValue());
        }
    }
}
