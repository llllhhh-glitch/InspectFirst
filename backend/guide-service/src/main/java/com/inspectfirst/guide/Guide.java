package com.inspectfirst.guide;

import jakarta.persistence.*;

@Entity
@Table(name = "guide")
public class Guide {
    @Id @Column(length = 36) private String id;
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", unique = true) private Product product;
    @Column(nullable = false, length = 16) private String availability;
    protected Guide() { }
    public String getId() { return id; }
    public Product getProduct() { return product; }
    public String getAvailability() { return availability; }
}
