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
    private final Command searchCommand;
    private final Command selectCommand;
    private final Command unknownCommand;

    public CommandDispatcher(TelegramBot telegramBot, CocktailDBService cocktailDBService, UserSessionService userSessionService) {
        this.commandMap = new HashMap<>();
        this.commandMap.put("/start", new StartCommand(telegramBot));

        this.searchCommand = new SearchCommand(telegramBot, cocktailDBService, userSessionService);
        this.selectCommand = new SelectCommand(telegramBot, cocktailDBService, userSessionService);
        this.unknownCommand = new UnknownCommand(telegramBot);
    }

    public void dispatch(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();

            Command command;
            if (messageText.startsWith("/")) {
                String commandIdentifier = messageText.split(" ")[0].toLowerCase();
                command = commandMap.getOrDefault(commandIdentifier, unknownCommand);
            } else {
                // Проверяем, является ли сообщение числом для выбора коктейля
                if (messageText.matches("\\d+")) {
                    command = selectCommand;
                } else {
                    // В противном случае, это поисковый запрос
                    command = searchCommand;
                }
            }
            command.execute(update);
        } else {
            // Если сообщение не содержит текста, используем UnknownCommand
            unknownCommand.execute(update);
        }
    }
}
