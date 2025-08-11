package ru.art.tg_bot;

public enum Model {
    GPT_4_1("gpt-4.1"),
    GPT_5_MINI("gpt-5-mini"),
    GPT_5("gpt-5"),
    GPT_5_CHAT("gpt-5-chat-latest");

    private final String modelName;

    Model(String modelName) {
        this.modelName = modelName;
    }

    public String getName() {
        return modelName;
    }

    static Model getByName(String modelName) {
        switch (modelName) {
            case "gpt-4.1":
                return GPT_4_1;
                case "gpt-5-mini":
                return GPT_5_MINI;
                case "gpt-5":
                return GPT_5;
                case "gpt-5-chat-latest":
                return GPT_5_CHAT;
            default:
                return Model.valueOf(modelName);
        }
    }
}