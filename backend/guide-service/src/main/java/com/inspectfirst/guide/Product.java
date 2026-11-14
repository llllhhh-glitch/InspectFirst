package com.inspectfirst.guide;

import jakarta.persistence.*;

@Entity
@Table(name = "product", uniqueConstraints = @UniqueConstraint(columnNames = {"brand", "model_code"}))
public class Product {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id") private Category category;
    @Column(nullable = false, length = 80) private String brand;
    @Column(name = "model_code", nullable = false, length = 80) private String modelCode;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 16) private String status;
    protected Product() { }
    public String getId() { return id; }
    public Category getCategory() { return category; }
    public String getBrand() { return brand; }
    public String getModelCode() { return modelCode; }
    public String getName() { return name; }
    public String getStatus() { return status; }
}
