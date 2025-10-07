package io.prj3ct.telegramdemobot.service.command;

import io.prj3ct.telegramdemobot.service.CocktailDBService;
import io.prj3ct.telegramdemobot.service.TelegramBot;
import io.prj3ct.telegramdemobot.service.UserSessionService;
import lombok.AllArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@AllArgsConstructor
public class IngredientsCommand implements Command {

    private final TelegramBot telegramBot;
    private final CocktailDBService cocktailDBService;
    private final UserSessionService userSessionService;
    private static final int PAGE_SIZE = 20;

    @Override
    public void execute(Update update) {
        if (update.hasCallbackQuery()) {
            processCallback(update);
        } else {
            sendFirstPage(update);
        }
    }

    private void sendFirstPage(Update update) {
        long chatId = update.getMessage().getChatId();
        List<String> ingredients = cocktailDBService.getTranslatedIngredients();
        if (ingredients.isEmpty()) {
            telegramBot.sendMessage(chatId, "Не удалось получить список ингредиентов.");
            return;
        }

        String text = formatPage(ingredients, 0);
        InlineKeyboardMarkup keyboard = createKeyboard(0, ingredients.size());
        telegramBot.sendMessage(chatId, text, keyboard);
        userSessionService.setUserState(chatId, UserSessionService.UserState.AWAITING_INGREDIENT_SELECTION);
    }

    private void processCallback(Update update) {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        String[] callbackData = update.getCallbackQuery().getData().split("_");
        int page = Integer.parseInt(callbackData[1]);

        List<String> ingredients = cocktailDBService.getTranslatedIngredients();
        String text = formatPage(ingredients, page);
        InlineKeyboardMarkup keyboard = createKeyboard(page, ingredients.size());

        telegramBot.editMessage(chatId, messageId, text, keyboard);
    }

    private String formatPage(List<String> ingredients, int page) {
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, ingredients.size());

        return "Доступные ингредиенты (страница " + (page + 1) + "):\n" +
                IntStream.range(start, end)
                        .mapToObj(i -> (i + 1) + ". " + ingredients.get(i))
                        .collect(Collectors.joining("\n"));
    }

    private InlineKeyboardMarkup createKeyboard(int currentPage, int totalSize) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        if (currentPage > 0) {
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Назад");
            backButton.setCallbackData("/ingredients_" + (currentPage - 1));
            row.add(backButton);
        }

        if ((currentPage + 1) * PAGE_SIZE < totalSize) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("Вперед ➡️");
            nextButton.setCallbackData("/ingredients_" + (currentPage + 1));
            row.add(nextButton);
        }

        rows.add(row);
        return new InlineKeyboardMarkup(rows);
    }
}
