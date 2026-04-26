package com.bolt.repository;

import com.bolt.model.UrlDocument;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UrlRepository extends MongoRepository<UrlDocument, String> {

    Optional<UrlDocument> findByAliasAndActiveTrue(String alias);
    boolean existsByAlias(String alias);
}
