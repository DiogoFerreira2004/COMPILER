package pt.up.fe.comp2025.optimization.register.analysis;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;

import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * ========================================================================
 *  LIVENESS ANALYZER  +  REGISTER‑CLASS INFERENCE
 * ------------------------------------------------------------------------
 *  Passo 1:  calcula  DEF/USE  e itera  in/out  até fix‑point.
 *  Passo 2:  constroi live‑ranges   (var → {insts onde está viva}).
 *  Passo 3:  COPY‑COALESCING  — une variáveis que:
 *              (a) aparecem numa instrução de cópia simples  x := y
 *              (b) nunca estão vivas simultaneamente (|range(x) ∩ range(y)| = 0)
 *            usando uma estrutura Union–Find.
 *
 *  Resultado:  • liveRanges (Map<String,Set<Integer>>)
 *              • registerClasses (Map<String,String>)  —> root de cada classe
 * ========================================================================
 */
public final class LivenessAnalyzer {

    /* ------------------------------------------------------------------ *
     * 0. LOGGING CONFIGURATION                                            *
     * ------------------------------------------------------------------ */
    public enum LogLevel { TRACE, INFO, OFF }

    private static final LogLevel LOG_LEVEL =
            switch (System.getenv().getOrDefault("LIVENESS_LOG", "").toUpperCase()) {
                case "TRACE" -> LogLevel.TRACE;
                case "INFO"  -> LogLevel.INFO;
                default      -> LogLevel.OFF;
            };

    private static void log(LogLevel lvl, String fmt, Object... args) {
        if (lvl.ordinal() >= LOG_LEVEL.ordinal())
            System.out.printf("[LIVENESS:%s] %s%n", lvl, String.format(fmt, args));
    }

    /* ------------------------------------------------------------------ *
     * 1.  INTERNAL STRUCTURES                                             *
     * ------------------------------------------------------------------ */
    private static final class NodeInfo {
        final Set<String> def = new HashSet<>();
        final Set<String> use = new HashSet<>();
        final Set<String> in  = new HashSet<>();
        final Set<String> out = new HashSet<>();
    }

    /* ---------- Union–Find para classes de registo -------------------- */
    private static final class RegisterClassInfo {
        private final Map<String, String> parent = new HashMap<>();
        private final Map<String, Integer> rank  = new HashMap<>();

        /** inicializa cada var como root de si mesma */
        RegisterClassInfo(Collection<String> vars) { vars.forEach(v -> { parent.put(v,v); rank.put(v,0); }); }

        private String find(String v) {
            String p = parent.get(v);
            if (!p.equals(v)) parent.put(v, p = find(p));
            return p;
        }
        void union(String a, String b) {
            String ra = find(a), rb = find(b);
            if (ra.equals(rb)) return;
            int rkA = rank.get(ra), rkB = rank.get(rb);
            if (rkA < rkB)       parent.put(ra, rb);
            else if (rkA > rkB)  parent.put(rb, ra);
            else { parent.put(rb, ra); rank.put(ra, rkA+1); }
        }
        /** devolve o representante (root) da classe de {@code v}. */
        String cls(String v){ return find(v); }

        Map<String, Set<String>> asClasses() {
            Map<String,Set<String>> map = new HashMap<>();
            for (String v : parent.keySet())
                map.computeIfAbsent(find(v), k->new HashSet<>()).add(v);
            return map;
        }
    }

    /* ------------------------------------------------------------------ *
     * 2.  PUBLIC STATE (resultados)                                       *
     * ------------------------------------------------------------------ */
    private Map<String, Set<Integer>> liveRanges;         // var → pontos vivos
    private RegisterClassInfo registerClasses;            // alias‑sets

    /** devolve mapping var → representative (root) depois de analyse(). */
    public Map<String,String> getRegisterClasses() {
        Map<String,String> m = new HashMap<>();
        registerClasses.parent.keySet()
                .forEach(v -> m.put(v, registerClasses.cls(v)));
        return m;
    }

