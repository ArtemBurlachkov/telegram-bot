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

public record SelectIngredientCommand(TelegramBot telegramBot, CocktailDBService cocktailDBService,
                                      UserSessionService userSessionService) implements Command {

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        try {
            List<String> allIngredients = cocktailDBService.getTranslatedIngredients();
            List<String> selectedIngredients = Arrays.stream(text.split(","))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .filter(n -> n > 0 && n <= allIngredients.size())
                    .mapToObj(n -> allIngredients.get(n - 1))
                    .collect(Collectors.toList());

            if (selectedIngredients.isEmpty()) {
                telegramBot.sendMessage(chatId, "Некорректные номера. Попробуйте еще раз, вы все еще в режиме выбора ингредиентов.");
                return;
            }

            List<Cocktail> cocktails = cocktailDBService.findByMultipleIngredients(selectedIngredients);

            if (cocktails.isEmpty()) {
                telegramBot.sendMessage(chatId, "Коктейли с таким сочетанием ингредиентов не найдены. Вы можете попробовать другие номера.");
            } else {
                userSessionService.saveUserSearchResult(chatId, cocktails);
                String responseText = "Вот что я нашел:\n" +
                        IntStream.range(0, cocktails.size())
                                .mapToObj(i -> (i + 1) + ". " + cocktails.get(i).getName())
                                .collect(Collectors.joining("\n"));
                responseText += "\n\nОтправьте номер, чтобы получить рецепт.";
                telegramBot.sendMessage(chatId, responseText);
                userSessionService.clearUserState(chatId);
            }

        } catch (NumberFormatException e) {
            telegramBot.sendMessage(chatId, "Пожалуйста, введите номера ингредиентов в виде чисел, например: 5 или 7, 12, 23. Вы все еще в режиме выбора ингредиентов.");
        }
    }
}
