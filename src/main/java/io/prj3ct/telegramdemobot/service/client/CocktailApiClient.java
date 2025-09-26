package io.prj3ct.telegramdemobot.service.client;

public interface CocktailApiClient {
    String findByIngredient(String ingredientName);
    String findById(String id);

}
