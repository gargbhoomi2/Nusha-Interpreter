public class TextManager {
    private final String content;
    private int position = 0;

    public TextManager(String content) {
        if (content.isEmpty() || content.charAt(content.length() - 1) != '\n') {
            content += "\n";
        }
        this.content = content;
    }

    public boolean isAtEnd() {
        return position >= content.length();
    }

    public char PeekCharacter() {
        if (isAtEnd()) return '\0';
        return content.charAt(position);
    }

    public char PeekCharacter(int offset) {
        int target = position + offset;
        if (target >= content.length()) return '\0';
        return content.charAt(target);
    }

    public char GetCharacter() {
        if (isAtEnd()) return '\0';
        return content.charAt(position++);
    }
}
