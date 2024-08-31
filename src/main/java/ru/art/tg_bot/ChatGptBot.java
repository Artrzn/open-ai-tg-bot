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
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ChatGptBot extends TelegramLongPollingBot {

    private final static Logger LOGGER = LogManager.getLogger(ChatGptBot.class);

    private final OpenAiService openAi;
    private final long ownerUserId;
    private final String botUserName;
    private static final List<String> AVAILABLE_MODELS = List.of("gpt-3.5-turbo", "gpt-4o");
    private String actualModel = AVAILABLE_MODELS.get(0);
    private boolean isUseContext;
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
                if (isUseContext && !newState) {
                    savedMessages.clear();
                    LOGGER.info("Clear messages");
                }
                isUseContext = newState;
                LOGGER.info("Context support state was change to: {}. Chosen model: {}", isUseContext, actualModel);
                sendTextMessage(String.format("Поддержка контекста: %s. Выбранная модель: %s", isUseContext, actualModel));
            } else {
                LOGGER.info("Chosen model: {}. Context support: {}", callbackData, isUseContext);
                actualModel = callbackData;
                sendTextMessage(String.format("Выбрана модель: %s. Поддержка контекста: %s", actualModel, isUseContext));
            }
        }
    }

    private void changeModel() {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Выбери модель")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(InlineKeyboardButton
                                        .builder()
                                        .text(AVAILABLE_MODELS.get(0))
                                        .callbackData(AVAILABLE_MODELS.get(0))
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(AVAILABLE_MODELS.get(1))
                                        .callbackData(AVAILABLE_MODELS.get(1))
                                        .build())
                        )
                        .build())
                .build());
    }

    private void changeContextMode() {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .text("Чат с поддержкой контекста или нет")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(Arrays.asList(InlineKeyboardButton
                                        .builder()
                                        .text("Контекст включен")
                                        .callbackData("true")
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text("Контекст выключен")
                                        .callbackData("false")
                                        .build())
                        )
                        .build())
                .build());
    }

    private void askOpenAi(String ask) {
        sendTextMessage(String.format("Жди... использована модель: %s. Поддержка контекста: %s", actualModel, isUseContext));

        List<ChatMessage> messages = isUseContext ? savedMessages : new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), ask);
        messages.add(systemMessage);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model(actualModel)
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
                        if (choice.getMessage().getContent() != null) {
                            answer.append(choice.getMessage().getContent());
                        }
                    }
                });
        LOGGER.info("Accepted answer: {}", answer.toString());

        sendTextMessage(answer.toString());
    }

    private void sendTextMessage(String text) {
        sendMessage(SendMessage.builder()
                .chatId(ownerUserId)
                .parseMode(ParseMode.MARKDOWNV2)
                .text(text)
                .build());
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}