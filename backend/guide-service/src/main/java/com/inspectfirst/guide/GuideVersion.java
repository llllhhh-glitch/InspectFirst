package com.inspectfirst.guide;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "guide_version", uniqueConstraints = @UniqueConstraint(columnNames = {"guide_id", "version_no"}))
public class GuideVersion {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guide_id") private Guide guide;
    @Column(name = "version_no", nullable = false) private int versionNo;
    @Column(nullable = false, length = 16) private String status;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 600) private String tools;
    @Column(name = "reviewed_on", nullable = false) private LocalDate reviewedOn;
    @Column(name = "content_note", nullable = false, length = 1200) private String contentNote;
    @OneToMany(mappedBy = "version", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC") private List<CheckItem> items = new ArrayList<>();
    protected GuideVersion() { }
    public String getId() { return id; }
    public Guide getGuide() { return guide; }
    public int getVersionNo() { return versionNo; }
    public String getTitle() { return title; }
    public String getTools() { return tools; }
    public LocalDate getReviewedOn() { return reviewedOn; }
    public String getContentNote() { return contentNote; }
    public List<CheckItem> getItems() { return items; }
}