    /** mapping completo varRoot → {members} (útil para debug/UI). */
    public Map<String,Set<String>> getClassSets(){ return registerClasses.asClasses(); }

    /* ------------------------------------------------------------------ *
     * 3.  ANALYSIS PIPELINE                                               *
     * ------------------------------------------------------------------ */
    public Map<String, Set<Integer>> analyze(org.specs.comp.ollir.Method method) {

        log(LogLevel.INFO, "Analyse %s", method.getMethodName());
        Instant t0 = Instant.now();

        /* 3.1  LIVENESS BASE ******************************************** */
        List<Instruction> instrs = method.getInstructions();
        int N = instrs.size();
        NodeInfo[] info = new NodeInfo[N];
        for (int i = 0; i < N; i++) info[i] = new NodeInfo();

        Set<String> vars = new HashSet<>(method.getVarTable().keySet());
        vars.remove("this"); vars.remove("return");
        method.getParams().stream()
                .filter(Operand.class::isInstance)
                .map(p -> ((Operand)p).getName())
                .forEach(vars::add);

        buildDefUse(instrs, info, vars);
        int iters = iterateInOut(method, info);
        liveRanges = toLiveRanges(info, vars);

        /* 3.2  COPY‑COALESCING ******************************************* */
        registerClasses = new RegisterClassInfo(vars);

        for (int idx = 0; idx < N; idx++) {
            Instruction inst = instrs.get(idx);
            if (inst.getInstType() != InstructionType.ASSIGN) continue;

            AssignInstruction a = (AssignInstruction) inst;
            String dest = ((Operand)a.getDest()).getName();

            Instruction rhs = a.getRhs();
            // copia simples  x := y     (SingleOp sobre Operand)
            if (rhs instanceof SingleOpInstruction ssi &&
                    ssi.getSingleOperand() instanceof Operand op) {

                String src = ((Operand)op).getName();
                if (!vars.contains(src) || dest.equals(src)) continue;

                // overlap check
                if (Collections.disjoint(liveRanges.get(dest), liveRanges.get(src))) {
                    log(LogLevel.TRACE, "Coalesce copy #%d   %s ← %s", idx,dest,src);
                    registerClasses.union(dest, src);
                } else {
                    log(LogLevel.TRACE, "Cannot coalesce %s/%s : live‑ranges overlap", dest, src);
                }
            }
        }

        /* 3.3  PARÂMETROS NÃO USADOS ************************************ */
        for (Element p : method.getParams()) {
            if (!(p instanceof Operand op)) continue;
            String v = op.getName();
            if (liveRanges.getOrDefault(v, Set.of()).isEmpty()) {
                // garante classe própria — já está por defeito
                log(LogLevel.TRACE, "Param %s never used: isolated register class", v);
            }
        }

        // Garantir que todas as variáveis na varTable tenham um intervalo de vida
        for (String varName : method.getVarTable().keySet()) {
            if (!liveRanges.containsKey(varName)) {
                liveRanges.put(varName, new HashSet<>());
            }
        }

        log(LogLevel.INFO, "Finished in %d ms, %d iterations, %d classes",
                Duration.between(t0, Instant.now()).toMillis(),
                iters,
                registerClasses.asClasses().size());

        if (LOG_LEVEL == LogLevel.TRACE)
            dumpDot(method, instrs, info);

        return liveRanges;
    }

