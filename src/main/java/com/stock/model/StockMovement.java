package com.stock.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Журнал руху: кожне "+" / "−" записується, щоб було видно історію. */
@Entity
@Table(indexes = @Index(columnList = "part_id, created_at"))
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id")
    private Part part;

    private int delta;
    private int quantityAfter;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(length = 255)
    private String note;

    /** Хто натиснув + / − (null — старі записи до появи входу). */
    @Column(length = 50)
    private String username;

    protected StockMovement() {}

    public StockMovement(Part part, int delta, int quantityAfter, String note, String username) {
        this.part = part;
        this.delta = delta;
        this.quantityAfter = quantityAfter;
        this.note = note;
        this.username = username;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public int getDelta() { return delta; }
    public int getQuantityAfter() { return quantityAfter; }
    public Instant getCreatedAt() { return createdAt; }
    public String getNote() { return note; }
    public String getUsername() { return username; }
}
