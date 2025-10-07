package io.prj3ct.telegramdemobot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prj3ct.telegramdemobot.dto.Cocktail;
import io.prj3ct.telegramdemobot.dto.CocktailDetails;
import io.prj3ct.telegramdemobot.model.CocktailCache;
import io.prj3ct.telegramdemobot.repository.CocktailCacheRepository;
import io.prj3ct.telegramdemobot.service.client.CocktailApiClient;
import io.prj3ct.telegramdemobot.service.parser.CocktailApiDataParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CocktailDBService {

    private static final String INGREDIENTS_CACHE_KEY = "ingredients_list_ru";

    private final TranslationService translationService;
    private final CocktailCacheRepository cocktailCacheRepository;
    private final ObjectMapper objectMapper;
    private final CocktailApiClient cocktailApiClient;
    private final CocktailApiDataParser cocktailApiDataParser;

    public CocktailDBService(TranslationService translationService,
                             CocktailCacheRepository cocktailCacheRepository,
                             ObjectMapper objectMapper,
                             CocktailApiClient cocktailApiClient,
                             CocktailApiDataParser cocktailApiDataParser) {
        this.translationService = translationService;
        this.cocktailCacheRepository = cocktailCacheRepository;
        this.objectMapper = objectMapper;
        this.cocktailApiClient = cocktailApiClient;
        this.cocktailApiDataParser = cocktailApiDataParser;
    }

    private boolean isCyrillic(String text) {
        return text != null && text.matches(".*\\p{IsCyrillic}.*");
    }

    public List<Cocktail> findByIngredient(String ingredient) {
        final String cacheKey = ingredient.toLowerCase().trim();
        final boolean queryIsCyrillic = isCyrillic(ingredient);

        Optional<CocktailCache> cachedResponse = cocktailCacheRepository.findByRequestKeyAndType(cacheKey, CocktailCache.CacheType.INGREDIENT_SEARCH);

        if (cachedResponse.isPresent()) {
            log.info("Found translated response in cache for key: {}", cacheKey);
            String cachedJson = cachedResponse.get().getResponseJson();
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<List<Cocktail>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached cocktails for key: {}. Refetching.", cacheKey, e);
            }
        }

        log.info("No cache entry for key: '{}'. Requesting from API.", cacheKey);
        String translatedIngredientForApi;
        if (queryIsCyrillic) {
            translatedIngredientForApi = translationService.translate(ingredient, "ru", "en");
            log.info("Translated ingredient '{}' to '{}'", ingredient, translatedIngredientForApi);
        } else {
            translatedIngredientForApi = ingredient;
        }

        String apiResponse = cocktailApiClient.findByIngredient(translatedIngredientForApi.toLowerCase().trim());

        List<Cocktail> cocktails = cocktailApiDataParser.parseCocktailList(apiResponse, queryIsCyrillic, translatedIngredientForApi);

        if (!cocktails.isEmpty()) {
            try {
                String translatedJson = objectMapper.writeValueAsString(cocktails);
                cocktailCacheRepository.save(new CocktailCache(cacheKey, translatedJson, CocktailCache.CacheType.INGREDIENT_SEARCH));
                log.info("Saved translated cocktails to cache for key: {}", cacheKey);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize translated cocktails for caching. Key: {}", cacheKey, e);
            }
        }

        return cocktails;
    }

    public List<Cocktail> findByMultipleIngredients(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Collections.emptyList();
        }

        List<Cocktail> result = findByIngredient(ingredients.get(0));
        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> resultIds = result.stream().map(Cocktail::getId).collect(Collectors.toList());

        for (int i = 1; i < ingredients.size(); i++) {
            List<Cocktail> cocktailsForNextIngredient = findByIngredient(ingredients.get(i));
            List<String> nextIds = cocktailsForNextIngredient.stream().map(Cocktail::getId).collect(Collectors.toList());
            resultIds.retainAll(nextIds);
        }

        return result.stream()
                .filter(cocktail -> resultIds.contains(cocktail.getId()))
                .collect(Collectors.toList());
    }

    public CocktailDetails findCocktailDetailsById(String id) {
        Optional<CocktailCache> cachedDetails = cocktailCacheRepository.findByRequestKeyAndType(id, CocktailCache.CacheType.COCKTAIL_DETAILS);

        if (cachedDetails.isPresent()) {
            log.info("Found details in cache for cocktail ID: {}", id);
            try {
                return objectMapper.readValue(cachedDetails.get().getResponseJson(), CocktailDetails.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached cocktail details for ID: {}", id, e);
            }
        }

        log.info("No details in cache for cocktail ID: {}. Requesting from API.", id);
        String response = cocktailApiClient.findById(id);
        CocktailDetails details = cocktailApiDataParser.parseCocktailDetails(response);

        if (details != null) {
            try {
                String detailsJson = objectMapper.writeValueAsString(details);
                cocktailCacheRepository.save(new CocktailCache(id, detailsJson, CocktailCache.CacheType.COCKTAIL_DETAILS));
                log.info("Saved translated details to cache for cocktail ID: {}", id);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize cocktail details for caching. ID: {}", id, e);
            }
        }

        return details;
    }

    public List<String> getIngredientsList() {
        String response = cocktailApiClient.listIngredients();
        return cocktailApiDataParser.parseIngredientsList(response);
    }



    public List<String> getTranslatedIngredients() {
        Optional<CocktailCache> cachedIngredients = cocktailCacheRepository.findByRequestKeyAndType(INGREDIENTS_CACHE_KEY, CocktailCache.CacheType.INGREDIENTS_LIST);

        if (cachedIngredients.isPresent()) {
            log.info("Found translated ingredients list in cache.");
            try {
                return objectMapper.readValue(cachedIngredients.get().getResponseJson(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached ingredients list. Refetching.", e);
            }
        }

        log.info("No translated ingredients list in cache. Fetching and translating.");
        List<String> ingredients = getIngredientsList();
        List<String> translatedIngredients = ingredients.stream()
                .map(ingredient -> translationService.translate(ingredient, "en", "ru"))
                .collect(Collectors.toList());

        try {
            String ingredientsJson = objectMapper.writeValueAsString(translatedIngredients);
            cocktailCacheRepository.save(new CocktailCache(INGREDIENTS_CACHE_KEY, ingredientsJson, CocktailCache.CacheType.INGREDIENTS_LIST));
            log.info("Saved translated ingredients list to cache.");
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ingredients list for caching.", e);
        }

        return translatedIngredients;
    }

}