    /* ------------------------------------------------------------------ *
     * 4. BUILD DEF / USE                                                  *
     * ------------------------------------------------------------------ */
    private void buildDefUse(List<Instruction> instrs, NodeInfo[] info, Set<String> vars) {
        for (int idx = 0; idx < instrs.size(); idx++) {
            Instruction inst = instrs.get(idx);
            NodeInfo ni      = info[idx];

            Consumer<Element> addUse = e -> {
                if (e instanceof Operand op && vars.contains(op.getName()))
                    ni.use.add(op.getName());
            };
            Consumer<Element> addDef = e -> {
                if (e instanceof Operand op && vars.contains(op.getName()))
                    ni.def.add(op.getName());
            };

            switch (inst.getInstType()) {
                case ASSIGN -> {
                    AssignInstruction a = (AssignInstruction) inst;
                    addDef.accept(a.getDest());
                    extractUses(a.getRhs(), addUse);
                }
                case BINARYOPER -> {
                    BinaryOpInstruction b = (BinaryOpInstruction) inst;
                    addUse.accept(b.getLeftOperand());
                    addUse.accept(b.getRightOperand());
                }
                case UNARYOPER -> reflectUnaryOperand((UnaryOpInstruction) inst)
                        .ifPresent(addUse);
                case CALL -> fetchCallOperands((CallInstruction) inst)
                        .forEach(addUse);
                case PUTFIELD, GETFIELD ->
                        reflectFieldOperands(inst).forEach(addUse);
                case BRANCH -> reflectCondCondition((CondBranchInstruction) inst)
                        .ifPresent(cond -> extractUses(cond, addUse));
                case RETURN -> {
                    ReturnInstruction r = (ReturnInstruction) inst;
                    if (r.hasReturnValue()) r.getOperand().ifPresent(addUse);
                }
                default -> {/* GOTO / NOPER */}
            }
        }
    }

    /* ------------------------------------------------------------------ *
     * 5. DATA‑FLOW ITERATION                                              *
     * ------------------------------------------------------------------ */
    private int iterateInOut(org.specs.comp.ollir.Method m, NodeInfo[] info) {
        List<Instruction> instrs = m.getInstructions();
        int N = instrs.size(), iter = 0; boolean changed;
        do {
            changed = false; iter++;
            for (int i = N-1; i>=0; i--) {
                NodeInfo ni = info[i];
                Set<String> newOut = new HashSet<>();
                for (int s : successors(m,i)) newOut.addAll(info[s].in);
                Set<String> newIn = new HashSet<>(ni.use);
                newOut.stream().filter(v->!ni.def.contains(v)).forEach(newIn::add);
                if (!ni.out.equals(newOut)) { ni.out.clear(); ni.out.addAll(newOut); changed=true; }
                if (!ni.in .equals(newIn )) { ni.in .clear(); ni.in .addAll(newIn ); changed=true; }
            }
        } while (changed);
        return iter;
    }

    /* ------------------------------------------------------------------ *
     * 6. LIVE‑RANGE                                                   *
     * ------------------------------------------------------------------ */
    private Map<String, Set<Integer>> toLiveRanges(NodeInfo[] info, Set<String> vars){
        Map<String, Set<Integer>> map=new HashMap<>();
        vars.forEach(v->map.put(v,new HashSet<>()));
        for (int i=0;i<info.length;i++){
            int id=i;
            info[i].in .forEach(v->map.get(v).add(id));
            info[i].out.forEach(v->map.get(v).add(id));
        }
        return map;
    }

    /* ------------------------------------------------------------------ *
     * 7. DOT DUMP (opcional)                                              *
     * ------------------------------------------------------------------ */
    private void dumpDot(org.specs.comp.ollir.Method m, List<Instruction> instrs, NodeInfo[] info){
        try(BufferedWriter bw = Files.newBufferedWriter(Path.of(m.getMethodName()+"_liveness.dot"));
            PrintWriter w = new PrintWriter(bw)){
            w.write("digraph CFG {\n  node [shape=box,fontname=\"monospace\",fontsize=10];\n");
            for(int i=0;i<instrs.size();i++){
                NodeInfo ni=info[i];
                String lab=("%d: %s\\ldef=%s\\luse=%s\\lin=%s\\lout=%s"
                        .formatted(i,instrs.get(i),ni.def,ni.use,ni.in,ni.out))
                        .replace("\"","\\\"");
                w.write("  n%d [label=\"%s\"];\n".formatted(i,lab));
            }
            for(int i=0;i<instrs.size();i++)
                for(int s:successors(m,i)) w.write("  n%d -> n%d;\n".formatted(i,s));
            w.write("}\n");
            log(LogLevel.INFO,"DOT written");
        }catch(Exception e){log(LogLevel.INFO,"DOT fail: %s",e.getMessage());}
    }

