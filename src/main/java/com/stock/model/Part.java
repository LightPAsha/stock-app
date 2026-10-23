package com.stock.model;

import jakarta.persistence.*;

/** Деталь посередині: Gimbal, Rear I, Main Fuselage ... */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "name"}),
       indexes = @Index(columnList = "category_id"))
public class Part {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private int quantity;

    /** Поріг "мало на складі" — підсвічується в інтерфейсі. 0 = вимкнено. */
    private int minQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Version
    private long version;

    protected Part() {}

    public Part(Category category, String name, int quantity, int minQuantity) {
        this.category = category;
        this.name = name;
        this.quantity = quantity;
        this.minQuantity = minQuantity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getMinQuantity() { return minQuantity; }
    public void setMinQuantity(int minQuantity) { this.minQuantity = minQuantity; }
    public Category getCategory() { return category; }
}
