package io.prj3ct.telegramdemobot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prj3ct.telegramdemobot.dto.Cocktail;
import io.prj3ct.telegramdemobot.dto.CocktailDetails;
import io.prj3ct.telegramdemobot.model.CocktailCache;
import io.prj3ct.telegramdemobot.repository.CocktailCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class CocktailDBService {

    private final TranslationService translationService;
    private final CocktailCacheRepository cocktailCacheRepository;
    private final ObjectMapper objectMapper;
    private final ImageService imageService;
    private final CocktailDbClient cocktailDbClient; // Новая зависимость

    // Убираем RestTemplate из конструктора, добавляем CocktailDbClient
    public CocktailDBService(TranslationService translationService, CocktailCacheRepository cocktailCacheRepository, ObjectMapper objectMapper, ImageService imageService, CocktailDbClient cocktailDbClient) {
        this.translationService = translationService;
        this.cocktailCacheRepository = cocktailCacheRepository;
        this.objectMapper = objectMapper;
        this.imageService = imageService;
        this.cocktailDbClient = cocktailDbClient;
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

        // Используем новый клиент для получения данных
        String apiResponse = cocktailDbClient.findByIngredient(translatedIngredientForApi.toLowerCase().trim());

        List<Cocktail> translatedCocktails = parseAndTranslateApiResponse(apiResponse, queryIsCyrillic, translatedIngredientForApi);

        if (!translatedCocktails.isEmpty()) {
            try {
                String translatedJson = objectMapper.writeValueAsString(translatedCocktails);
                cocktailCacheRepository.save(new CocktailCache(cacheKey, translatedJson, CocktailCache.CacheType.INGREDIENT_SEARCH));
                log.info("Saved translated cocktails to cache for key: {}", cacheKey);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize translated cocktails for caching. Key: {}", cacheKey, e);
            }
        }

        return translatedCocktails;
    }

    // ... (метод findByMultipleIngredients остается без изменений) ...

    public List<Cocktail> findByMultipleIngredients(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Collections.emptyList();
        }

        // Начинаем с первого ингредиента
        List<Cocktail> result = findByIngredient(ingredients.get(0));
        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        // Сохраняем ID для быстрой проверки пересечений
        List<String> resultIds = result.stream().map(Cocktail::getId).collect(Collectors.toList());

        // Итерируемся по остальным ингредиентам
        for (int i = 1; i < ingredients.size(); i++) {
            List<Cocktail> cocktailsForNextIngredient = findByIngredient(ingredients.get(i));
            List<String> nextIds = cocktailsForNextIngredient.stream().map(Cocktail::getId).collect(Collectors.toList());

            // Оставляем только те ID, которые есть в обоих списках
            resultIds.retainAll(nextIds);
        }

        // Фильтруем итоговый список по ID
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
        // Используем новый клиент
        String response = cocktailDbClient.findById(id);

        if (response == null) {
            return null;
        }

        JSONObject jsonObject = new JSONObject(response);
        JSONArray drinks = jsonObject.getJSONArray("drinks");
        if (drinks.isEmpty()) {
            return null;
        }

        JSONObject drinkJson = drinks.getJSONObject(0);
        CocktailDetails details = new CocktailDetails();
        details.setId(drinkJson.getString("idDrink"));
        details.setName(translationService.translate(drinkJson.getString("strDrink"), "en", "ru"));

        String imageUrl = drinkJson.optString("strDrinkThumb");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            details.setImage(imageService.downloadImage(imageUrl));
        }

        details.setInstructions(translationService.translate(drinkJson.getString("strInstructions"), "en", "ru"));

        List<String> ingredientsList = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            String ingredient = drinkJson.optString("strIngredient" + i);
            String measure = drinkJson.optString("strMeasure" + i);
            if (ingredient != null && !ingredient.trim().isEmpty() && !ingredient.equalsIgnoreCase("null")) {
                String translatedIngredient = translationService.translate(ingredient, "en", "ru");
                ingredientsList.add(translatedIngredient + (measure != null && !measure.trim().isEmpty() && !measure.equalsIgnoreCase("null") ? " - " + measure : ""));
            } else {
                break;
            }
        }
        details.setIngredients(ingredientsList);

        try {
            String detailsJson = objectMapper.writeValueAsString(details);
            cocktailCacheRepository.save(new CocktailCache(id, detailsJson, CocktailCache.CacheType.COCKTAIL_DETAILS));
            log.info("Saved translated details to cache for cocktail ID: {}", id);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cocktail details for caching. ID: {}", id, e);
        }

        return details;
    }

    // ... (метод parseAndTranslateApiResponse остается без изменений) ...
    private List<Cocktail> parseAndTranslateApiResponse(String response, boolean queryIsCyrillic, String apiIngredient) {
        if (response == null || response.trim().isEmpty() || response.trim().equals("{\"drinks\":null}")) {
            log.warn("Received null or empty response from CocktailDB for ingredient: {}", apiIngredient);
            return Collections.emptyList();
        }

        JSONObject jsonObject = new JSONObject(response);
        if (jsonObject.isNull("drinks")) {
            log.info("No drinks found for ingredient: {}", apiIngredient);
            return Collections.emptyList();
        }

        Object drinksObject = jsonObject.get("drinks");
        if (!(drinksObject instanceof JSONArray)) {
            if (drinksObject instanceof String) {
                log.info("No drinks found for ingredient '{}' (API returned a string: '{}')", apiIngredient, drinksObject);
            } else {
                log.error("Expected 'drinks' to be a JSONArray, but got {}. Response: {}", drinksObject.getClass().getName(), response);
            }
            return Collections.emptyList();
        }

        JSONArray drinks = (JSONArray) drinksObject;
        return StreamSupport.stream(drinks.spliterator(), false)
                .map(drinkObj -> {
                    JSONObject drinkJson = (JSONObject) drinkObj;
                    Cocktail cocktail = new Cocktail();
                    cocktail.setId(drinkJson.getString("idDrink"));

                    String originalName = drinkJson.getString("strDrink");
                    if (queryIsCyrillic) {
                        String translatedName = translationService.translate(originalName, "en", "ru");
                        cocktail.setName(translatedName);
                    } else {
                        cocktail.setName(originalName);
                    }
                    return cocktail;
                })
                .collect(Collectors.toList());
    }
}