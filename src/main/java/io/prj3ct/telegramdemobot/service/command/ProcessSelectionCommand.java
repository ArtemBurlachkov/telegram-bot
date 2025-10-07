package io.prj3ct.telegramdemobot.service.command;

import io.prj3ct.telegramdemobot.dto.Cocktail;
import io.prj3ct.telegramdemobot.dto.CocktailDetails;
import io.prj3ct.telegramdemobot.service.CocktailDBService;
import io.prj3ct.telegramdemobot.service.TelegramBot;
import io.prj3ct.telegramdemobot.service.UserSessionService;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

public record ProcessSelectionCommand(TelegramBot telegramBot, CocktailDBService cocktailDBService,
                                      UserSessionService userSessionService) implements Command {

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();

        List<Cocktail> lastResult = userSessionService.getUserSearchResult(chatId);
        if (lastResult == null || lastResult.isEmpty()) {
            telegramBot.sendMessage(chatId, "Сначала выполните поиск, чтобы я мог показать вам рецепт. Введите ингредиент.");
            return;
        }

        int choice;
        try {
            choice = Integer.parseInt(messageText);
            if (choice < 1 || choice > lastResult.size()) {
                telegramBot.sendMessage(chatId, "Пожалуйста, выберите номер из списка.");
                return;
            }
        } catch (NumberFormatException e) {
            telegramBot.sendMessage(chatId, "Это не похоже на номер. Пожалуйста, выберите номер из списка.");
            return;
        }

        Cocktail selectedCocktail = lastResult.get(choice - 1);
        CocktailDetails details = cocktailDBService.findCocktailDetailsById(selectedCocktail.getId());

        if (details != null) {
            telegramBot.sendCocktailDetails(chatId, details);
        } else {
            telegramBot.sendMessage(chatId, "Не удалось получить детали рецепта. Попробуйте еще раз.");
        }
    }
}
