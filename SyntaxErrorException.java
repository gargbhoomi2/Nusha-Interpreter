import AST.Token;

public class SyntaxErrorException extends Exception {
    private final int lineNumber;
    private final int charPosition;

    public SyntaxErrorException(String message, int lineNumber, int charPosition) {
        super(message);
        this.lineNumber = lineNumber;
        this.charPosition = charPosition;
    }

    public SyntaxErrorException(Token first, Token second, int lineNumber, int charPosition) throws SyntaxErrorException {
        this.lineNumber = lineNumber;
        this.charPosition = charPosition;

        // Check token type
        if (second != null && second.Type != Token.TokenTypes.IDENTIFIER) {
            throw new SyntaxErrorException("Expected IDENTIFIER but found: " + second.Type, second.LineNumber, second.ColumnNumber);
        }

        // Compare token values
        String a = first.Value.orElse("");
        String b = second != null ? second.Value.orElse("") : "";
        if (!a.equals(b)) {
            throw new SyntaxErrorException("First Value differs: " + a + " vs " + b, first.LineNumber, first.ColumnNumber);
        }
    }

    @Override
    public String toString() {
        return "Error at line " + lineNumber + " at character " + charPosition + " : " + super.toString();
    }
}
