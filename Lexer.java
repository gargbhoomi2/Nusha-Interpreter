import AST.Token;
import AST.Token.TokenTypes;

import java.util.LinkedList;
import java.util.Stack;

public class Lexer {
    private final TextManager tm;
    private final LinkedList<Token> tokens = new LinkedList<>();
    private final Stack<Integer> indentStack = new Stack<>();

    private int line = 1;
    private int charPos = 1;

    public Lexer(String input) {
        this.tm = new TextManager(input);
        indentStack.push(0); // base indent
    }

    public LinkedList<Token> Lex() throws SyntaxErrorException {
        while (!tm.isAtEnd()) {
            char c = tm.PeekCharacter();

            if (c == '\n') {
                tm.GetCharacter();
                tokens.add(new Token(TokenTypes.NEWLINE, line, charPos));
                line++;
                charPos = 1;

                int spaces = 0;
                while (!tm.isAtEnd() && (tm.PeekCharacter() == ' ' || tm.PeekCharacter() == '\t')) {
                    spaces += (tm.PeekCharacter() == '\t') ? 4 : 1;
                    tm.GetCharacter();
                }

                if (tm.isAtEnd() || tm.PeekCharacter() == '\n') continue; // skip blank line

                if (spaces % 4 != 0)
                    throw new SyntaxErrorException("Indentation must be multiple of 4", line, charPos);

                int currentIndent = indentStack.peek();
                if (spaces > currentIndent) {
                    indentStack.push(spaces);
                    tokens.add(new Token(TokenTypes.INDENT, line, 1));
                } else if (spaces < currentIndent) {
                    while (spaces < indentStack.peek()) {
                        indentStack.pop();
                        tokens.add(new Token(TokenTypes.DEDENT, line, 1));
                    }
                    if (spaces != indentStack.peek())
                        throw new SyntaxErrorException("Unmatched indentation", line, charPos);
                }
                charPos = spaces + 1;
                continue;
            }

            if (c == ' ' || c == '\t' || c == '\r') {
                tm.GetCharacter();
                charPos++;
                continue;
            }

            char next = tm.PeekCharacter(1);
            if (c == '=' && next == '>') {
                tm.GetCharacter(); tm.GetCharacter();
                tokens.add(new Token(TokenTypes.YIELDS, line, charPos));
                charPos += 2;
                continue;
            }
            if (c == '!' && next == '=') {
                tm.GetCharacter(); tm.GetCharacter();
                tokens.add(new Token(TokenTypes.NOTEQUAL, line, charPos));
                charPos += 2;
                continue;
            }

            switch (c) {
                case '=': emit(TokenTypes.EQUAL); continue;
                case '.': emit(TokenTypes.DOT); continue;
                case ',': emit(TokenTypes.COMMA); continue;
                case ':': emit(TokenTypes.COLON); continue;
                case '{': emit(TokenTypes.LEFTCURLY); continue;
                case '}': emit(TokenTypes.RIGHTCURLY); continue;
                case '[': emit(TokenTypes.LEFTBRACE); continue;
                case ']': emit(TokenTypes.RIGHTBRACE); continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int startPos = charPos;
                StringBuilder buf = new StringBuilder();
                while (!tm.isAtEnd() && (Character.isLetterOrDigit(tm.PeekCharacter()) || tm.PeekCharacter() == '_')) {
                    buf.append(tm.PeekCharacter());
                    tm.GetCharacter();
                    charPos++;
                }
                String word = buf.toString();
                if (word.equals("var")) tokens.add(new Token(TokenTypes.VAR, line, startPos, word));
                else if (word.equals("unique")) tokens.add(new Token(TokenTypes.UNIQUE, line, startPos, word));
                else tokens.add(new Token(TokenTypes.IDENTIFIER, line, startPos, word));
                continue;
            }

            if (Character.isDigit(c)) {
                int startPos = charPos;
                StringBuilder buf = new StringBuilder();
                while (!tm.isAtEnd() && Character.isDigit(tm.PeekCharacter())) {
                    buf.append(tm.PeekCharacter());
                    tm.GetCharacter();
                    charPos++;
                }
                tokens.add(new Token(TokenTypes.NUMBER, line, startPos, buf.toString()));
                continue;
            }

            throw new SyntaxErrorException("Unexpected character: " + c, line, charPos);
        }

        while (indentStack.size() > 1) {
            indentStack.pop();
            tokens.add(new Token(TokenTypes.DEDENT, line, 1));
        }
        tokens.add(new Token(TokenTypes.NEWLINE, line, charPos));

        return tokens;
    }

    private void emit(TokenTypes type) {
        tokens.add(new Token(type, line, charPos));
        tm.GetCharacter();
        charPos++;
    }
}
