package com.bolt.repository;

import com.bolt.model.UrlDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UrlRepository extends MongoRepository<UrlDocument, String> {

    Optional<UrlDocument> findByAliasAndActiveTrue(String alias);
    boolean existsByAliasAndActiveTrue(String alias);
}
