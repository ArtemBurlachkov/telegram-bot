package io.prj3ct.telegramdemobot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class CocktailDbClient {

    private final RestTemplate restTemplate;

    @Value("${cocktaildb.api.base-url}")
    private String apiBaseUrl;

    public CocktailDbClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String findByIngredient(String ingredient) {
        String url = apiBaseUrl + "/filter.php?i=" + ingredient;
        log.info("Requesting cocktails from URL: {}", url);
        return restTemplate.getForObject(url, String.class);
    }

    public String findById(String id) {
        String url = apiBaseUrl + "/lookup.php?i=" + id;
        log.info("Requesting cocktail details from URL: {}", url);
        return restTemplate.getForObject(url, String.class);
    }
}
