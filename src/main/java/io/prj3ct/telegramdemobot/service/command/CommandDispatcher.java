package io.prj3ct.telegramdemobot.service.command;

import io.prj3ct.telegramdemobot.service.CocktailDBService;
import io.prj3ct.telegramdemobot.service.TelegramBot;
import io.prj3ct.telegramdemobot.service.UserSessionService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;

@Component
public class CommandDispatcher {

    private final Map<String, Command> commandMap;
    private final Command processSelectionCommand;
    private final Command unknownCommand;
    private final Command searchCommand;
    private final UserSessionService userSessionService;
    private final Command selectIngredientCommand;


    public CommandDispatcher(TelegramBot telegramBot, CocktailDBService cocktailDBService, UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
        this.commandMap = new HashMap<>();
        commandMap.put("/start", new StartCommand(telegramBot));

        this.searchCommand = new SearchCommand(telegramBot, cocktailDBService, userSessionService);
        commandMap.put("/search", this.searchCommand);

        commandMap.put("/ingredients", new IngredientsCommand(telegramBot, cocktailDBService, userSessionService));
        this.selectIngredientCommand = new SelectIngredientCommand(telegramBot, cocktailDBService, userSessionService);

        this.processSelectionCommand = new ProcessSelectionCommand(telegramBot, cocktailDBService, userSessionService);
        this.unknownCommand = new UnknownCommand(telegramBot);
    }

    public void dispatch(Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            if (callbackData.startsWith("/ingredients")) {
                commandMap.get("/ingredients").execute(update);
            }
            // Здесь можно будет добавить обработку для других callback'ов
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText().trim();
            UserSessionService.UserState userState = userSessionService.getUserState(chatId);

            if (userState == UserSessionService.UserState.AWAITING_INGREDIENT_SELECTION) {
                if (messageText.matches("^[0-9, ]+$")) {
                    selectIngredientCommand.execute(update);
                } else {
                    userSessionService.clearUserState(chatId);
                    dispatchMessage(update, messageText);
                }
            } else {
                dispatchMessage(update, messageText);
            }
        }
    }

    private void dispatchMessage(Update update, String messageText) {
        if (messageText.startsWith("/")) {
            String commandIdentifier = messageText.split(" ")[0].toLowerCase();
            Command command = commandMap.getOrDefault(commandIdentifier, unknownCommand);
            command.execute(update);
        } else {
            try {
                // Check if the message is a number, which indicates a selection
                Integer.parseInt(messageText);
                processSelectionCommand.execute(update);
            } catch (NumberFormatException e) {
                // If it's not a number, treat it as a search query
                searchCommand.execute(update);
            }
        }
    }
}
