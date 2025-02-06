package chat.ui.data.model;

import java.util.List;

public class ChatHistory {
    public String id;
    public long sessinId;
    public String text;
    public String settingName;
    public String charater;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getSessinId() {
        return sessinId;
    }

    public void setSessinId(long sessinId) {
        this.sessinId = sessinId;
    }

    public String getCharater() {
        return charater;
    }

    public void setCharater(String charater) {
        this.charater = charater;
    }

    public String getSettingName() {
        return settingName;
    }

    public void setSettingName(String settingName) {
        this.settingName = settingName;
    }

    @Override
    public String toString() {
        return "ChatHistory{" +
                "id='" + id + '\'' +
                ", sessinId=" + sessinId +
                ", text='" + text + '\'' +
                ", settingName='" + settingName + '\'' +
                ", charater='" + charater + '\'' +
                '}';
    }
}
