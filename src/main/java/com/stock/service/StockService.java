package com.stock.service;

import com.stock.model.Category;
import com.stock.model.Part;
import com.stock.model.StockMovement;
import com.stock.repo.CategoryRepository;
import com.stock.repo.PartRepository;
import com.stock.repo.StockMovementRepository;
import com.stock.web.Dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockService {

    private final CategoryRepository categories;
    private final PartRepository parts;
    private final StockMovementRepository movements;

    public StockService(CategoryRepository categories, PartRepository parts, StockMovementRepository movements) {
        this.categories = categories;
        this.parts = parts;
        this.movements = movements;
    }

    // ---------- Розділи ----------

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories() {
        return categories.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(c -> CategoryView.of(c, parts.countByCategoryId(c.getId())))
                .toList();
    }

    @Transactional
    public CategoryView createCategory(String name) {
        String n = name.trim();
        if (categories.existsByNameIgnoreCase(n)) throw new StockException.Conflict("Section already exists: " + n);
        Category c = categories.save(new Category(n, (int) categories.count()));
        return CategoryView.of(c, 0);
    }

    @Transactional
    public CategoryView renameCategory(Long id, String name) {
        Category c = categories.findById(id).orElseThrow(() -> new StockException.NotFound("Section not found"));
        String n = name.trim();
        if (!c.getName().equalsIgnoreCase(n) && categories.existsByNameIgnoreCase(n))
            throw new StockException.Conflict("Section already exists: " + n);
        c.setName(n);
        return CategoryView.of(c, parts.countByCategoryId(id));
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new StockException.NotFound("Section not found"));
        if (parts.countByCategoryId(id) > 0)
            throw new StockException.Conflict("Delete the parts in section \"" + c.getName() + "\" first");
        categories.delete(c);
    }

    // ---------- Деталі ----------

    @Transactional(readOnly = true)
    public List<PartView> listParts(Long categoryId) {
        return parts.findByCategoryIdOrderByNameAsc(categoryId).stream().map(PartView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<PartView> search(String q) {
        return parts.findByNameContainingIgnoreCaseOrderByNameAsc(q.trim()).stream().map(PartView::of).toList();
    }

    @Transactional
    public PartView createPart(CreatePart req, String username) {
        Category c = categories.findById(req.categoryId())
                .orElseThrow(() -> new StockException.NotFound("Section not found"));
        String n = req.name().trim();
        if (parts.existsByCategoryIdAndNameIgnoreCase(c.getId(), n))
            throw new StockException.Conflict("This section already has a part named: " + n);
        Part p = parts.save(new Part(c, n, req.quantity(), req.minQuantity()));
        if (req.quantity() > 0) movements.save(new StockMovement(p, req.quantity(), req.quantity(), "Opening stock", username));
        return PartView.of(p);
    }

    @Transactional
    public PartView updatePart(Long id, UpdatePart req) {
        Part p = parts.findById(id).orElseThrow(() -> new StockException.NotFound("Part not found"));
        String n = req.name().trim();
        if (!p.getName().equalsIgnoreCase(n) && parts.existsByCategoryIdAndNameIgnoreCase(p.getCategory().getId(), n))
            throw new StockException.Conflict("This section already has a part named: " + n);
        p.setName(n);
        p.setMinQuantity(req.minQuantity());
        return PartView.of(p);
    }

    @Transactional
    public void deletePart(Long id) {
        Part p = parts.findById(id).orElseThrow(() -> new StockException.NotFound("Part not found"));
        movements.deleteByPartId(id);
        parts.delete(p);
    }

    /** Головна дія: "+" або "−". Атомарно, з блокуванням рядка та записом в журнал. */
    @Transactional
    public PartView adjust(Long id, int delta, String note, String username) {
        if (delta == 0) throw new StockException.BadRequest("Change cannot be 0");
        Part p = parts.findForUpdate(id).orElseThrow(() -> new StockException.NotFound("Part not found"));
        int next = p.getQuantity() + delta;
        if (next < 0)
            throw new StockException.Conflict("Not enough in stock: have " + p.getQuantity() + ", need " + (-delta));
        p.setQuantity(next);
        movements.save(new StockMovement(p, delta, next, note, username));
        return PartView.of(p);
    }

    @Transactional(readOnly = true)
    public List<MovementView> history(Long partId, int limit) {
        return movements.findByPartIdOrderByCreatedAtDesc(partId, PageRequest.of(0, Math.min(limit, 500)))
                .stream().map(MovementView::of).toList();
    }
}
