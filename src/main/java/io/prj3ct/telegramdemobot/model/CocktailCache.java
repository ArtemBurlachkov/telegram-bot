package io.prj3ct.telegramdemobot.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "cocktail_cache")
public class CocktailCache {

    public enum CacheType {
        INGREDIENT_SEARCH,
        COCKTAIL_DETAILS
    }

    @Id
    private String id;

    private String requestKey; // The search term (e.g., ingredient) or cocktail ID

    private String responseJson; // The JSON response from the external API or serialized DTO

    private CacheType type;

    public CocktailCache(String requestKey, String responseJson, CacheType type) {
        this.requestKey = requestKey;
        this.responseJson = responseJson;
        this.type = type;
    }

}