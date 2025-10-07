package io.prj3ct.telegramdemobot.service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CocktailDbClient implements CocktailApiClient {

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;

    public CocktailDbClient(RestTemplate restTemplate, @Value("${cocktaildb.api.base-url}") String apiBaseUrl) {
        this.restTemplate = restTemplate;
        this.apiBaseUrl = apiBaseUrl;
    }

    @Override
    public String findByIngredient(String ingredientName) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiBaseUrl + "filter.php")
                .queryParam("i", ingredientName);
        return restTemplate.getForObject(builder.toUriString(), String.class);
    }

    @Override
    public String findById(String id) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiBaseUrl + "lookup.php")
                .queryParam("i", id);
        return restTemplate.getForObject(builder.toUriString(), String.class);
    }
    @Override
    public String listIngredients() {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiBaseUrl + "list.php")
                .queryParam("i", "list");
        return restTemplate.getForObject(builder.toUriString(), String.class);
    }

}
