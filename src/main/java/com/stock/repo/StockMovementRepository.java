package com.stock.repo;

import com.stock.model.StockMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByPartIdOrderByCreatedAtDesc(Long partId, Pageable page);

    @Modifying
    @Query("delete from StockMovement m where m.part.id = :partId")
    void deleteByPartId(Long partId);
}