    /* ------------------------------------------------------------------ *
     * 8. Helpers: USE extraction, reflection, successors                  *
     * ------------------------------------------------------------------ */
    private void extractUses(Instruction inst, Consumer<Element> addUse){
        if(inst==null)return;
        switch(inst.getInstType()){
            case BINARYOPER->{
                BinaryOpInstruction b=(BinaryOpInstruction)inst;
                addUse.accept(b.getLeftOperand()); addUse.accept(b.getRightOperand());
            }
            case UNARYOPER->reflectUnaryOperand((UnaryOpInstruction)inst).ifPresent(addUse);
            case CALL->fetchCallOperands((CallInstruction)inst).forEach(addUse);
            case ASSIGN->extractUses(((AssignInstruction)inst).getRhs(),addUse);
            case PUTFIELD,GETFIELD->reflectFieldOperands(inst).forEach(addUse);
            default->{}
        }
    }

    private Optional<Element> reflectUnaryOperand(UnaryOpInstruction u){
        for(String m:List.of("getRightOperand","getOperand"))
            try{ java.lang.reflect.Method mm=u.getClass().getMethod(m);
                return Optional.ofNullable((Element) mm.invoke(u)); }
            catch(Exception ignored){}
        return Optional.empty();
    }

    private List<Element> reflectFieldOperands(Object f) {
        List<Element> operands = new ArrayList<>();
        try {
            if (f instanceof GetFieldInstruction g) {
                // Usar reflection para obter o operando
                try {
                    java.lang.reflect.Method m = g.getClass().getMethod("getObjectOperand");
                    Element operand = (Element) m.invoke(g);
                    if (operand != null) operands.add(operand);
                } catch (Exception e) {
                    // Tentar métodos alternativos
                    for (String methodName : List.of("getOperand", "getSelfOperand", "getFieldOperand")) {
                        try {
                            java.lang.reflect.Method m = g.getClass().getMethod(methodName);
                            Element operand = (Element) m.invoke(g);
                            if (operand != null) {
                                operands.add(operand);
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                return operands;
            }

            if (f instanceof PutFieldInstruction p) {
                // Tentar obter os operandos via reflection
                try {
                    java.lang.reflect.Method objM = p.getClass().getMethod("getObjectOperand");
                    Element objOperand = (Element) objM.invoke(p);
                    if (objOperand != null) operands.add(objOperand);

                    java.lang.reflect.Method valM = p.getClass().getMethod("getValueOperand");
                    Element valOperand = (Element) valM.invoke(p);
                    if (valOperand != null) operands.add(valOperand);
                } catch (Exception e) {
                    // Tentar métodos alternativos
                    for (int i = 1; i <= 2; i++) {
                        try {
                            java.lang.reflect.Method m = p.getClass().getMethod("getOperand" + i);
                            Element operand = (Element) m.invoke(p);
                            if (operand != null) operands.add(operand);
                        } catch (Exception ignored) {}
                    }
                }
                return operands;
            }
        } catch (Exception ignored) {}
        return operands;
    }

    private Optional<Instruction> reflectCondCondition(CondBranchInstruction cb) {
        try {
            return Optional.ofNullable(cb.getCondition());
        } catch (Exception ignored) {
            // Tentar usar reflection para obter a condição
            try {
                java.lang.reflect.Method m = cb.getClass().getMethod("getConditionInstruction");
                return Optional.ofNullable((Instruction) m.invoke(cb));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    private List<Element> fetchCallOperands(CallInstruction c) {
        List<Element> operands = new ArrayList<>();
        try {
            // Tentar diferentes métodos para obter o primeiro argumento
            for (String methodName : List.of("getInvokeObject", "getFirstArg", "getCallerObject", "getFirstOperand")) {
                try {
                    java.lang.reflect.Method m = c.getClass().getMethod(methodName);
                    Element first = (Element) m.invoke(c);
                    if (first != null) {
                        operands.add(first);
                        break;
                    }
                } catch (Exception ignored) {}
            }

            // Obter os argumentos através do método getArguments() ou similar
            for (String methodName : List.of("getArguments", "getParams", "getCallArgs")) {
                try {
                    java.lang.reflect.Method argsMethod = c.getClass().getMethod(methodName);
                    Object args = argsMethod.invoke(c);
                    if (args instanceof List) {
                        for (Object arg : (List<?>) args) {
                            if (arg instanceof Element) {
                                operands.add((Element) arg);
                            }
                        }
                        return operands;
                    }
                } catch (Exception ignored) {}
            }

            // Se não conseguiu com nenhum método específico, tentar um método genérico
            try {
                java.lang.reflect.Method countMethod = c.getClass().getMethod("getNumOperands");
                int count = (int) countMethod.invoke(c);
                for (int i = 0; i < count; i++) {
                    try {
                        java.lang.reflect.Method getMethod = c.getClass().getMethod("getOperand", int.class);
                        Element arg = (Element) getMethod.invoke(c, i);
                        if (arg != null) operands.add(arg);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return operands;
    }

    private List<Integer> successors(org.specs.comp.ollir.Method m, int idx) {
        List<Integer> succ = new ArrayList<>();
        Instruction inst = m.getInstructions().get(idx);

        // Adicionar o próximo na sequência, a menos que seja um return ou goto incondicional
        InstructionType type = inst.getInstType();
        if (type != InstructionType.RETURN && !(type == InstructionType.GOTO && !(inst instanceof CondBranchInstruction))) {
            if (idx + 1 < m.getInstructions().size())
                succ.add(idx + 1);
        }

        // Adicionar destinos de branch
        if (type == InstructionType.GOTO) {
            GotoInstruction g = (GotoInstruction) inst;
            int target = findLabel(m, g.getLabel());
            if (target >= 0) succ.add(target);
        } else if (type == InstructionType.BRANCH) {
            CondBranchInstruction cb = (CondBranchInstruction) inst;
            int target = findLabel(m, cb.getLabel());
            if (target >= 0) succ.add(target);
        }

        return succ;
    }

    private int findLabel(org.specs.comp.ollir.Method m, String l) {
        List<Instruction> instrs = m.getInstructions();
        for (int i = 0; i < instrs.size(); i++) {
            Instruction inst = instrs.get(i);
            // Verificar se o tipo da instrução representa um label
            try {
                // Como InstructionType.LABEL não está disponível, vamos tentar verificar pelo nome ou classe
                if (inst.getClass().getSimpleName().contains("Label") ||
                        inst.getInstType().toString().equals("LABEL")) {

                    // Tentar obter o label usando reflection
                    for (String methodName : List.of("getLabel", "getLabelName", "getName")) {
                        try {
                            java.lang.reflect.Method labelMethod = inst.getClass().getMethod(methodName);
                            String label = (String) labelMethod.invoke(inst);
                            if (l.equals(label)) return i;
                        } catch (Exception ignored) {}
                    }

                    // Fallback: tentar encontrar qualquer método que retorne String sem parâmetros
                    for (java.lang.reflect.Method method : inst.getClass().getMethods()) {
                        if (method.getReturnType() == String.class &&
                                method.getParameterCount() == 0 &&
                                !method.getName().equals("toString")) {
                            try {
                                String label = (String) method.invoke(inst);
                                if (l.equals(label)) return i;
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }
}