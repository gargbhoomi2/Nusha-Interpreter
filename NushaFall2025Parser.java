import AST.*;
import java.util.LinkedList;
import java.util.Optional;

/**
 * Parser that exposes the test-required entry:
 *   public Optional<Nusha> Nusha(LinkedList<Token> tokens) throws SyntaxErrorException
 *
 * Implementation follows the same control flow and semantics you provided,
 * but uses slightly different helper names internally.
 */
public class NushaFall2025Parser {
    private TokenManager tm;

    // no-arg constructor (tests sometimes construct parser with no args)
    public NushaFall2025Parser() { }

    // entry required by tests (keeps the same name as in tests)
    public Optional<Nusha> Nusha(LinkedList<Token> tokens) throws SyntaxErrorException {
        // initialize token stream manager
        this.tm = new TokenManager(tokens);
        if (tm.Done()) return Optional.empty();

        // build root
        Nusha root = new Nusha();
        root.definitions = new Definitions();
        root.variables = new Variables();
        root.rules = new Rules();

        root.definitions.definition = new LinkedList<>();
        root.variables.variable = new LinkedList<>();
        root.rules.rule = new LinkedList<>();

        // main loop: skip blank lines, then parse def / var / rule
        while (!tm.Done()) {
            skipBlankLines();

            if (tm.Done()) break;

            // definition: IDENTIFIER EQUAL ...
            if (isDefinitionStart()) {
                Definition d = parseDefinition();
                root.definitions.definition.add(d);
                skipBlankLines();
                continue;
            }

            // variable: VAR ...
            if (isVariableStart()) {
                Optional<Variable> v = parseVariable();
                v.ifPresent(root.variables.variable::add);
                skipBlankLines();
                continue;
            }

            // otherwise try rule
            try {
                Rule r = parseRule();
                root.rules.rule.add(r);
                skipBlankLines();
            } catch (SyntaxErrorException ex) {
                // stop parsing on clean failure (same behavior as your original)
                break;
            }
        }

        return Optional.of(root);
    }

    // ---------- small helpers ----------
    private void skipBlankLines() {
        while (tm.MatchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) { /* loop */ }
    }

    private boolean isDefinitionStart() {
        return tm.Peek(0).isPresent()
                && tm.Peek(0).get().Type == Token.TokenTypes.IDENTIFIER
                && tm.Peek(1).isPresent()
                && tm.Peek(1).get().Type == Token.TokenTypes.EQUAL;
    }

    private boolean isVariableStart() {
        return tm.Peek(0).isPresent()
                && tm.Peek(0).get().Type == Token.TokenTypes.VAR;
    }

    // ---------- parsing building blocks ----------
    private Definition parseDefinition() throws SyntaxErrorException {
        Token nameTok = require(Token.TokenTypes.IDENTIFIER);
        require(Token.TokenTypes.EQUAL);

        // choices: { id (, id)* }
        if (tm.MatchAndRemove(Token.TokenTypes.LEFTCURLY).isPresent()) {
            LinkedList<String> choicesList = new LinkedList<>();
            Token first = require(Token.TokenTypes.IDENTIFIER);
            choicesList.add(first.Value.orElseThrow());
            while (tm.MatchAndRemove(Token.TokenTypes.COMMA).isPresent()) {
                Token nxt = require(Token.TokenTypes.IDENTIFIER);
                choicesList.add(nxt.Value.orElseThrow());
            }
            require(Token.TokenTypes.RIGHTCURLY);
            requireNewLine();

            Choices cs = new Choices();
            cs.choice = choicesList;

            Definition def = new Definition();
            def.definitionName = nameTok.Value.orElseThrow();
            def.choices = Optional.of(cs);
            def.nstruct = Optional.empty();
            return def;
        }

        // nstruct: [ entry (, entry)* ]
        if (tm.MatchAndRemove(Token.TokenTypes.LEFTBRACE).isPresent()) {
            LinkedList<Entry> entries = new LinkedList<>();
            entries.add(parseEntry());
            while (tm.MatchAndRemove(Token.TokenTypes.COMMA).isPresent()) {
                entries.add(parseEntry());
            }
            require(Token.TokenTypes.RIGHTBRACE);
            requireNewLine();

            NStruct ns = new NStruct();
            ns.entry = entries;

            Definition def = new Definition();
            def.definitionName = nameTok.Value.orElseThrow();
            def.choices = Optional.empty();
            def.nstruct = Optional.of(ns);
            return def;
        }

        // neither choices nor nstruct -> syntax error
        throw new SyntaxErrorException("Expected '{' or '[' in definition",
                tm.getCurrentLine(), tm.getCurrentColumnNumber());
    }

    private Entry parseEntry() throws SyntaxErrorException {
        boolean unique = tm.MatchAndRemove(Token.TokenTypes.UNIQUE).isPresent();
        Token typeTok = require(Token.TokenTypes.IDENTIFIER);
        Token nameTok = require(Token.TokenTypes.IDENTIFIER);

        Entry e = new Entry();
        e.unique = unique;
        e.type = typeTok.Value.orElseThrow();
        e.name = nameTok.Value.orElseThrow();
        return e;
    }

