package com.stock.repo;

import com.stock.model.Part;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long> {

    List<Part> findByCategoryIdOrderByNameAsc(Long categoryId);

    List<Part> findByNameContainingIgnoreCaseOrderByNameAsc(String q);

    long countByCategoryId(Long categoryId);

    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);

    /** Блокуємо рядок, щоб два одночасні кліки "−" не пішли в мінус. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Part p where p.id = :id")
    Optional<Part> findForUpdate(Long id);
}
