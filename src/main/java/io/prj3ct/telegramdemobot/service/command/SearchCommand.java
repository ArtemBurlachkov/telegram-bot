package io.prj3ct.telegramdemobot.service.command;

import io.prj3ct.telegramdemobot.dto.Cocktail;
import io.prj3ct.telegramdemobot.service.CocktailDBService;
import io.prj3ct.telegramdemobot.service.TelegramBot;
import io.prj3ct.telegramdemobot.service.UserSessionService;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record SearchCommand(TelegramBot telegramBot, CocktailDBService cocktailDBService,
                            UserSessionService userSessionService) implements Command {

    public static final String SEARCH_MESSAGE = "Введите название ингредиента (например, 'водка') или несколько через запятую (например, 'ром, мята').";

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText().trim();

        String query;
        if (messageText.toLowerCase().startsWith("/search")) {
            query = messageText.substring("/search".length()).trim();
        } else {
            query = messageText;
        }

        if (query.isEmpty()) {
            telegramBot.sendMessage(chatId, SEARCH_MESSAGE);
            return;
        }

        List<String> ingredients = Arrays.stream(messageText.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<Cocktail> cocktails;
        if (ingredients.size() > 1) {
            cocktails = cocktailDBService.findByMultipleIngredients(ingredients);
        } else {
            cocktails = cocktailDBService.findByIngredient(ingredients.get(0));
        }

        if (cocktails.isEmpty()) {
            telegramBot.sendMessage(chatId, "К сожалению, по вашему запросу ничего не найдено. Попробуйте другие ингредиенты.");
        } else {
            userSessionService.saveUserSearchResult(chatId, cocktails);
            String responseText = "Вот что я нашел:\n" +
                    IntStream.range(0, cocktails.size())
                            .mapToObj(i -> (i + 1) + ". " + cocktails.get(i).getName())
                            .collect(Collectors.joining("\n"));
            responseText += "\n\nОтправьте номер, чтобы получить рецепт.";
            telegramBot.sendMessage(chatId, responseText);
        }
    }
}
