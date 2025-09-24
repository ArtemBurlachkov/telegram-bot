package io.prj3ct.telegramdemobot.repository;

import io.prj3ct.telegramdemobot.model.CocktailCache;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CocktailCacheRepository extends MongoRepository<CocktailCache, String> {
    Optional<CocktailCache> findByRequestKeyAndType(String requestKey, CocktailCache.CacheType type);
}
