package com.stock.web;

import com.stock.service.StockService;
import com.stock.web.Auth.AdminOnly;
import com.stock.web.Auth.SessionUser;
import com.stock.web.Dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StockController {

    private final StockService service;

    public StockController(StockService service) { this.service = service; }

    // Розділи
    @GetMapping("/categories")
    public List<CategoryView> categories() { return service.listCategories(); }

    @AdminOnly
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@Valid @RequestBody CreateCategory req) { return service.createCategory(req.name()); }

    @AdminOnly
    @PutMapping("/categories/{id}")
    public CategoryView renameCategory(@PathVariable Long id, @Valid @RequestBody RenameCategory req) {
        return service.renameCategory(id, req.name());
    }

    @AdminOnly
    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) { service.deleteCategory(id); }

    @GetMapping("/categories/{id}/parts")
    public List<PartView> parts(@PathVariable Long id) { return service.listParts(id); }

    // Деталі
    @GetMapping("/parts")
    public List<PartView> search(@RequestParam("q") String q) { return service.search(q); }

    @AdminOnly
    @PostMapping("/parts")
    @ResponseStatus(HttpStatus.CREATED)
    public PartView createPart(@Valid @RequestBody CreatePart req, @RequestAttribute(Auth.SESSION_KEY) SessionUser u) {
        return service.createPart(req, u.username());
    }

    @AdminOnly
    @PutMapping("/parts/{id}")
    public PartView updatePart(@PathVariable Long id, @Valid @RequestBody UpdatePart req) { return service.updatePart(id, req); }

    @AdminOnly
    @DeleteMapping("/parts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePart(@PathVariable Long id) { service.deletePart(id); }

    // Дії: + / −
    @PostMapping("/parts/{id}/adjust")
    public PartView adjust(@PathVariable Long id, @Valid @RequestBody Adjust req,
                           @RequestAttribute(Auth.SESSION_KEY) SessionUser u) {
        return service.adjust(id, req.delta(), req.note(), u.username());
    }

    @GetMapping("/parts/{id}/history")
    public List<MovementView> history(@PathVariable Long id, @RequestParam(defaultValue = "50") int limit) {
        return service.history(id, limit);
    }
}
