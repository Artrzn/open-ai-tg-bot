package ru.art.tg_bot;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class ChatGptBot extends TelegramLongPollingBot {

    private final static Logger LOGGER = LogManager.getLogger(ChatGptBot.class);

    private final OpenAiService openAi;
    private final long ownerUserId;
    private final String botUserName;
    private static final List<String> AVAILABLE_MODELS = List.of("gpt-3.5-turbo", "gpt-4o");
    private int actualModelIndex = 0;
    private boolean isUseContext;
    private final Function<Boolean, String> getContextState = b -> b ? "On" : "Off";
    private String actualRole = ChatMessageRole.USER.value();
    private final List<ChatMessage> savedMessages = new ArrayList<>();

    public ChatGptBot(OpenAiService openAi, long ownerUserId, String botToken, String botUserName) {
        super(botToken);
        this.openAi = openAi;
        this.ownerUserId = ownerUserId;
        this.botUserName = botUserName;
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    public void onUpdateReceived(Update update) {
        try {
            Message message = update.getMessage();
            CallbackQuery callbackQuery = update.getCallbackQuery();
            if (message != null) {
                User messageAuthor = message.getFrom();
                Long userId = messageAuthor.getId();
                if (ownerUserId == userId) {
                    String text = message.getText();
                    LOGGER.info("Accepted text message: \"{}\"", text);
                    if (message.isCommand() && text.equals("/choose_model")) {
                        changeModel();
                    } else if (message.isCommand() && text.equals("/choose_context_mode")) {
                        changeContextMode();
                    } else if (message.isCommand() && text.equals("/choose_role")) {
                        changeRole();
                    } else if (message.isCommand() && text.startsWith("/")) {
                        sendTextMessage(String.format("Не поддерживаемая команда: %s\n ヽ(°□° )ノ", text));
                    } else {
                        askOpenAi(text);
                    }
                } else {
                    LOGGER.info("Accepted request from unauthorized user: {}. Request: {}", userId, message.getText());
                    sendTextMessage("Это частная вечеринка\n ヽ(°□° )ノ");
                }
            } else if (callbackQuery != null) {
                String callbackData = callbackQuery.getData();
                if (callbackData.equals("true") || callbackData.equals("false")) {
                    boolean newState = Boolean.parseBoolean(callbackData);
                    if (isUseContext != newState) {
                        LOGGER.info("Context support state was change from: {} to: {}", getContextState.apply(isUseContext), getContextState.apply(newState));
                    }
                    LOGGER.info("Clear messages");
                    savedMessages.clear();
                    isUseContext = newState;
                } else if (callbackData.equals("system") || callbackData.equals("user")) {
                    if (!actualRole.equals(callbackData)) {
                        LOGGER.info("Role was change from: {} to: {}.", actualRole, callbackData);
                    }
                    actualRole = callbackData;
                } else {
                    actualModelIndex = Integer.parseInt(callbackData);
                    LOGGER.info("Chosen model: {}.", AVAILABLE_MODELS.get(actualModelIndex));
                }
                sendTextMessage(String.format("Режим контекста: %s. Роль: %s. Выбранная модель: %s", getContextState.apply(isUseContext), actualRole, AVAILABLE_MODELS.get(actualModelIndex)));
            }
        } catch (Throwable t) {
            LOGGER.error("Some error: ", t);
        }
    }

    private void changeModel() throws TelegramApiException {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Выбери модель")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(InlineKeyboardButton
                                        .builder()
                                        .text(AVAILABLE_MODELS.get(0))
                                        .callbackData("0")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(AVAILABLE_MODELS.get(1))
                                        .callbackData("1")
                                        .build())
                        )
                        .build())
                .build());
    }

    private void changeContextMode() throws TelegramApiException {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Выбор режима контекста")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(InlineKeyboardButton
                                        .builder()
                                        .text("On")
                                        .callbackData("true")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text("Off")
                                        .callbackData("false")
                                        .build())
                        )
                        .build())
                .build());
    }

    private void changeRole() throws TelegramApiException {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Выбор роли")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(InlineKeyboardButton
                                        .builder()
                                        .text(ChatMessageRole.SYSTEM.value())
                                        .callbackData(ChatMessageRole.SYSTEM.value())
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(ChatMessageRole.USER.value())
                                        .callbackData(ChatMessageRole.USER.value())
                                        .build())
                        )
                        .build())
                .build());
    }

    private void askOpenAi(String ask) throws TelegramApiException {
        String model = AVAILABLE_MODELS.get(actualModelIndex);
        sendTextMessage(String.format("Жди... использована модель: %s. Режим контекста: %s. Роль: %s.", model, getContextState.apply(isUseContext), actualRole));

        List<ChatMessage> messages = isUseContext ? savedMessages : new ArrayList<>();
        ChatMessage userMessage = new ChatMessage(actualRole, ask);
        messages.add(userMessage);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model(model)
                .messages(messages)
                .n(1)
                .logitBias(new HashMap<>())
                .build();
        StringBuilder answer = new StringBuilder();
        openAi.streamChatCompletion(chatCompletionRequest)
                .doOnError(throwable -> {
                    LOGGER.error("Error while get answer from apenAi", throwable);
                    sendTextMessage(String.format("Ошибка выполнения запроса к openAi. %s", throwable.getMessage()));
                })
                .blockingForEach(chatCompletionChunk -> {
                    List<ChatCompletionChoice> choices = chatCompletionChunk.getChoices();
                    for (ChatCompletionChoice choice : choices) {
                        ChatMessage assistantMessage = choice.getMessage();
                        if (assistantMessage.getContent() != null) {
                            answer.append(choice.getMessage().getContent());
                        }
                    }
                });
        LOGGER.info("Accepted answer: {}", answer.toString());
        if (isUseContext) {
            messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), answer.toString()));
        }
        sendTextMessage(answer.toString());
    }

    private void sendTextMessage(String text) throws TelegramApiException {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .parseMode(ParseMode.MARKDOWNV2)
                .text(escapeMarkdown(text))
                .build());
    }

    private String escapeMarkdown(String text) {
        String[] specialChars = {"\\", "_", "*", "[", "]", "(", ")", "~", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!"};
        for (String specialChar : specialChars) {
            text = text.replace(specialChar, "\\" + specialChar);
        }
        return text;
    }

    private void sendMessage(SendMessage message) throws TelegramApiException {
        execute(message);
    }
}