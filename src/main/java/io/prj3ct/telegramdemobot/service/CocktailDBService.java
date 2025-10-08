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

    private static final String INGREDIENTS_CACHE_KEY = "ingredients_list";

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
            CocktailCache cache = cachedResponse.get();
            log.info("Found response in cache for key: '{}'. Translated: {}", cacheKey, cache.isTranslated());
            try {
                List<Cocktail> cocktails = objectMapper.readValue(cache.getResponseJson(), new TypeReference<List<Cocktail>>() {});
                if (!cache.isTranslated() && queryIsCyrillic) {
                    log.info("Cache entry for '{}' is not translated. Attempting to translate now.", cacheKey);
                    try {
                        cocktails.forEach(cocktail -> cocktail.setName(translationService.translate(cocktail.getName(), "en", "ru")));
                        boolean isTranslated = !cocktails.isEmpty() && isCyrillic(cocktails.get(0).getName());
                        cacheAndLog(cacheKey, cocktails, CocktailCache.CacheType.INGREDIENT_SEARCH, isTranslated);
                        if (isTranslated) {
                            log.info("Successfully translated and updated cache for key: '{}'", cacheKey);
                        } else {
                            log.warn("Failed to translate from cache for key: '{}', translator might be down. Caching as untranslated.", cacheKey);
                        }
                        return cocktails;
                    } catch (Exception e) {
                        log.error("Failed to translate from cache for key: '{}'. Returning untranslated data.", cacheKey, e);
                    }
                }
                return cocktails;
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached cocktails for key: {}. Refetching.", cacheKey, e);
            }
        }

        log.info("No cache entry for key: '{}'. Requesting from API.", cacheKey);
        String translatedIngredientForApi = queryIsCyrillic ? translationService.translate(ingredient, "ru", "en") : ingredient;
        if (queryIsCyrillic) log.info("Translated ingredient '{}' to '{}'", ingredient, translatedIngredientForApi);

        String apiResponse = cocktailApiClient.findByIngredient(translatedIngredientForApi.toLowerCase().trim());
        List<Cocktail> cocktails = cocktailApiDataParser.parseCocktailList(apiResponse, translatedIngredientForApi);

        if (queryIsCyrillic) {
            try {
                cocktails.forEach(cocktail -> cocktail.setName(translationService.translate(cocktail.getName(), "en", "ru")));
                boolean isTranslated = !cocktails.isEmpty() && isCyrillic(cocktails.get(0).getName());
                cacheAndLog(cacheKey, cocktails, CocktailCache.CacheType.INGREDIENT_SEARCH, isTranslated);
            } catch (Exception e) {
                log.error("Failed to translate new data for key: '{}'. Caching untranslated data.", cacheKey, e);
                cacheAndLog(cacheKey, cocktails, CocktailCache.CacheType.INGREDIENT_SEARCH, false);
            }
        } else {
            cacheAndLog(cacheKey, cocktails, CocktailCache.CacheType.INGREDIENT_SEARCH, false);
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
            CocktailCache cache = cachedDetails.get();
            log.info("Found details in cache for cocktail ID: {}. Translated: {}", id, cache.isTranslated());
            try {
                CocktailDetails details = objectMapper.readValue(cache.getResponseJson(), CocktailDetails.class);
                if (!cache.isTranslated()) {
                    log.info("Details for ID '{}' are not translated. Attempting to translate now.", id);
                    try {
                        translateCocktailDetails(details);
                        boolean isTranslated = isCyrillic(details.getName());
                        cacheAndLog(id, details, CocktailCache.CacheType.COCKTAIL_DETAILS, isTranslated);
                        if (isTranslated) {
                            log.info("Successfully translated and updated cache for ID: '{}'", id);
                        } else {
                            log.warn("Failed to translate from cache for ID: '{}', translator might be down. Caching as untranslated.", id);
                        }
                        return details;
                    } catch (Exception e) {
                        log.error("Failed to translate details from cache for ID: '{}'. Returning untranslated data.", id, e);
                    }
                }
                return details;
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached cocktail details for ID: {}. Refetching.", id, e);
            }
        }

        log.info("No details in cache for cocktail ID: {}. Requesting from API.", id);
        String response = cocktailApiClient.findById(id);
        CocktailDetails details = cocktailApiDataParser.parseCocktailDetails(response);

        if (details != null) {
            try {
                translateCocktailDetails(details);
                boolean isTranslated = isCyrillic(details.getName());
                cacheAndLog(id, details, CocktailCache.CacheType.COCKTAIL_DETAILS, isTranslated);
            } catch (Exception e) {
                log.error("Failed to translate new details for ID: '{}'. Caching untranslated data.", id, e);
                cacheAndLog(id, details, CocktailCache.CacheType.COCKTAIL_DETAILS, false);
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
            CocktailCache cache = cachedIngredients.get();
            log.info("Found ingredients list in cache. Translated: {}", cache.isTranslated());
            try {
                List<String> ingredients = objectMapper.readValue(cache.getResponseJson(), new TypeReference<List<String>>() {});
                if (!cache.isTranslated()) {
                    log.info("Ingredients list is not translated. Attempting to translate now.");
                    try {
                        List<String> translatedIngredients = ingredients.stream()
                                .map(ing -> translationService.translate(ing, "en", "ru"))
                                .collect(Collectors.toList());
                        boolean isTranslated = !translatedIngredients.isEmpty() && isCyrillic(translatedIngredients.get(0));
                        cacheAndLog(INGREDIENTS_CACHE_KEY, translatedIngredients, CocktailCache.CacheType.INGREDIENTS_LIST, isTranslated);
                        if (isTranslated) {
                            log.info("Successfully translated and updated ingredients cache.");
                        } else {
                            log.warn("Failed to translate ingredients from cache, translator might be down. Caching as untranslated.");
                        }
                        return translatedIngredients;
                    } catch (Exception e) {
                        log.error("Failed to translate ingredients from cache. Returning untranslated.", e);
                    }
                }
                return ingredients;
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached ingredients list. Refetching.", e);
            }
        }

        log.info("No ingredients list in cache. Fetching and translating.");
        List<String> ingredients = getIngredientsList();
        try {
            List<String> translatedIngredients = ingredients.stream()
                    .map(ing -> translationService.translate(ing, "en", "ru"))
                    .collect(Collectors.toList());
            boolean isTranslated = !translatedIngredients.isEmpty() && isCyrillic(translatedIngredients.get(0));
            cacheAndLog(INGREDIENTS_CACHE_KEY, translatedIngredients, CocktailCache.CacheType.INGREDIENTS_LIST, isTranslated);
            return translatedIngredients;
        } catch (Exception e) {
            log.error("Failed to translate new ingredients list. Caching untranslated.", e);
            cacheAndLog(INGREDIENTS_CACHE_KEY, ingredients, CocktailCache.CacheType.INGREDIENTS_LIST, false);
            return ingredients;
        }
    }

    private void translateCocktailDetails(CocktailDetails details) {
        details.setName(translationService.translate(details.getName(), "en", "ru"));
        details.setInstructions(translationService.translate(details.getInstructions(), "en", "ru"));
        List<String> translatedIngredients = details.getIngredients().stream()
                .map(ing -> {
                    String[] parts = ing.split(" - ", 2);
                    String translatedIngredient = translationService.translate(parts[0], "en", "ru");
                    return parts.length > 1 ? translatedIngredient + " - " + parts[1] : translatedIngredient;
                })
                .collect(Collectors.toList());
        details.setIngredients(translatedIngredients);
    }

    private void cacheAndLog(String key, Object data, CocktailCache.CacheType type, boolean translated) {
        try {
            String json = objectMapper.writeValueAsString(data);

            Optional<CocktailCache> existingCacheOpt = cocktailCacheRepository.findByRequestKeyAndType(key, type);

            CocktailCache cacheToSave;
            if (existingCacheOpt.isPresent()) {
                cacheToSave = existingCacheOpt.get();
                cacheToSave.setResponseJson(json);
                cacheToSave.setTranslated(translated);
                log.info("Updating cache for key: '{}'. Translated: {}", key, translated);
            } else {
                cacheToSave = new CocktailCache(key, json, type, translated);
                log.info("Saving new cache for key: '{}'. Translated: {}", key, translated);
            }

            cocktailCacheRepository.save(cacheToSave);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize for caching. Key: '{}'", key, e);
        }
    }
}
