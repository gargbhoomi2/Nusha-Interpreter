import AST.*;
import java.util.LinkedList;
import java.util.Optional;

/**
 * A small token-stream helper that exposes the operations the parser/test-suite expects:
 * - MatchAndRemove(Token.TokenTypes) -> Optional<Token>
 * - Peek(int) -> Optional<Token>
 * - Done() -> boolean
 * - getCurrentLine(), getCurrentColumnNumber() (and getLine()/getColumn() for compatibility)
 *
 * Works with AST.Token as provided.
 */
public class TokenManager {
    private final LinkedList<Token> tokens;

    // Accept tokens list from parser
    public TokenManager(LinkedList<Token> tokens) {
        // keep a defensive copy to avoid external mutation
        this.tokens = (tokens == null) ? new LinkedList<>() : new LinkedList<>(tokens);
    }

    // No-arg constructor (safe default)
    public TokenManager() {
        this.tokens = new LinkedList<>();
    }

    // True when there are no tokens left
    public boolean Done() {
        return tokens.isEmpty();
    }

    // Return the optional token at offset i (does not remove)
    public Optional<Token> Peek(int i) {
        if (i < 0 || i >= tokens.size()) return Optional.empty();
        return Optional.of(tokens.get(i));
    }

    // Convenience: Peek the first (0)
    public Optional<Token> Peek() {
        return Peek(0);
    }

    // If the next token has the requested type, remove and return it
    public Optional<Token> MatchAndRemove(Token.TokenTypes t) {
        if (tokens.isEmpty()) return Optional.empty();
        Token head = tokens.peekFirst();
        if (head.Type == t) {
            return Optional.of(tokens.removeFirst());
        } else {
            return Optional.empty();
        }
    }

    // Provide a simple consume() used in some variants (removes head unconditionally)
    public Optional<Token> ConsumeIfAny() {
        if (tokens.isEmpty()) return Optional.empty();
        return Optional.of(tokens.removeFirst());
    }

    // Provide check without removing
    public boolean Check(Token.TokenTypes t) {
        return !tokens.isEmpty() && tokens.peekFirst().Type == t;
    }

    // Compatibility names found in some parser versions:
    public int getCurrentLine() {
        if (tokens.isEmpty()) return -1;
        return tokens.peekFirst().LineNumber;
    }

    public int getCurrentColumnNumber() {
        if (tokens.isEmpty()) return -1;
        return tokens.peekFirst().ColumnNumber;
    }

    // Some older code asked for getLine() / getColumn()
    public int getLine() {
        return getCurrentLine();
    }

    public int getColumn() {
        return getCurrentColumnNumber();
    }

    // For debugging
    @Override
    public String toString() {
        return tokens.toString();
    }
}
