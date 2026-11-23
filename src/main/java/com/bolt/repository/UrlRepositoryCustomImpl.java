package com.bolt.repository;

import com.bolt.model.UrlDocument;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

@RequiredArgsConstructor
public class UrlRepositoryCustomImpl implements UrlRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public boolean deactivateActive(String alias, Instant now) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("alias").is(alias),
                Criteria.where("active").is(true),
                new Criteria().orOperator(
                        Criteria.where("expiresAt").is(null),
                        Criteria.where("expiresAt").gt(now)
                )
        ));
        UpdateResult result = mongoTemplate.updateFirst(query, Update.update("active", false), UrlDocument.class);
        return result.getModifiedCount() > 0;
    }

    @Override
    public boolean deactivateExpired(String alias, Instant now) {
        Query query = new Query(
                Criteria.where("alias").is(alias)
                        .and("active").is(true)
                        .and("expiresAt").ne(null).lte(now)
        );
        UpdateResult result = mongoTemplate.updateFirst(query, Update.update("active", false), UrlDocument.class);
        return result.getModifiedCount() > 0;
    }
}
