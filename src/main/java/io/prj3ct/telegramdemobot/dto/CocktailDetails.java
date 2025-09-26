package io.prj3ct.telegramdemobot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CocktailDetails extends Cocktail {
    private byte[] image;
    private String instructions;
    private List<String> ingredients;
}
