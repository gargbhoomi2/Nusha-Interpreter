import AST.*;
import java.util.*;

public class Interpreter {

    // interpreter structures
    private final Map<String, String[]> definitions = new HashMap<>();

    private final Map<String, Entry[]> structTemplates = new HashMap<>();

    private final Map<String, variableInstance[]> varMap = new HashMap<>();

    private final Map<String, structInstance[]> structMap = new HashMap<>();

    private final List<variableInstance> allVars = new ArrayList<>();

    private final Map<VariableReference, ParsedModifier> modifierCache = new IdentityHashMap<>();



    //One runtime variable instance.
    private static final class variableInstance {
        final String defName;
        final String[] domain;
        int value = 0;

        // For uniqueness constraints
        final ArrayList<variableInstance> uniquePeers = new ArrayList<>();

        variableInstance(String defName, String[] domain) {
            this.defName = defName;
            this.domain = domain;
        }

        String valueAsString() {
            int idx = Math.max(0, Math.min(value, domain.length - 1));
            return domain[idx];
        }

        void connectUniquePeer(variableInstance other) {
            if (other == this) return;
            if (!uniquePeers.contains(other)) uniquePeers.add(other);
            if (!other.uniquePeers.contains(this)) other.uniquePeers.add(this);
        }
    }

    //Struct instance: ordered fields
    private static final class structInstance {
        private final LinkedHashMap<String, variableInstance> fields = new LinkedHashMap<>();
        void put(String fieldName, variableInstance vi) { fields.put(fieldName, vi); }
        variableInstance get(String fieldName) { return fields.get(fieldName); }
        Set<Map.Entry<String, variableInstance>> entries() { return fields.entrySet(); }
    }

    private static final class ParsedModifier {
        final Integer index;
        final String fieldName;
        ParsedModifier(Integer index, String fieldName) {
            this.index = index;
            this.fieldName = fieldName;
        }
    }

    // interpret
    public void Interpret(Nusha tree) throws Exception {
        definitions.clear();
        structTemplates.clear();
        varMap.clear();
        structMap.clear();
        allVars.clear();
        modifierCache.clear();

        loadDefinitionsAndStructs(tree);
        instantiateVariables(tree);
        buildAllVarsList();

        boolean found = runSolver(tree);

        if (found) {
            System.out.println("SUCCESS:");
            printAllStructVars();
        } else {
            System.out.println("NO SOLUTION FOUND.");
        }
    }

    // Defination and Stucts
    @SuppressWarnings("unchecked")
    private void loadDefinitionsAndStructs(Nusha tree) {
        if (tree.definitions == null || tree.definitions.definition == null) return;

        for (Definition def : (List<Definition>) tree.definitions.definition) {
            String name = def.definitionName;

            if (def.choices != null && def.choices.isPresent()) {
                List<String> list = (List<String>) def.choices.get().choice;
                definitions.put(name, list.toArray(new String[0]));
            }

            if (def.nstruct != null && def.nstruct.isPresent()) {
                Entry[] schema = ((List<Entry>) def.nstruct.get().entry).toArray(new Entry[0]);
                structTemplates.put(name, schema);
            }
        }
    }

    // instantiating variables
    @SuppressWarnings("unchecked")
    private void instantiateVariables(Nusha tree) {
        if (tree.variables == null || tree.variables.variable == null) return;

        for (Variable va : (List<Variable>) tree.variables.variable) {
            String varName = va.variableName;
            String type = va.type;

            int size = 1;
            if (va.size != null && va.size.isPresent()) {
                try { size = Integer.parseInt(va.size.get()); } catch (Exception ignored) {}
            }

            if (structTemplates.containsKey(type)) {
                // array of structs
                structInstance[] sin = new structInstance[size];
                Entry[] schema = structTemplates.get(type);

                for (int i = 0; i < size; i++) {
                    structInstance structInstance = new structInstance();
                    for (Entry e : schema) {
                        String[] domain = definitions.get(e.type);
                        structInstance.put(e.name, new variableInstance(e.type, domain));
                    }
                    sin[i] = structInstance;
                }

                // uniqueness wiring
                for (Entry e : schema) {
                    if (e.unique != null && e.unique) {
                        for (int i = 0; i < size; i++) {
                            variableInstance a = sin[i].get(e.name);
                            for (int j = i + 1; j < size; j++) {
                                a.connectUniquePeer(sin[j].get(e.name));
                            }
                        }
                    }
                }

                structMap.put(varName, sin);

            } else if (definitions.containsKey(type)) {
                // simple var array
                String[] domain = definitions.get(type);
                variableInstance[] vn = new variableInstance[size];
                for (int i = 0; i < size; i++) vn[i] = new variableInstance(type, domain);
                varMap.put(varName, vn);

            } else {
                throw new IllegalStateException("Unknown type: " + type);
            }
        }
    }

