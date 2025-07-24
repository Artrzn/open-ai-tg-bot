package ru.art.tg_bot;

public enum Model {
    GPT_3_5_TURBO("gpt-3.5-turbo"),
    O3_MINI("o3-mini"),
    GPT_4O("gpt-4o"),
    O4_MINI("o4-mini"),
    GPT_4_1("gpt-4.1");

    private final String modelName;

    Model(String modelName) {
        this.modelName = modelName;
    }

    public String getName() {
        return modelName;
    }

    static Model getByName(String modelName) {
        switch (modelName) {
            case "gpt-3.5-turbo":
                return GPT_3_5_TURBO;
            case "o3-mini":
                return O3_MINI;
            case "gpt-4o":
                return GPT_4O;
            case "o4-mini":
                return O4_MINI;
            case "gpt-4.1":
                return GPT_4_1;
            default:
                return Model.valueOf(modelName);
        }
    }
}