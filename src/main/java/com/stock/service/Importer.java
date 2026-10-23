package com.stock.service;

import com.stock.model.Category;
import com.stock.model.Part;
import com.stock.model.StockMovement;
import com.stock.repo.CategoryRepository;
import com.stock.repo.PartRepository;
import com.stock.repo.StockMovementRepository;
import com.stock.web.Dto.ImportResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Імпорт залишків з Excel / Google Таблиць (.xlsx).
 * Розуміє файл, вивантажений самою програмою (аркуш «Усі»), і будь-яку таблицю,
 * де в рядку заголовків є колонки «Деталь» і «Кількість» (за бажанням «Розділ», «Мінімум»).
 * Немає колонки «Розділ» — розділом стає назва аркуша.
 * Нові розділи й деталі створюються, у наявних деталей кількість стає як у таблиці.
 * Нічого не видаляється.
 */
@Service
public class Importer {

    private static final Set<String> NAME = Set.of("part", "name", "item", "product", "деталь", "назва", "товар", "найменування");
    private static final Set<String> QTY = Set.of("quantity", "qty", "count", "stock", "кількість", "к-сть", "залишок");
    private static final Set<String> CATEGORY = Set.of("section", "category", "розділ", "категорія");
    private static final Set<String> MIN = Set.of("minimum", "min", "мінімум", "мін");
    private static final Set<String> TOTAL = Set.of("total", "разом", "всього", "усього");
    /** Аркуш «усі деталі» з експорту: нова англійська назва і стара українська. */
    private static final List<String> ALL_SHEETS = List.of(ExcelExporter.ALL_SHEET, "Усі");

    private final CategoryRepository categories;
    private final PartRepository parts;
    private final StockMovementRepository movements;

    public Importer(CategoryRepository categories, PartRepository parts, StockMovementRepository movements) {
        this.categories = categories;
        this.parts = parts;
        this.movements = movements;
    }

    private record Item(String category, String name, int quantity, Integer min) {}

    @Transactional
    public ImportResult importXlsx(InputStream in, String username) {
        List<Item> items = new ArrayList<>();
        int skipped;
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet all = ALL_SHEETS.stream().map(wb::getSheet).filter(Objects::nonNull).findFirst().orElse(null);
            List<Sheet> sheets = new ArrayList<>();
            if (all != null) sheets.add(all); else wb.forEach(sheets::add);
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
            skipped = 0;
            for (Sheet s : sheets) skipped += readSheet(s, fmt, eval, items);
        } catch (IOException | RuntimeException e) {
            if (e instanceof StockException se) throw se;
            throw new StockException.BadRequest("Could not read the spreadsheet. An .xlsx file is required");
        }
        if (items.isEmpty())
            throw new StockException.BadRequest("No \"Part\" and \"Quantity\" columns found in the sheet");
        return apply(items, skipped, username);
    }

    /** @return кількість пропущених рядків */
    private int readSheet(Sheet s, DataFormatter fmt, FormulaEvaluator eval, List<Item> out) {
        int headerRow = -1, nameCol = -1, qtyCol = -1, catCol = -1, minCol = -1;
        for (int r = s.getFirstRowNum(); r <= Math.min(s.getLastRowNum(), 15) && headerRow < 0; r++) {
            Row row = s.getRow(r);
            if (row == null) continue;
            int n = -1, q = -1, c = -1, m = -1;
            for (Cell cell : row) {
                String h = fmt.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
                int i = cell.getColumnIndex();
                if (NAME.contains(h) && n < 0) n = i;
                else if (QTY.contains(h) && q < 0) q = i;
                else if (CATEGORY.contains(h) && c < 0) c = i;
                else if (MIN.contains(h) && m < 0) m = i;
            }
            if (n >= 0 && q >= 0) { headerRow = r; nameCol = n; qtyCol = q; catCol = c; minCol = m; }
        }
        if (headerRow < 0) return 0;

        int skipped = 0;
        for (int r = headerRow + 1; r <= s.getLastRowNum(); r++) {
            Row row = s.getRow(r);
            if (row == null) continue;
            String name = text(row, nameCol, fmt, eval);
            String cat = catCol >= 0 ? text(row, catCol, fmt, eval) : s.getSheetName().trim();
            if (name.isEmpty() || TOTAL.contains(name.toLowerCase(Locale.ROOT))) continue;
            Integer qty = number(row, qtyCol, fmt, eval);
            Integer min = minCol >= 0 ? number(row, minCol, fmt, eval) : null;
            if (cat.isEmpty() || qty == null || qty < 0 || name.length() > 150 || cat.length() > 100) { skipped++; continue; }
            out.add(new Item(cat, name, qty, min != null && min >= 0 ? min : null));
        }
        return skipped;
    }

    private static String text(Row row, int col, DataFormatter fmt, FormulaEvaluator eval) {
        Cell c = row.getCell(col);
        return c == null ? "" : fmt.formatCellValue(c, eval).trim();
    }

    private static Integer number(Row row, int col, DataFormatter fmt, FormulaEvaluator eval) {
        String t = text(row, col, fmt, eval).replace(" ", "").replace(" ", "").replace(',', '.');
        if (t.isEmpty()) return null;
        try {
            double d = Double.parseDouble(t);
            return d == Math.rint(d) && Math.abs(d) < 1_000_000_000 ? (int) d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ImportResult apply(List<Item> items, int skipped, String username) {
        Map<String, Category> cats = new HashMap<>();
        categories.findAll().forEach(c -> cats.put(c.getName().toLowerCase(Locale.ROOT), c));
        int catsCreated = 0, created = 0, updated = 0;

        // Якщо деталь трапляється в таблиці двічі — береться останній рядок
        Map<String, Item> unique = new LinkedHashMap<>();
        for (Item i : items) unique.put((i.category() + "\u0000" + i.name()).toLowerCase(Locale.ROOT), i);

        for (Item i : unique.values()) {
            Category c = cats.get(i.category().toLowerCase(Locale.ROOT));
            if (c == null) {
                c = categories.save(new Category(i.category(), cats.size()));
                cats.put(i.category().toLowerCase(Locale.ROOT), c);
                catsCreated++;
            }
            Category cat = c;
            Part p = parts.findByCategoryIdOrderByNameAsc(cat.getId()).stream()
                    .filter(x -> x.getName().equalsIgnoreCase(i.name())).findFirst().orElse(null);
            if (p == null) {
                p = parts.save(new Part(cat, i.name(), i.quantity(), i.min() == null ? 0 : i.min()));
                if (i.quantity() > 0) movements.save(new StockMovement(p, i.quantity(), i.quantity(), "Import", username));
                created++;
            } else {
                boolean changed = false;
                int delta = i.quantity() - p.getQuantity();
                if (delta != 0) {
                    p.setQuantity(i.quantity());
                    movements.save(new StockMovement(p, delta, i.quantity(), "Import", username));
                    changed = true;
                }
                if (i.min() != null && i.min() != p.getMinQuantity()) { p.setMinQuantity(i.min()); changed = true; }
                if (changed) updated++;
            }
        }
        return new ImportResult(catsCreated, created, updated, skipped);
    }
}
