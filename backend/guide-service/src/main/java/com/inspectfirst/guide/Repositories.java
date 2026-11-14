package com.inspectfirst.guide;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findAllByOrderByNameAsc();
}

interface ProductRepository extends JpaRepository<Product, String> {
    @EntityGraph(attributePaths = "category")
    @Query("""
        select p from Product p where p.status = 'ACTIVE'
        and (:category = '' or p.category.slug = :category)
        and (lower(p.name) like :pattern escape '!' or lower(p.brand) like :pattern escape '!'
          or lower(p.modelCode) like :pattern escape '!')
        and exists (select v.id from GuideVersion v where v.guide.product = p
          and v.guide.availability = 'ACTIVE' and v.status = 'PUBLISHED')
        """)
    Page<Product> search(@Param("pattern") String pattern, @Param("category") String category, Pageable pageable);
}

interface GuideVersionRepository extends JpaRepository<GuideVersion, String> {
    @Query("""
        select v from GuideVersion v join fetch v.guide g join fetch g.product p join fetch p.category
        where p.id = :productId and p.status = 'ACTIVE' and g.availability = 'ACTIVE'
          and v.status = 'PUBLISHED' order by v.versionNo desc
        """)
    List<GuideVersion> findPublished(@Param("productId") String productId, Pageable pageable);
}
