package chat.ui.data.model;

public class SimpleMessage {
    private String text;
    private String role;

    public SimpleMessage(String text, String role) {
        this.text = text;
        this.role = role;
    }

    public SimpleMessage() {
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
