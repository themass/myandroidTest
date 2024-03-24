package chat.asrassistant.model;

import java.util.List;

public class StreamAiAnswer {

    public String id;

    public String object;

    public int created;

    public String model;

    public List<Choices> choices;

    public static class Choices {

        public Delta delta;
        //非流式
        public Delta message;

        public int index;

        public String finish_reason;
    }

    public static class Delta {
        public String role;

        public String content;
    }
}
