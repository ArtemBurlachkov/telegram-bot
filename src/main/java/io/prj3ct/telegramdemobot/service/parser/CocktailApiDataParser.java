package io.prj3ct.telegramdemobot.service.parser;

import io.prj3ct.telegramdemobot.dto.Cocktail;
import io.prj3ct.telegramdemobot.dto.CocktailDetails;
import io.prj3ct.telegramdemobot.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class CocktailApiDataParser {

    private final ImageService imageService;

    public List<Cocktail> parseCocktailList(String jsonResponse, String ingredientForLog) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.trim().equals("{\"drinks\":null}")) {
            log.warn("Received null or empty response from CocktailDB for ingredient: {}", ingredientForLog);
            return Collections.emptyList();
        }

        JSONObject jsonObject = new JSONObject(jsonResponse);
        if (jsonObject.isNull("drinks")) {
            log.info("No drinks found for ingredient: {}", ingredientForLog);
            return Collections.emptyList();
        }

        Object drinksObject = jsonObject.get("drinks");
        if (!(drinksObject instanceof JSONArray)) {
            if (drinksObject instanceof String) {
                log.info("No drinks found for ingredient '{}' (API returned a string: '{}')", ingredientForLog, drinksObject);
            } else {
                log.error("Expected 'drinks' to be a JSONArray, but got {}. Response: {}", drinksObject.getClass().getName(), jsonResponse);
            }
            return Collections.emptyList();
        }

        JSONArray drinks = (JSONArray) drinksObject;
        return StreamSupport.stream(drinks.spliterator(), false)
                .map(drinkObj -> {
                    JSONObject drinkJson = (JSONObject) drinkObj;
                    Cocktail cocktail = new Cocktail();
                    cocktail.setId(drinkJson.getString("idDrink"));
                    cocktail.setName(drinkJson.getString("strDrink"));
                    return cocktail;
                })
                .collect(Collectors.toList());
    }

    public CocktailDetails parseCocktailDetails(String jsonResponse) {
        if (jsonResponse == null) {
            return null;
        }

        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONArray drinks = jsonObject.getJSONArray("drinks");
        if (drinks.isEmpty()) {
            return null;
        }

        JSONObject drinkJson = drinks.getJSONObject(0);
        CocktailDetails details = new CocktailDetails();
        details.setId(drinkJson.getString("idDrink"));
        details.setName(drinkJson.getString("strDrink"));

        String imageUrl = drinkJson.optString("strDrinkThumb");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            details.setImage(imageService.downloadImage(imageUrl));
        }

        details.setInstructions(drinkJson.getString("strInstructions"));

        List<String> ingredientsList = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            String ingredient = drinkJson.optString("strIngredient" + i);
            String measure = drinkJson.optString("strMeasure" + i);
            if (ingredient != null && !ingredient.trim().isEmpty() && !ingredient.equalsIgnoreCase("null")) {
                ingredientsList.add(ingredient + (measure != null && !measure.trim().isEmpty() && !measure.equalsIgnoreCase("null") ? " - " + measure : ""));
            } else {
                break;
            }
        }
        details.setIngredients(ingredientsList);

        return details;
    }
    public List<String> parseIngredientsList(String jsonResponse) {
        if (jsonResponse == null) {
            return Collections.emptyList();
        }

        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONArray drinks = jsonObject.getJSONArray("drinks");
        if (drinks.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> ingredients = new ArrayList<>();
        for (int i = 0; i < drinks.length(); i++) {
            JSONObject ingredientJson = drinks.getJSONObject(i);
            ingredients.add(ingredientJson.getString("strIngredient1"));
        }
        return ingredients;
    }
}
