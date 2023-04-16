package ru.art.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.art.ChatGptBot;

@Configuration
@ConfigurationProperties(prefix = "tg.bot")
public class BotConfiguration {

    private String openAiApiKey;
    private long acceptedUserId;
    private String token;
    private String userName;

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public void setOpenAiApiKey(String openAiApiKey) {
        this.openAiApiKey = openAiApiKey;
    }

    public long getAcceptedUserId() {
        return acceptedUserId;
    }

    public void setAcceptedUserId(long acceptedUserId) {
        this.acceptedUserId = acceptedUserId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Bean
    public ChatGptBot getBot() {
        return new ChatGptBot(openAiApiKey, acceptedUserId, token, userName);
    }
}
