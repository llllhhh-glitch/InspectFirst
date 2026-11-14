package com.inspectfirst.guide;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, unique = true, length = 60) private String slug;
    @Column(nullable = false, length = 80) private String name;
    protected Category() { }
    public String getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
}