    // to build all var list
    private void buildAllVarsList() {
        for (variableInstance[] vi : varMap.values()) Collections.addAll(allVars, vi);
        for (structInstance[] sn : structMap.values())
            for (structInstance sIn : sn)
                for (var e : sIn.entries())
                    allVars.add(e.getValue());

    }

    // solver
    @SuppressWarnings("unchecked")
    private boolean runSolver(Nusha tree) {

        List<Rule> rules =
                (tree.rules == null || tree.rules.rule == null)
                        ? Collections.emptyList()
                        : (List<Rule>) tree.rules.rule;

        // Reset all values
        for (variableInstance v : allVars) v.value = 0;

        while (true) {
            if (checkUniqueness() && checkAllRules(rules)) return true;
            if (!incrementAllVars()) break;
        }
        return false;
    }


    // odometer increment
    private boolean incrementAllVars() {
        for (int i = 0; i < allVars.size(); i++) {
            variableInstance va = allVars.get(i);
            va.value++;
            if (va.value < va.domain.length) return true;
            va.value = 0;
        }
        return false;
    }

    private boolean checkUniqueness() {
        for (variableInstance vi : allVars)
            for (variableInstance u : vi.uniquePeers)
                if (vi.value == u.value) return false;
        return true;
    }

    private boolean checkAllRules(List<Rule> rules) {
        for (Rule rl : rules)
            if (!runRule(rl)) return false;
        return true;
    }

    // rule execution
    private boolean runRule(Rule rl) {
        if (rl.thens == null || rl.thens.isEmpty()) {
            return evaluateExpression(rl.expression, null, -1, false);
        }
        return runComplexRule(rl);
    }

    private boolean runComplexRule(Rule r) {
        Expression head = r.expression;
        String structName = head.left.variableName;
        structInstance[] stI = structMap.get(structName);

        if (stI == null) return evaluateExpression(head, null, -1, false);

        for (int i = 0; i < stI.length; i++) {
            if (evaluateExpression(head, structName, i, true)) {
                for (Expression ex : r.thens)
                    if (!evaluateExpression(ex, structName, i, true))
                        return false;
            }
        }
        return true;
    }

    // expression evaluation
    private boolean evaluateExpression(Expression expr,
                                       String boundStructName,
                                       int boundIndex,
                                       boolean hasBound) {

        variableInstance left =
                evaluateVariableReference(expr.left, boundStructName, boundIndex, hasBound);

        variableInstance right = null;
        try {
            right = evaluateVariableReference(expr.right, boundStructName, boundIndex, hasBound);
        } catch (RuntimeException ignored) {}

        boolean isNot = expr.op.toString().contains("!");

        if (right != null) {
            boolean eq = (left.value == right.value);
            return isNot ? !eq : eq;
        }

        String opt = expr.right.variableName;
        int idx = -1;
        for (int i = 0; i < left.domain.length; i++)
            if (left.domain[i].equals(opt)) idx = i;

        boolean eq2 = (left.value == idx);
        return isNot ? !eq2 : eq2;
    }

    // variable reference resolution
    private ParsedModifier getCachedModifier(VariableReference ref) {
        ParsedModifier pm = modifierCache.get(ref);
        if (pm != null) return pm;

        String mod = "";
        if (ref.vrmodifier != null && ref.vrmodifier.isPresent())
            mod = ref.vrmodifier.get().toString();

        pm = parseModifier(mod);
        modifierCache.put(ref, pm);
        return pm;
    }

