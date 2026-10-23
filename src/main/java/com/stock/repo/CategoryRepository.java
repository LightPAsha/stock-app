package com.stock.repo;

import com.stock.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderBySortOrderAscNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