    private Optional<Variable> parseVariable() throws SyntaxErrorException {
        if (tm.MatchAndRemove(Token.TokenTypes.VAR).isEmpty()) return Optional.empty();

        Token name = require(Token.TokenTypes.IDENTIFIER);
        require(Token.TokenTypes.COLON);
        Token type = require(Token.TokenTypes.IDENTIFIER);

        Optional<String> size = Optional.empty();
        if (tm.MatchAndRemove(Token.TokenTypes.LEFTBRACE).isPresent()) {
            Token num = require(Token.TokenTypes.NUMBER);
            size = Optional.of(num.Value.orElseThrow());
            require(Token.TokenTypes.RIGHTBRACE);
        }

        requireNewLine();

        Variable v = new Variable();
        v.variableName = name.Value.orElseThrow();
        v.type = type.Value.orElseThrow();
        v.size = size;
        return Optional.of(v);
    }

    private Rule parseRule() throws SyntaxErrorException {
        Expression head = parseExpression();

        // no yields -> single-line rule
        if (tm.MatchAndRemove(Token.TokenTypes.YIELDS).isEmpty()) {
            requireNewLine();
            Rule r = new Rule();
            r.expression = head;
            r.thens = new LinkedList<>();
            return r;
        }

        // yields -> indented block of then-expressions
        requireNewLine();
        require(Token.TokenTypes.INDENT);

        LinkedList<Expression> thens = new LinkedList<>();
        while (true) {
            skipBlankLines();
            if (tm.Peek(0).isPresent() && tm.Peek(0).get().Type == Token.TokenTypes.DEDENT) break;

            Expression e = parseExpression();
            thens.add(e);
            requireNewLine();
        }

        require(Token.TokenTypes.DEDENT);

        Rule rr = new Rule();
        rr.expression = head;
        rr.thens = thens;
        return rr;
    }

    private Expression parseExpression() throws SyntaxErrorException {
        VariableReference left = parseVariableReference();

        Optional<Token> opTok = tm.MatchAndRemove(Token.TokenTypes.EQUAL);
        if (opTok.isEmpty()) opTok = tm.MatchAndRemove(Token.TokenTypes.NOTEQUAL);
        if (opTok.isEmpty()) {
            throw new SyntaxErrorException("Expected operator",
                    tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }

        VariableReference right = parseVariableReference();

        Op op = new Op();
        op.type = (opTok.get().Type == Token.TokenTypes.EQUAL)
                ? Op.OpTypes.Equal
                : Op.OpTypes.NotEqual;

        Expression e = new Expression();
        e.left = left;
        e.op = op;
        e.right = right;
        return e;
    }

    private VariableReference parseVariableReference() throws SyntaxErrorException {
        Token name = require(Token.TokenTypes.IDENTIFIER);
        VariableReference vr = new VariableReference();
        vr.variableName = name.Value.orElseThrow();
        vr.vrmodifier = parseVRModifierChain();
        return vr;
    }

    private Optional<VRModifier> parseVRModifierChain() throws SyntaxErrorException {
        if (!tm.Peek(0).isPresent()) return Optional.empty();
        Token t = tm.Peek(0).get();
        if (t.Type != Token.TokenTypes.LEFTBRACE && t.Type != Token.TokenTypes.DOT) return Optional.empty();

        VRModifier m = new VRModifier();
        if (tm.MatchAndRemove(Token.TokenTypes.LEFTBRACE).isPresent()) {
            Token num = require(Token.TokenTypes.NUMBER);
            m.dot = false;
            m.size = num.Value.orElseThrow();
            m.part = Optional.empty();
            require(Token.TokenTypes.RIGHTBRACE);
        } else if (tm.MatchAndRemove(Token.TokenTypes.DOT).isPresent()) {
            Token partTok = require(Token.TokenTypes.IDENTIFIER);
            m.dot = true;
            m.part = Optional.of(partTok.Value.orElseThrow());
            m.size = null;
        } else {
            return Optional.empty();
        }
        m.vrmodifier = parseVRModifierChain();
        return Optional.of(m);
    }

    // require a token of a given type or throw SyntaxErrorException
    private Token require(Token.TokenTypes required) throws SyntaxErrorException {
        Optional<Token> tok = tm.MatchAndRemove(required);
        if (tok.isEmpty()) {
            throw new SyntaxErrorException(
                    "Expected " + required,
                    tm.getCurrentLine(),
                    tm.getCurrentColumnNumber()
            );
        }
        return tok.get();
    }

    private void requireNewLine() throws SyntaxErrorException {
        if (!tm.MatchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {
            throw new SyntaxErrorException(
                    "Expected NEWLINE",
                    tm.getCurrentLine(),
                    tm.getCurrentColumnNumber()
            );
        }
        // swallow extra NEWLINE tokens
        while (tm.MatchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {}
    }
}
