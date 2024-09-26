package chat.ui.data.model;

public class SimpleMessage {
    private Long id;
    private String text;
    private String role;

    public SimpleMessage(Long id, String text, String role) {
        this.id = id;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
