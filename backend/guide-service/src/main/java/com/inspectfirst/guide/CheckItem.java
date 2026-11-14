package com.inspectfirst.guide;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "check_item", uniqueConstraints = @UniqueConstraint(columnNames = {"guide_version_id", "sort_order"}))
public class CheckItem {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guide_version_id") private GuideVersion version;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 600) private String purpose;
    @Column(nullable = false, length = 2000) private String method;
    @Column(name = "expected_observation", nullable = false, length = 1200) private String expectedObservation;
    @Column(name = "abnormal_signs", nullable = false, length = 1200) private String abnormalSigns;
    @Column(nullable = false, length = 1200) private String limitations;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "check_item_source", joinColumns = @JoinColumn(name = "check_item_id"), inverseJoinColumns = @JoinColumn(name = "source_ref_id"))
    @OrderBy("title ASC") private List<SourceRef> sources = new ArrayList<>();
    protected CheckItem() { }
    public String getId() { return id; }
    public int getSortOrder() { return sortOrder; }
    public String getTitle() { return title; }
    public String getPurpose() { return purpose; }
    public String getMethod() { return method; }
    public String getExpectedObservation() { return expectedObservation; }
    public String getAbnormalSigns() { return abnormalSigns; }
    public String getLimitations() { return limitations; }
    public List<SourceRef> getSources() { return sources; }
}
