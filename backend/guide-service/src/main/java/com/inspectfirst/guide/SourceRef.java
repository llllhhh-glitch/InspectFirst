package com.inspectfirst.guide;

import jakarta.persistence.*;

@Entity
@Table(name = "source_ref")
public class SourceRef {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 1000) private String url;
    @Column(nullable = false, length = 80) private String publisher;
    protected SourceRef() { }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getPublisher() { return publisher; }
}
