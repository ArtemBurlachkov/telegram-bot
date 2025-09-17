package io.prj3ct.telegramdemobot.service;

import io.prj3ct.telegramdemobot.dto.Cocktail;
import io.prj3ct.telegramdemobot.dto.CocktailDetails;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class CocktailDBService {

    private final RestTemplate restTemplate;
    private final TranslationService translationService;

    public CocktailDBService(RestTemplate restTemplate, TranslationService translationService) {
        this.restTemplate = restTemplate;
        this.translationService = translationService;
    }

    private boolean isCyrillic(String text) {
        return text != null && text.matches(".*\\p{IsCyrillic}.*");
    }

    public List<Cocktail> findByIngredient(String ingredient) {
        boolean queryIsCyrillic = isCyrillic(ingredient);
        String translatedIngredient;

        if (queryIsCyrillic) {
            translatedIngredient = translationService.translate(ingredient, "ru", "en");
            if (translatedIngredient.equalsIgnoreCase(ingredient)) {
                log.warn("Translation for ingredient '{}' not found or failed, using original.", ingredient);
            } else {
                log.info("Translated ingredient '{}' to '{}'", ingredient, translatedIngredient);
            }
        } else {
            log.info("Ingredient '{}' is not in Cyrillic, using as is for API request.", ingredient);
            translatedIngredient = ingredient;
        }

        String url = "https://www.thecocktaildb.com/api/json/v1/1/filter.php?i=" + translatedIngredient.toLowerCase().trim();
        log.info("Requesting cocktails from URL: {}", url);
        String response = restTemplate.getForObject(url, String.class);

        if (response == null || response.trim().isEmpty() || response.trim().equals("{\"drinks\":null}")) {
            log.warn("Received null or empty response from CocktailDB for ingredient: {}", translatedIngredient);
            return Collections.emptyList();
        }

        JSONObject jsonObject = new JSONObject(response);
        if (jsonObject.isNull("drinks")) {
            log.info("No drinks found for ingredient: {}", translatedIngredient);
            return Collections.emptyList();
        }

        Object drinksObject = jsonObject.get("drinks");
        if (!(drinksObject instanceof JSONArray)) {
            if (drinksObject instanceof String) {
                log.info("No drinks found for ingredient '{}' (API returned a string: '{}')", translatedIngredient, drinksObject);
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

    public List<Cocktail> findByMultipleIngredients(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Collections.emptyList();
        }

        List<Cocktail> result = new ArrayList<>(findByIngredient(ingredients.get(0)));
        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        for (int i = 1; i < ingredients.size(); i++) {
            List<Cocktail> cocktailsForNextIngredient = findByIngredient(ingredients.get(i));
            result.retainAll(cocktailsForNextIngredient);
        }

        return result;
    }

    public CocktailDetails findCocktailDetailsById(String id) {
        String url = "https://www.thecocktaildb.com/api/json/v1/1/lookup.php?i=" + id;
        String response = restTemplate.getForObject(url, String.class);

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
        details.setImageUrl(drinkJson.getString("strDrinkThumb"));

        details.setInstructions(translationService.translate(drinkJson.getString("strInstructions"), "en", "ru"));

        List<String> ingredients = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            String ingredient = drinkJson.optString("strIngredient" + i);
            String measure = drinkJson.optString("strMeasure" + i);
            if (ingredient != null && !ingredient.trim().isEmpty() && !ingredient.equalsIgnoreCase("null")) {

                String translatedIngredient = translationService.translate(ingredient, "en", "ru");
                ingredients.add(translatedIngredient + (measure != null && !measure.trim().isEmpty() && !measure.equalsIgnoreCase("null") ? " - " + measure : ""));
            } else {
                break;
            }
        }
        details.setIngredients(ingredients);

        return details;
    }
}
