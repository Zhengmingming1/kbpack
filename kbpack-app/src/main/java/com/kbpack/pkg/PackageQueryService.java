package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.user.AppUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PackageQueryService {

    public record Filter(
            String keyword,
            String tag,
            String collection,
            String status,
            String source,
            Boolean favorite
    ) {
    }

    private final KnowledgePackageRepository packageRepository;
    private final PackageAccessService accessService;

    public PackageQueryService(
            KnowledgePackageRepository packageRepository,
            PackageAccessService accessService
    ) {
        this.packageRepository = packageRepository;
        this.accessService = accessService;
    }

    public Page<KnowledgePackage> search(AppUser user, Filter filter, Pageable pageable) {
        KnowledgePackage.Status status = parseStatus(filter.status());
        KnowledgePackage.SourceType source = parseSource(filter.source());
        TagFilter tag = parseTag(filter.tag());
        CollectionFilter collection = parseCollection(filter.collection());

        Specification<KnowledgePackage> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (!accessService.isAdministrator(user)) {
                predicates.add(cb.or(
                        cb.equal(root.get("ownerId"), user.getId()),
                        root.get("visibility").in("team", "public")
                ));
            }

            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + escapeLike(filter.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (source != null) {
                predicates.add(cb.equal(root.get("sourceType"), source));
            }
            if (tag != null) {
                predicates.add(tagPredicate(root, query.subquery(Integer.class), cb, tag));
            }
            if (collection != null) {
                predicates.add(collectionPredicate(root, query.subquery(Integer.class), cb, collection));
            }
            if (filter.favorite() != null) {
                Subquery<Integer> favoriteQuery = query.subquery(Integer.class);
                Root<Favorite> favoriteRoot = favoriteQuery.from(Favorite.class);
                favoriteQuery.select(cb.literal(1)).where(
                        cb.equal(favoriteRoot.get("id").get("packageId"), root.get("id")),
                        cb.equal(favoriteRoot.get("id").get("userId"), user.getId())
                );
                Predicate favoritePredicate = cb.exists(favoriteQuery);
                predicates.add(filter.favorite() ? favoritePredicate : cb.not(favoritePredicate));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return packageRepository.findAll(specification, pageable);
    }

    private Predicate tagPredicate(
            Root<KnowledgePackage> packageRoot,
            Subquery<Integer> subquery,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            TagFilter filter
    ) {
        Root<PackageTag> packageTag = subquery.from(PackageTag.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(packageTag.get("id").get("packageId"), packageRoot.get("id")));
        if (filter.id() != null) {
            predicates.add(cb.equal(packageTag.get("id").get("tagId"), filter.id()));
        } else {
            Root<Tag> tag = subquery.from(Tag.class);
            predicates.add(cb.equal(packageTag.get("id").get("tagId"), tag.get("id")));
            predicates.add(cb.equal(cb.lower(tag.get("name")), filter.name().toLowerCase(Locale.ROOT)));
        }
        subquery.select(cb.literal(1)).where(predicates.toArray(Predicate[]::new));
        return cb.exists(subquery);
    }

    private Predicate collectionPredicate(
            Root<KnowledgePackage> packageRoot,
            Subquery<Integer> subquery,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            CollectionFilter filter
    ) {
        Root<PackageCollection> packageCollection = subquery.from(PackageCollection.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(packageCollection.get("id").get("packageId"), packageRoot.get("id")));
        if (filter.id() != null) {
            predicates.add(cb.equal(packageCollection.get("id").get("collectionId"), filter.id()));
        } else {
            Root<CollectionEntity> collection = subquery.from(CollectionEntity.class);
            predicates.add(cb.equal(packageCollection.get("id").get("collectionId"), collection.get("id")));
            predicates.add(cb.equal(cb.lower(collection.get("name")), filter.name().toLowerCase(Locale.ROOT)));
        }
        subquery.select(cb.literal(1)).where(predicates.toArray(Predicate[]::new));
        return cb.exists(subquery);
    }

    private KnowledgePackage.Status parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return KnowledgePackage.Status.valueOf(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "status 参数无效");
        }
    }

    private KnowledgePackage.SourceType parseSource(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return KnowledgePackage.SourceType.valueOf(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "source 参数无效");
        }
    }

    private TagFilter parseTag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "tag_", 0, 4)) {
            try {
                return new TagFilter(IdPrefix.TAG.parse(normalized), null);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "tag 参数无效");
            }
        }
        return new TagFilter(null, normalized);
    }

    private CollectionFilter parseCollection(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "col_", 0, 4)) {
            try {
                return new CollectionFilter(IdPrefix.COLLECTION.parse(normalized), null);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "collection 参数无效");
            }
        }
        return new CollectionFilter(null, normalized);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record TagFilter(UUID id, String name) {
    }

    private record CollectionFilter(UUID id, String name) {
    }
}
