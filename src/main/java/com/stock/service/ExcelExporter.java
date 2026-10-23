package com.stock.service;

import com.stock.model.Category;
import com.stock.model.Part;
import com.stock.repo.CategoryRepository;
import com.stock.repo.PartRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Вивантаження залишків у Excel:
 *  - аркуш "Усі" — всі деталі з колонкою "Розділ";
 *  - окремий аркуш на кожен розділ (Drone, GCS, ...).
 * Деталі, яких мало (кількість ≤ мінімуму), підсвічуються.
 */
@Service
public class ExcelExporter {

    public static final String ALL_SHEET = "All";

    private static final String[] HEADERS = {"Section", "Part", "Quantity", "Minimum", "Status"};

    private final CategoryRepository categories;
    private final PartRepository parts;

    public ExcelExporter(CategoryRepository categories, PartRepository parts) {
        this.categories = categories;
        this.parts = parts;
    }

    /** @param categoryId null — увесь склад, інакше лише один розділ */
    @Transactional(readOnly = true)
    public void write(Long categoryId, OutputStream out) throws IOException {
        List<Category> cats = categoryId == null
                ? categories.findAllByOrderBySortOrderAscNameAsc()
                : categories.findById(categoryId).map(List::of)
                    .orElseThrow(() -> new StockException.NotFound("Section not found"));

        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            if (categoryId == null) {
                Sheet all = wb.createSheet(ALL_SHEET);
                int r = header(all, st);
                for (Category c : cats)
                    for (Part p : parts.findByCategoryIdOrderByNameAsc(c.getId()))
                        row(all, r++, c, p, st);
                finish(all, r, st);
            }
            for (Category c : cats) {
                Sheet s = wb.createSheet(safeName(c.getName()));
                int r = header(s, st);
                for (Part p : parts.findByCategoryIdOrderByNameAsc(c.getId())) row(s, r++, c, p, st);
                finish(s, r, st);
            }
            wb.write(out);
        }
    }

    private int header(Sheet s, Styles st) {
        Row title = s.createRow(0);
        Cell t = title.createCell(0);
        t.setCellValue("Stock levels — " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        t.setCellStyle(st.title);
        Row h = s.createRow(2);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(st.header);
        }
        s.createFreezePane(0, 3);
        return 3;
    }

    private void row(Sheet s, int r, Category c, Part p, Styles st) {
        boolean low = p.getMinQuantity() > 0 && p.getQuantity() <= p.getMinQuantity();
        Row row = s.createRow(r);
        row.createCell(0).setCellValue(c.getName());
        row.createCell(1).setCellValue(p.getName());
        Cell q = row.createCell(2);
        q.setCellValue(p.getQuantity());
        q.setCellStyle(low ? st.lowNumber : st.number);
        row.createCell(3).setCellValue(p.getMinQuantity());
        Cell status = row.createCell(4);
        status.setCellValue(p.getQuantity() == 0 ? "Out" : low ? "Low" : "OK");
        if (low) status.setCellStyle(st.low);
    }

    private void finish(Sheet s, int lastRow, Styles st) {
        if (lastRow > 3) {
            s.setAutoFilter(new CellRangeAddress(2, lastRow - 1, 0, HEADERS.length - 1));
            Row total = s.createRow(lastRow + 1);
            Cell l = total.createCell(1);
            l.setCellValue("Total");
            l.setCellStyle(st.header);
            Cell sum = total.createCell(2);
            sum.setCellFormula("SUM(C4:C" + lastRow + ")");
            sum.setCellStyle(st.header);
        }
        int[] widths = {16, 34, 12, 12, 10};
        for (int i = 0; i < widths.length; i++) s.setColumnWidth(i, widths[i] * 256);
    }

    /** Excel не дозволяє деякі символи та >31 знака в назві аркуша. */
    private static String safeName(String n) {
        String s = n.replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (s.equalsIgnoreCase(ALL_SHEET)) s = s + "_";
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    private static final class Styles {
        final CellStyle title, header, number, low, lowNumber;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);

            title = wb.createCellStyle();
            title.setFont(big);

            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            number = wb.createCellStyle();
            number.setAlignment(HorizontalAlignment.CENTER);

            low = wb.createCellStyle();
            low.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            low.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            lowNumber = wb.createCellStyle();
            lowNumber.cloneStyleFrom(low);
            lowNumber.setAlignment(HorizontalAlignment.CENTER);
        }
    }
}