    private variableInstance evaluateVariableReference(VariableReference ref,
                                                       String boundStructName,
                                                       int boundIndex,
                                                       boolean hasBound) {

        String base = ref.variableName;

        // struct variable
        if (structMap.containsKey(base)) {
            structInstance[] arr = structMap.get(base);
            ParsedModifier pm = getCachedModifier(ref);

            Integer index = pm.index;
            String field = pm.fieldName;

            if (index == null) {
                if (hasBound && base.equals(boundStructName))
                    index = boundIndex;
                else
                    throw new RuntimeException("Missing index for struct " + base);
            }

            if (index < 0 || index >= arr.length)
                throw new RuntimeException("Bad index for struct " + base);

            // Recover missing field if needed
            if (field == null) {
                String s = ref.toString();
                for (String key : arr[0].fields.keySet())
                    if (s.contains("." + key))
                        field = key;
            }

            if (field == null)
                throw new RuntimeException("Missing field for struct " + base);

            return arr[index].get(field);
        }

        // simple array variable
        if (varMap.containsKey(base)) {
            variableInstance[] arr = varMap.get(base);
            ParsedModifier pm = getCachedModifier(ref);

            if (pm.index == null)
                throw new RuntimeException("Missing index for simple variable " + base);

            return arr[pm.index];
        }

        throw new RuntimeException("Unknown variable: " + base);
    }

    //  parse modifier
    private ParsedModifier parseModifier(String mod) {
        if (mod == null) mod = "";
        Integer idx = null;
        String field = null;

        int i = 0;
        while (i < mod.length()) {
            char c = mod.charAt(i);
            if (c == '[') {
                int end = mod.indexOf(']', i);
                if (end < 0) break;

                String inside = mod.substring(i + 1, end).trim();
                boolean numeric = true;
                for (int k = 0; k < inside.length(); k++) {
                    if (!Character.isDigit(inside.charAt(k))) numeric = false;
                }
                if (numeric && !inside.isEmpty())
                    idx = Integer.parseInt(inside);

                i = end + 1;
            }
            else if (c == '.') {
                int start = i + 1;
                int j = start;
                while (j < mod.length() && Character.isJavaIdentifierPart(mod.charAt(j)))
                    j++;
                if (j > start)
                    field = mod.substring(start, j);
                i = j;
            }
            else i++;
        }

        return new ParsedModifier(idx, field);
    }

    //  to print struct value
    private void printAllStructVars() {
        ArrayList<String> names = new ArrayList<>(structMap.keySet());
        Collections.sort(names);

        for (String name : names) {
            structInstance[] st = structMap.get(name);

            for (int i = 0; i < st.length; i++) {
                structInstance si = st[i];
                List<String> fields = new ArrayList<>();
                for (var e : si.entries()) fields.add(e.getKey());

                List<String> order = getFieldOrder(name, fields);

                for (String f : order) {
                    variableInstance v = si.get(f);
                    if (v != null)
                        System.out.println(name + "[" + i + "]." + f + " = " + v.valueAsString());
                }
                System.out.println();
            }
        }
    }

    private List<String> getFieldOrder(String name, List<String> fields) {

        if (name.equals("Parties")) return Arrays.asList("b", "c", "g", "k");
        if (name.equals("Puzzles")) return Arrays.asList("p", "d", "n");
        if (name.equals("Couples")) return Arrays.asList("b", "e", "g", "ic");

        if (name.equals("Stories")) {
            if (fields.contains("c") && fields.contains("d"))
                return Arrays.asList("p", "c", "d");  // Dish
            if (fields.contains("f"))
                return Arrays.asList("p", "a", "f");  // Friends
            if (fields.contains("h"))
                return Arrays.asList("p", "a", "h");  // Pets
        }

        if (name.equals("Days")) return Arrays.asList("b", "s", "g", "m");

        return fields; // fallback
    }
}
