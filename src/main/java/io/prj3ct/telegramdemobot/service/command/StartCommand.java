package io.prj3ct.telegramdemobot.service.command;

import io.prj3ct.telegramdemobot.service.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.Update;

public class StartCommand implements Command {

    private final TelegramBot telegramBot;

    private static final String START_MESSAGE = "Привет! Я бот для поиска коктейлей. " +
            "Просто отправь мне название ингредиента (например, 'водка'), " +
            "и я найду коктейли, в которых он есть.\\n\\n" +
            "Ты также можешь искать по нескольким ингредиентам, перечислив их через запятую (например, 'ром, мята, лайм').";

    public StartCommand(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        telegramBot.sendMessage(chatId, START_MESSAGE);
    }
}
