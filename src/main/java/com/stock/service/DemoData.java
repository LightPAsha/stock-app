package com.stock.service;

import com.stock.model.Category;
import com.stock.model.Part;
import com.stock.repo.CategoryRepository;
import com.stock.repo.PartRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Заповнює порожню базу прикладом з дошки (тільки при першому запуску). */
@Component
public class DemoData implements CommandLineRunner {

    private final CategoryRepository categories;
    private final PartRepository parts;

    public DemoData(CategoryRepository categories, PartRepository parts) {
        this.categories = categories;
        this.parts = parts;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categories.count() > 0) return;
        Category drone = categories.save(new Category("Drone", 0));
        categories.save(new Category("GCS", 1));
        parts.save(new Part(drone, "Gimbal", 3, 1));
        parts.save(new Part(drone, "Rear I", 7, 2));
        parts.save(new Part(drone, "Rear II", 5, 2));
        parts.save(new Part(drone, "Main Fuselage", 4, 1));
    }
}
