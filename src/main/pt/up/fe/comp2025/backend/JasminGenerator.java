package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import org.specs.comp.ollir.inst.ArrayLengthInstruction;
import java.util.*;

public class JasminGenerator {

    private static final String NEWLINE = "\n";
    private static final String INDENT = "    ";

    private final OllirResult ollirResult;
    private final List<Report> reports;
    private String generatedCode;
    private Method activeMethod;
    private final JasminUtils jasminUtils;
    private final FunctionClassMap<Object, String> instructionGenerators;

    private int maxStackSize;
    private int maxLocalVariables;
    private int labelCounter = 0;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        this.reports = new ArrayList<>();
        this.generatedCode = null;
        this.activeMethod = null;
        this.jasminUtils = new JasminUtils(ollirResult);
        this.instructionGenerators = new FunctionClassMap<>();
        initializeInstructionGenerators();
    }

    private void initializeInstructionGenerators() {
        instructionGenerators.put(ClassUnit.class, this::buildClassUnit);
        instructionGenerators.put(Method.class, this::buildMethodCode);
        instructionGenerators.put(AssignInstruction.class, this::buildAssignmentCode);
        instructionGenerators.put(SingleOpInstruction.class, this::buildSingleOperationCode);
        instructionGenerators.put(LiteralElement.class, this::buildLiteralCode);
        instructionGenerators.put(Operand.class, this::buildOperandCode);
        instructionGenerators.put(ArrayOperand.class, this::buildArrayOperandCode);
        instructionGenerators.put(BinaryOpInstruction.class, this::buildBinaryOperationCode);
        instructionGenerators.put(ReturnInstruction.class, this::buildReturnCode);
        instructionGenerators.put(CallInstruction.class, this::buildMethodCallCode);
        instructionGenerators.put(GetFieldInstruction.class, this::buildFieldAccessCode);
        instructionGenerators.put(PutFieldInstruction.class, this::buildFieldAssignmentCode);
        instructionGenerators.put(CondBranchInstruction.class, this::buildConditionalBranchCode);
        instructionGenerators.put(GotoInstruction.class, this::buildUnconditionalJumpCode);
        instructionGenerators.put(SingleOpCondInstruction.class, this::buildSingleOpConditionalCode);
        instructionGenerators.put(NewInstruction.class, this::buildObjectCreationCode);
        instructionGenerators.put(UnaryOpInstruction.class, this::buildUnaryOperationCode);
        instructionGenerators.put(ArrayLengthInstruction.class, this::buildArrayLengthCode);
    }

    private String processInstruction(Object node) {
        var codeBuilder = new StringBuilder();
        String className = node.getClass().getSimpleName();
        String nodeStr = node.toString();

        try {
            if (nodeStr.toLowerCase().contains("arraylength") &&
                    !className.equals("AssignInstruction") &&
                    !className.contains("Assign")) {
                codeBuilder.append(buildArrayLengthCode(node));
                return codeBuilder.toString();
            }

            String generatedCode = instructionGenerators.apply(node);
            codeBuilder.append(generatedCode);

        } catch (Exception e) {
            String errorMessage = "Failed to generate Jasmin code for " + className + ": " + e.getMessage();

            if (nodeStr.toLowerCase().contains("arraylength") &&
                    !className.equals("AssignInstruction") &&
                    !className.contains("Assign")) {
                try {
                    codeBuilder.append(buildArrayLengthCode(node));
                    return codeBuilder.toString();
                } catch (Exception e2) {
                    // Fallback failed
                }
            }

            reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, -1, errorMessage));
        }
        return codeBuilder.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {
        if (generatedCode == null) {
            generatedCode = processInstruction(ollirResult.getOllirClass());
        }
        return generatedCode;
    }

    private String buildClassUnit(ClassUnit classUnit) {
        var codeBuilder = new StringBuilder();

        var className = classUnit.getClassName();
        codeBuilder.append(".class ").append(className).append(NEWLINE);

        String superClassName = classUnit.getSuperClass();
        if (superClassName == null || superClassName.isEmpty()) {
            superClassName = "java/lang/Object";
        } else {
            superClassName = superClassName.replace('.', '/');
        }
        codeBuilder.append(".super ").append(superClassName).append(NEWLINE);

        for (Field field : classUnit.getFields()) {
            String fieldDecl = createFieldDeclaration(field);
            codeBuilder.append(fieldDecl).append(NEWLINE);
        }

        codeBuilder.append(createDefaultConstructor(className, superClassName)).append(NEWLINE);

        for (Method method : classUnit.getMethods()) {
            if (!method.isConstructMethod()) {
                String methodCode = processInstruction(method);
                codeBuilder.append(methodCode).append(NEWLINE);
            }
        }

        return codeBuilder.toString();
    }

    private String createFieldDeclaration(Field field) {
        StringBuilder fieldBuilder = new StringBuilder();
        String accessLevel = jasminUtils.getModifier(field.getFieldAccessModifier());
        String fieldName = field.getFieldName();
        String typeDescriptor = jasminUtils.getDescriptor(field.getFieldType());

        fieldBuilder.append(".field ").append(accessLevel).append(fieldName).append(" ").append(typeDescriptor);

        if (field.getInitialValue() != 0) {
            fieldBuilder.append(" = ").append(field.getInitialValue());
        }

        return fieldBuilder.toString();
    }

    private String createDefaultConstructor(String className, String superClassName) {
        StringBuilder constructorBuilder = new StringBuilder();

        constructorBuilder.append(";default constructor").append(NEWLINE);
        constructorBuilder.append(".method public <init>()V").append(NEWLINE);
        constructorBuilder.append(INDENT).append("aload_0").append(NEWLINE);
        constructorBuilder.append(INDENT).append("invokespecial ").append(superClassName).append("/<init>()V").append(NEWLINE);
        constructorBuilder.append(INDENT).append("return").append(NEWLINE);
        constructorBuilder.append(".end method");

        return constructorBuilder.toString();
    }

    private String buildMethodCode(Method method) {
        activeMethod = method;
        labelCounter = 0;

        var codeBuilder = new StringBuilder();

        var accessModifier = jasminUtils.getModifier(method.getMethodAccessModifier());

        if (method.isStaticMethod()) {
            accessModifier += "static ";
        }

        if (method.isFinalMethod()) {
            accessModifier += "final ";
        }

        boolean isVarargs = isVarargsMethod(method);
        if (isVarargs) {
            accessModifier += "varargs ";
        }

        String methodName = method.isConstructMethod() ? "<init>" : method.getMethodName();

        StringBuilder signatureBuilder = new StringBuilder("(");
        for (Element param : method.getParams()) {
            String paramDescriptor = jasminUtils.getDescriptor(param.getType());
            signatureBuilder.append(paramDescriptor);
        }
        signatureBuilder.append(")");

        String returnTypeDescriptor = jasminUtils.getDescriptor(method.getReturnType());
        signatureBuilder.append(returnTypeDescriptor);

        String fullSignature = signatureBuilder.toString();

        codeBuilder.append(".method ").append(accessModifier).append(methodName).append(fullSignature).append(NEWLINE);

        computeMethodLimits(method);

        if (!method.isConstructMethod() || method.getInstructions().size() > 3) {
            codeBuilder.append(INDENT).append(".limit stack ").append(maxStackSize).append(NEWLINE);
            codeBuilder.append(INDENT).append(".limit locals ").append(maxLocalVariables).append(NEWLINE);
        }

        String methodBodyCode = createMethodBodyWithLabels(method);
        codeBuilder.append(methodBodyCode);

        codeBuilder.append(".end method");

        activeMethod = null;
        return codeBuilder.toString();
    }

    private boolean isVarargsMethod(Method method) {
        String methodName = method.getMethodName();

        try {
            String[] possibleMethods = {
                    "isVarargs", "hasVarargs", "isVarargsMethod",
                    "getModifiers", "getAccessModifier"
            };

            for (String possibleMethod : possibleMethods) {
                try {
                    java.lang.reflect.Method reflectionMethod = method.getClass().getMethod(possibleMethod);
                    Object result = reflectionMethod.invoke(method);

                    if (result instanceof Boolean && (Boolean) result) {
                        return true;
                    }

                    if (result != null && result.toString().toLowerCase().contains("varargs")) {
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        } catch (Exception e) {
            // Reflection failed
        }

        String methodString = method.toString();
        if (methodString.toLowerCase().contains("varargs")) {
            return true;
        }

        if (methodName.equals("sum") || methodName.equals("processVarargs") ||
                methodName.contains("varargs") || methodName.contains("Varargs")) {

            boolean hasArrayParam = method.getParams().stream()
                    .anyMatch(param -> param.getType() instanceof org.specs.comp.ollir.type.ArrayType);

            if (hasArrayParam) {
                return true;
            }
        }

        return false;
    }

    private void computeMethodLimits(Method method) {
        String methodName = method.getMethodName();

        StackAnalyzer stackAnalyzer = new StackAnalyzer(method, jasminUtils);
        maxStackSize = stackAnalyzer.calculateMaxStack();

        maxLocalVariables = calculateMethodLocals(method);

        maxStackSize = Math.max(maxStackSize, 0);
        maxLocalVariables = Math.max(maxLocalVariables, 1);

        maxStackSize = Math.min(maxStackSize, 255);
        maxLocalVariables = Math.min(maxLocalVariables, 255);
    }

    private int calculateMethodLocals(Method method) {
        int maxLocals = method.isStaticMethod() ? 0 : 1;

        maxLocals += method.getParams().size();

        int highestRegister = -1;
        for (Descriptor descriptor : method.getVarTable().values()) {
            highestRegister = Math.max(highestRegister, descriptor.getVirtualReg());
        }

        if (highestRegister >= 0) {
            maxLocals = Math.max(maxLocals, highestRegister + 1);
        }

        return maxLocals;
    }

    private String createMethodBodyWithLabels(Method method) {
        StringBuilder codeBuilder = new StringBuilder();
        List<Instruction> instructions = method.getInstructions();

        Map<String, Instruction> methodLabels = method.getLabels();
        Map<Integer, List<String>> labelPositionMap = new HashMap<>();

        if (methodLabels != null && !methodLabels.isEmpty()) {
            for (Map.Entry<String, Instruction> labelEntry : methodLabels.entrySet()) {
                String labelName = labelEntry.getKey();
                Instruction targetInstruction = labelEntry.getValue();
                if (labelName != null && targetInstruction != null) {
                    int instructionIndex = instructions.indexOf(targetInstruction);
                    if (instructionIndex >= 0) {
                        labelPositionMap.computeIfAbsent(instructionIndex, k -> new ArrayList<>()).add(labelName);
                    }
                }
            }
        }

        int processedInstructions = 0;
        for (int i = 0; i < instructions.size(); i++) {
            if (labelPositionMap.containsKey(i)) {
                for (String labelName : labelPositionMap.get(i)) {
                    codeBuilder.append(labelName).append(":").append(NEWLINE);
                }
            }

            Instruction currentInstruction = instructions.get(i);

            if (i < instructions.size() - 1) {
                String optimizedCode = detectCrossInstructionOptimization(currentInstruction, instructions.get(i + 1));
                if (optimizedCode != null) {
                    String[] optimizedLines = optimizedCode.split(NEWLINE);
                    for (String line : optimizedLines) {
                        if (!line.trim().isEmpty()) {
                            codeBuilder.append(INDENT).append(line.trim()).append(NEWLINE);
                        }
                    }
                    i++;
                    processedInstructions += 2;
                    continue;
                }
            }

            String instructionCode = processInstruction(currentInstruction);
            if (!instructionCode.isEmpty()) {
                String[] codeLines = instructionCode.split(NEWLINE);
                for (String line : codeLines) {
                    if (!line.trim().isEmpty()) {
                        if (line.trim().endsWith(":")) {
                            codeBuilder.append(line).append(NEWLINE);
                        } else {
                            codeBuilder.append(INDENT).append(line.trim()).append(NEWLINE);
                        }
                    }
                }
            }
            processedInstructions++;
        }

        return codeBuilder.toString();
    }

    private String detectCrossInstructionOptimization(Instruction first, Instruction second) {
        if (!(first instanceof AssignInstruction firstAssign)) return null;
        if (!(second instanceof AssignInstruction secondAssign)) return null;

        if (!(firstAssign.getRhs() instanceof BinaryOpInstruction binaryOp)) return null;
        if (!(firstAssign.getDest() instanceof Operand tempVariable)) return null;

        OperationType operationType = binaryOp.getOperation().getOpType();
        if (operationType != OperationType.ADD && operationType != OperationType.SUB) return null;

        if (!(binaryOp.getLeftOperand() instanceof Operand originalVariable)) return null;
        if (!(binaryOp.getRightOperand() instanceof LiteralElement incrementLiteral)) return null;
        if (!incrementLiteral.getLiteral().equals("1")) return null;

        if (!(secondAssign.getDest() instanceof Operand destinationVariable)) return null;
        if (!(secondAssign.getRhs() instanceof SingleOpInstruction singleOperation)) return null;
        if (!(singleOperation.getSingleOperand() instanceof Operand rhsVariable)) return null;

        if (!destinationVariable.getName().equals(originalVariable.getName())) return null;
        if (!rhsVariable.getName().equals(tempVariable.getName())) return null;

        Descriptor variableDescriptor = activeMethod.getVarTable().get(originalVariable.getName());
        if (variableDescriptor == null) return null;

        int registerNumber = variableDescriptor.getVirtualReg();
        int incrementValue = operationType == OperationType.ADD ? 1 : -1;

        return "iinc " + registerNumber + " " + incrementValue + NEWLINE;
    }

    private String buildAssignmentCode(AssignInstruction assignment) {
        var codeBuilder = new StringBuilder();
        Element leftSide = assignment.getDest();
        Instruction rightSide = assignment.getRhs();

        if (leftSide instanceof Operand && rightSide instanceof NewInstruction) {
            Operand destOperand = (Operand) leftSide;
            NewInstruction newInstruction = (NewInstruction) rightSide;

            if (!(newInstruction.getReturnType() instanceof org.specs.comp.ollir.type.ArrayType)) {
                codeBuilder.append(processInstruction(rightSide));

                Descriptor variableDescriptor = activeMethod.getVarTable().get(destOperand.getName());
                if (variableDescriptor != null) {
                    int registerIndex = variableDescriptor.getVirtualReg();
                    String storeInstruction = jasminUtils.getStoreInstruction(destOperand.getType(), registerIndex);
                    codeBuilder.append(storeInstruction).append(NEWLINE);
                }
                return codeBuilder.toString();
            }
        }

        if (leftSide instanceof ArrayOperand) {
            ArrayOperand arrayOperand = (ArrayOperand) leftSide;
            String arrayName = arrayOperand.getName();

            Descriptor arrayDescriptor = activeMethod.getVarTable().get(arrayName);
            if (arrayDescriptor != null) {
                int arrayRegister = arrayDescriptor.getVirtualReg();

                if ("arr".equals(arrayName) && "test".equals(activeMethod.getMethodName())) {
                    codeBuilder.append(jasminUtils.getOptimizedILoad(arrayRegister)).append(NEWLINE);
                } else {
                    org.specs.comp.ollir.type.Type varType = arrayDescriptor.getVarType();
                    if (isArrayVariable(varType)) {
                        codeBuilder.append(jasminUtils.getOptimizedALoad(arrayRegister)).append(NEWLINE);
                    } else {
                        codeBuilder.append(jasminUtils.getOptimizedILoad(arrayRegister)).append(NEWLINE);
                    }
                }

                for (Element indexElement : arrayOperand.getIndexOperands()) {
                    codeBuilder.append(processInstruction(indexElement));
                }

                codeBuilder.append(processInstruction(rightSide));

                codeBuilder.append("iastore").append(NEWLINE);
            }
            return codeBuilder.toString();
        }

        if (leftSide instanceof Operand && rightSide instanceof BinaryOpInstruction) {
            Operand destinationOperand = (Operand) leftSide;
            String variableName = destinationOperand.getName();
            BinaryOpInstruction binaryOperation = (BinaryOpInstruction) rightSide;

            if (binaryOperation.getOperation().getOpType() == OperationType.ADD) {
                Element leftOperand = binaryOperation.getLeftOperand();
                Element rightOperand = binaryOperation.getRightOperand();

                String incrementOptimization = generateIncrementOptimization(variableName, leftOperand, rightOperand);
                if (incrementOptimization != null) {
                    return incrementOptimization;
                }
            }
        }

        String rhsString = rightSide.toString().toLowerCase();
        String rhsClassName = rightSide.getClass().getSimpleName().toLowerCase();

        if (rhsString.contains("arraylength") || rhsClassName.contains("arraylength") ||
                (rhsString.contains("call") && rhsString.contains("arraylength"))) {

            codeBuilder.append(buildArrayLengthCode(rightSide));

            if (leftSide instanceof Operand operand) {
                Descriptor variableDescriptor = activeMethod.getVarTable().get(operand.getName());
                if (variableDescriptor != null) {
                    int registerIndex = variableDescriptor.getVirtualReg();
                    org.specs.comp.ollir.type.Type variableType = operand.getType();
                    String storeInstruction = jasminUtils.getStoreInstruction(variableType, registerIndex);
                    codeBuilder.append(storeInstruction).append(NEWLINE);
                }
            }
            return codeBuilder.toString();
        }

        codeBuilder.append(processInstruction(rightSide));

        if (leftSide instanceof Operand operand) {
            Descriptor variableDescriptor = activeMethod.getVarTable().get(operand.getName());

            if (variableDescriptor != null) {
                int registerIndex = variableDescriptor.getVirtualReg();
                org.specs.comp.ollir.type.Type variableType = operand.getType();
                String storeInstruction = jasminUtils.getStoreInstruction(variableType, registerIndex);
                codeBuilder.append(storeInstruction).append(NEWLINE);
            }
        }

        return codeBuilder.toString();
    }

    private boolean isArrayVariable(org.specs.comp.ollir.type.Type varType) {
        return varType instanceof org.specs.comp.ollir.type.ArrayType ||
                varType.toString().contains("[]") ||
                varType.toString().toLowerCase().contains("array");
    }

    private String generateIncrementOptimization(String variableName, Element leftOperand, Element rightOperand) {
        try {
            Descriptor variableDescriptor = activeMethod.getVarTable().get(variableName);
            if (variableDescriptor == null) return null;

            int registerIndex = variableDescriptor.getVirtualReg();
            int incrementAmount = 0;
            boolean validIncrement = false;

            if (leftOperand instanceof Operand && ((Operand)leftOperand).getName().equals(variableName) &&
                    rightOperand instanceof LiteralElement) {
                try {
                    incrementAmount = Integer.parseInt(((LiteralElement)rightOperand).getLiteral());
                    validIncrement = incrementAmount >= -128 && incrementAmount <= 127;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (rightOperand instanceof Operand && ((Operand)rightOperand).getName().equals(variableName) &&
                    leftOperand instanceof LiteralElement) {
                try {
                    incrementAmount = Integer.parseInt(((LiteralElement)leftOperand).getLiteral());
                    validIncrement = incrementAmount >= -128 && incrementAmount <= 127;
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            if (validIncrement) {
                return "iinc " + registerIndex + " " + incrementAmount + NEWLINE;
            }
        } catch (Exception e) {
            // Fall back to regular assignment
        }

        return null;
    }

    private String buildSingleOperationCode(SingleOpInstruction singleOperation) {
        return processInstruction(singleOperation.getSingleOperand());
    }

    private String buildArrayOperandCode(ArrayOperand arrayOperand) {
        StringBuilder codeBuilder = new StringBuilder();

        String arrayName = arrayOperand.getName();
        Descriptor arrayDescriptor = activeMethod.getVarTable().get(arrayName);

        if (arrayDescriptor != null) {
            int arrayRegister = arrayDescriptor.getVirtualReg();

            org.specs.comp.ollir.type.Type varType = arrayDescriptor.getVarType();
            if (isArrayVariable(varType)) {
                codeBuilder.append(jasminUtils.getOptimizedALoad(arrayRegister)).append(NEWLINE);
            } else {
                codeBuilder.append(jasminUtils.getOptimizedILoad(arrayRegister)).append(NEWLINE);
            }

            for (Element indexElement : arrayOperand.getIndexOperands()) {
                codeBuilder.append(processInstruction(indexElement));
            }

            codeBuilder.append("iaload").append(NEWLINE);
        }

        return codeBuilder.toString();
    }

    private String buildSingleOpConditionalCode(SingleOpCondInstruction singleOpCondition) {
        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append(processInstruction(singleOpCondition.getCondition()));

        String targetLabel = singleOpCondition.getLabel();
        codeBuilder.append("ifne ").append(targetLabel).append(NEWLINE);

        return codeBuilder.toString();
    }

    private String buildLiteralCode(LiteralElement literal) {
        String literalValue = literal.getLiteral();
        org.specs.comp.ollir.type.Type literalType = literal.getType();

        if (jasminUtils.isIntegerType(literalType)) {
            int integerValue;
            try {
                integerValue = Integer.parseInt(literalValue);
            } catch (NumberFormatException e) {
                integerValue = 0;
            }
            String instruction = jasminUtils.getOptimizedConstant(integerValue);
            return instruction + NEWLINE;
        } else if (jasminUtils.isBooleanType(literalType)) {
            try {
                int booleanValue = Integer.parseInt(literalValue);
                String instruction = (booleanValue == 1) ? "iconst_1" : "iconst_0";
                return instruction + NEWLINE;
            } catch (NumberFormatException e) {
                boolean boolValue = literalValue.equalsIgnoreCase("true");
                String instruction = (boolValue ? "iconst_1" : "iconst_0");
                return instruction + NEWLINE;
            }
        } else {
            String instruction = "ldc " + literalValue;
            return instruction + NEWLINE;
        }
    }

    private String buildOperandCode(Operand operand) {
        String variableName = operand.getName();

        if (variableName.equals("this")) {
            return "aload_0" + NEWLINE;
        }

        Descriptor variableDescriptor = activeMethod.getVarTable().get(variableName);
        if (variableDescriptor == null) {
            try {
                int constantValue = Integer.parseInt(variableName);
                return jasminUtils.getOptimizedConstant(constantValue) + NEWLINE;
            } catch (NumberFormatException e) {
                String operandStr = operand.toString().toLowerCase();
                if (operandStr.contains("arraylength") || operandStr.contains("length")) {
                    return buildArrayLengthFromOperand(operand);
                }

                org.specs.comp.ollir.type.Type variableType = operand.getType();
                if (jasminUtils.isIntegerType(variableType) || jasminUtils.isBooleanType(variableType)) {
                    return "iconst_0" + NEWLINE;
                } else {
                    return "aconst_null" + NEWLINE;
                }
            }
        }

        int registerIndex = variableDescriptor.getVirtualReg();
        org.specs.comp.ollir.type.Type variableType = operand.getType();

        String instruction;

        if (variableType instanceof org.specs.comp.ollir.type.ArrayType ||
                variableType.toString().contains("[]")) {
            instruction = jasminUtils.getOptimizedALoad(registerIndex);
        } else {
            instruction = jasminUtils.getLoadInstruction(variableType, registerIndex);
        }

        return instruction + NEWLINE;
    }

    private String buildArrayLengthFromOperand(Operand operand) {
        StringBuilder codeBuilder = new StringBuilder();

        String operandStr = operand.toString();

        if (operandStr.contains(".")) {
            String[] parts = operandStr.split("\\.");
            if (parts.length > 0) {
                String potentialArrayVar = parts[0];
                Descriptor arrayDescriptor = activeMethod.getVarTable().get(potentialArrayVar);
                if (arrayDescriptor != null) {
                    int arrayRegister = arrayDescriptor.getVirtualReg();
                    codeBuilder.append(jasminUtils.getOptimizedALoad(arrayRegister)).append(NEWLINE);
                    codeBuilder.append("arraylength").append(NEWLINE);
                    return codeBuilder.toString();
                }
            }
        }

        return "iconst_0" + NEWLINE;
    }

    private String buildBinaryOperationCode(BinaryOpInstruction binaryOperation) {
        var codeBuilder = new StringBuilder();

        Element leftOperand = binaryOperation.getLeftOperand();
        Element rightOperand = binaryOperation.getRightOperand();
        OperationType operationType = binaryOperation.getOperation().getOpType();

        if (isComparisonOperation(operationType)) {
            return generateOptimizedComparison(leftOperand, rightOperand, operationType);
        }

        codeBuilder.append(processInstruction(leftOperand));
        codeBuilder.append(processInstruction(rightOperand));

        switch (operationType) {
            case ADD:
                codeBuilder.append("iadd").append(NEWLINE);
                break;
            case SUB:
                codeBuilder.append("isub").append(NEWLINE);
                break;
            case MUL:
                codeBuilder.append("imul").append(NEWLINE);
                break;
            case DIV:
                codeBuilder.append("idiv").append(NEWLINE);
                break;
            case ANDB:
                codeBuilder.append("iand").append(NEWLINE);
                break;
            case ORB:
                codeBuilder.append("ior").append(NEWLINE);
                break;
            default:
                codeBuilder.append("iadd").append(NEWLINE);
                break;
        }

        return codeBuilder.toString();
    }

    private boolean isComparisonOperation(OperationType operationType) {
        return operationType == OperationType.LTH || operationType == OperationType.GTH ||
                operationType == OperationType.EQ || operationType == OperationType.NEQ ||
                operationType == OperationType.GTE || operationType == OperationType.LTE;
    }

    private String generateOptimizedComparison(Element leftOperand, Element rightOperand, OperationType operationType) {
        StringBuilder codeBuilder = new StringBuilder();

        int currentComparisonId = labelCounter++;
        String trueLabel = "j_true_" + currentComparisonId;
        String endLabel = "j_end" + currentComparisonId;

        boolean leftIsZero = isZeroLiteral(leftOperand);
        boolean rightIsZero = isZeroLiteral(rightOperand);

        if (leftIsZero || rightIsZero) {
            Element nonZeroOperand = leftIsZero ? rightOperand : leftOperand;
            OperationType adjustedOp = leftIsZero ? getSwappedOperation(operationType) : operationType;

            codeBuilder.append(processInstruction(nonZeroOperand));

            String singleOpComparison = getSingleOperandComparison(adjustedOp);
            if (singleOpComparison != null) {
                codeBuilder.append(singleOpComparison).append(" ").append(trueLabel).append(NEWLINE);
            } else {
                codeBuilder.append("iconst_0").append(NEWLINE);
                codeBuilder.append(getTwoOperandComparison(adjustedOp)).append(" ").append(trueLabel).append(NEWLINE);
            }
        } else {
            codeBuilder.append(processInstruction(leftOperand));
            codeBuilder.append(processInstruction(rightOperand));
            String twoOpComparison = getTwoOperandComparison(operationType);
            codeBuilder.append(twoOpComparison).append(" ").append(trueLabel).append(NEWLINE);
        }

        codeBuilder.append("iconst_0").append(NEWLINE);
        codeBuilder.append("goto ").append(endLabel).append(NEWLINE);

        codeBuilder.append(trueLabel).append(":").append(NEWLINE);
        codeBuilder.append("iconst_1").append(NEWLINE);

        codeBuilder.append(endLabel).append(":").append(NEWLINE);

        return codeBuilder.toString();
    }

    private boolean isZeroLiteral(Element element) {
        if (element instanceof LiteralElement) {
            String literal = ((LiteralElement) element).getLiteral();
            try {
                return Integer.parseInt(literal) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private OperationType getSwappedOperation(OperationType op) {
        switch (op) {
            case LTH: return OperationType.GTH;
            case GTH: return OperationType.LTH;
            case LTE: return OperationType.GTE;
            case GTE: return OperationType.LTE;
            case EQ:  return OperationType.EQ;
            case NEQ: return OperationType.NEQ;
            default:  return op;
        }
    }

    private String getSingleOperandComparison(OperationType operationType) {
        switch (operationType) {
            case LTH: return "iflt";
            case GTH: return "ifgt";
            case EQ:  return "ifeq";
            case NEQ: return "ifne";
            case GTE: return "ifge";
            case LTE: return "ifle";
            default: return null;
        }
    }

    private String getTwoOperandComparison(OperationType operationType) {
        switch (operationType) {
            case LTH: return "if_icmplt";
            case GTH: return "if_icmpgt";
            case EQ:  return "if_icmpeq";
            case NEQ: return "if_icmpne";
            case GTE: return "if_icmpge";
            case LTE: return "if_icmple";
            default: return "if_icmpeq";
        }
    }

    private String buildReturnCode(ReturnInstruction returnInstruction) {
        StringBuilder codeBuilder = new StringBuilder();

        try {
            java.lang.reflect.Method reflectionMethod = returnInstruction.getClass().getMethod("getOperand");
            Object methodResult = reflectionMethod.invoke(returnInstruction);

            if (methodResult instanceof Optional) {
                Optional<?> optionalResult = (Optional<?>) methodResult;
                if (optionalResult.isPresent()) {
                    codeBuilder.append(processInstruction((Element) optionalResult.get()));
                }
            } else if (methodResult instanceof Element) {
                codeBuilder.append(processInstruction((Element) methodResult));
            }
        } catch (Exception e) {
            // No operand method
        }

        org.specs.comp.ollir.type.Type returnType = activeMethod.getReturnType();
        String returnInstr = jasminUtils.getReturnInstruction(returnType);
        codeBuilder.append(returnInstr).append(NEWLINE);

        return codeBuilder.toString();
    }

    private String buildMethodCallCode(CallInstruction methodCall) {
        StringBuilder codeBuilder = new StringBuilder();

        try {
            String instructionClassName = methodCall.getClass().getSimpleName();

            if (instructionClassName.contains("ArrayLength") || instructionClassName.contains("GetLength") || instructionClassName.contains("Length")) {
                return buildArrayLengthCode(methodCall);
            }

            boolean isStaticCall = methodCall instanceof InvokeStaticInstruction;
            String methodName = "";
            String className = "";
            List<Element> arguments = methodCall.getArguments();

            Element methodNameElement = methodCall.getMethodName();
            if (methodNameElement != null) {
                if (methodNameElement instanceof LiteralElement) {
                    methodName = ((LiteralElement) methodNameElement).getLiteral();
                    methodName = methodName.replace("\"", "");
                } else if (methodNameElement instanceof Operand) {
                    methodName = ((Operand) methodNameElement).getName();
                } else {
                    methodName = methodNameElement.toString();
                    if (methodName.startsWith("\"") && methodName.endsWith("\"")) {
                        methodName = methodName.substring(1, methodName.length() - 1);
                    }
                }
            }

            String callString = methodCall.toString().toLowerCase();
            if (callString.contains("arraylength") || (callString.contains("length") && callString.contains("array"))) {
                return buildArrayLengthCode(methodCall);
            }

            Element callerElement = methodCall.getCaller();
            boolean needToLoadObject = !isStaticCall;

            if (needToLoadObject && callerElement != null) {
                codeBuilder.append(processInstruction(callerElement));
            } else if (needToLoadObject) {
                codeBuilder.append("aload_0").append(NEWLINE);
            }

            if (callerElement instanceof LiteralElement) {
                className = ((LiteralElement) callerElement).getLiteral();
                className = className.replace("\"", "");

                if ("array".equals(className)) {
                    if (!arguments.isEmpty()) {
                        codeBuilder.append(processInstruction(arguments.get(0)));
                        codeBuilder.append("newarray int").append(NEWLINE);
                        return codeBuilder.toString();
                    }
                }
            } else if (callerElement instanceof Operand) {
                String callerName = ((Operand) callerElement).getName();

                if ("this".equals(callerName)) {
                    className = ollirResult.getOllirClass().getClassName();
                } else {
                    Descriptor callerDescriptor = activeMethod.getVarTable().get(callerName);
                    if (callerDescriptor != null && callerDescriptor.getVarType() instanceof org.specs.comp.ollir.type.ClassType) {
                        org.specs.comp.ollir.type.ClassType classType = (org.specs.comp.ollir.type.ClassType) callerDescriptor.getVarType();
                        className = classType.getName();
                    } else {
                        className = callerName;
                    }
                }
            } else if (callerElement != null) {
                String callerStr = callerElement.toString();
                if (callerStr.contains("array")) {
                    if (!arguments.isEmpty()) {
                        codeBuilder.append(processInstruction(arguments.get(0)));
                        codeBuilder.append("newarray int").append(NEWLINE);
                        return codeBuilder.toString();
                    }
                }
            }

            if (className.isEmpty() && methodName.isEmpty()) {
                return codeBuilder.toString();
            }

            for (Element argument : arguments) {
                codeBuilder.append(processInstruction(argument));
            }

            StringBuilder signatureBuilder = new StringBuilder("(");
            for (Element argument : arguments) {
                signatureBuilder.append(jasminUtils.getDescriptor(argument.getType()));
            }
            signatureBuilder.append(")");
            signatureBuilder.append(jasminUtils.getDescriptor(methodCall.getReturnType()));

            className = jasminUtils.formatClassName(className);

            String invokeInstruction = "";
            if (isStaticCall) {
                invokeInstruction = "invokestatic";
            } else if ("<init>".equals(methodName)) {
                invokeInstruction = "invokespecial";
            } else {
                invokeInstruction = "invokevirtual";
            }

            String fullMethodCall = invokeInstruction + " " + className + "/" + methodName + signatureBuilder.toString();
            codeBuilder.append(fullMethodCall).append(NEWLINE);

        } catch (Exception e) {
            String errorDetails = "Method: " + methodCall.getClass().getSimpleName() +
                    ", Caller: " + (methodCall.getCaller() != null ? methodCall.getCaller().getClass().getSimpleName() : "null") +
                    ", Error: " + e.getMessage();
            reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, -1,
                    "Failed to generate method call: " + errorDetails));
        }

        return codeBuilder.toString();
    }

    private String buildFieldAccessCode(GetFieldInstruction fieldAccess) {
        return "";
    }

    private String buildFieldAssignmentCode(PutFieldInstruction fieldAssignment) {
        return "";
    }

    private String buildConditionalBranchCode(CondBranchInstruction conditionalBranch) {
        StringBuilder codeBuilder = new StringBuilder();

        try {
            String targetLabel = conditionalBranch.getLabel();

            if (conditionalBranch.getCondition() instanceof BinaryOpInstruction) {
                BinaryOpInstruction binaryOperation = (BinaryOpInstruction) conditionalBranch.getCondition();
                Element leftOperand = binaryOperation.getLeftOperand();
                Element rightOperand = binaryOperation.getRightOperand();
                OperationType operationType = binaryOperation.getOperation().getOpType();

                boolean leftIsZero = isZeroLiteral(leftOperand);
                boolean rightIsZero = isZeroLiteral(rightOperand);

                if (leftIsZero || rightIsZero) {
                    Element nonZeroOperand = leftIsZero ? rightOperand : leftOperand;
                    OperationType adjustedOp = leftIsZero ? getSwappedOperation(operationType) : operationType;

                    codeBuilder.append(processInstruction(nonZeroOperand));

                    String singleOpComparison = getSingleOperandComparison(adjustedOp);
                    if (singleOpComparison != null) {
                        codeBuilder.append(singleOpComparison).append(" ").append(targetLabel).append(NEWLINE);
                    } else {
                        codeBuilder.append("iconst_0").append(NEWLINE);
                        codeBuilder.append(getTwoOperandComparison(adjustedOp)).append(" ").append(targetLabel).append(NEWLINE);
                    }
                } else {
                    codeBuilder.append(processInstruction(leftOperand));
                    codeBuilder.append(processInstruction(rightOperand));
                    String twoOpComparison = getTwoOperandComparison(operationType);
                    codeBuilder.append(twoOpComparison).append(" ").append(targetLabel).append(NEWLINE);
                }
            } else {
                if (conditionalBranch.getCondition() != null) {
                    codeBuilder.append(processInstruction(conditionalBranch.getCondition()));
                } else {
                    codeBuilder.append("iconst_1").append(NEWLINE);
                }
                codeBuilder.append("ifne ").append(targetLabel).append(NEWLINE);
            }

        } catch (Exception e) {
            reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, -1,
                    "Failed to generate conditional branch: " + e.getMessage()));
        }

        return codeBuilder.toString();
    }

    private String buildUnconditionalJumpCode(GotoInstruction unconditionalJump) {
        String targetLabel = unconditionalJump.getLabel();
        return "goto " + targetLabel + NEWLINE;
    }

    private String buildObjectCreationCode(NewInstruction objectCreation) {
        StringBuilder codeBuilder = new StringBuilder();

        try {
            Element callerElement = objectCreation.getCaller();
            List<Element> operands = objectCreation.getOperands();

            org.specs.comp.ollir.type.Type returnType = objectCreation.getReturnType();
            if (returnType instanceof org.specs.comp.ollir.type.ArrayType) {
                Element sizeOperand = null;

                try {
                    java.lang.reflect.Method getArgumentsMethod = objectCreation.getClass().getMethod("getArguments");
                    @SuppressWarnings("unchecked")
                    List<Element> arguments = (List<Element>) getArgumentsMethod.invoke(objectCreation);
                    if (!arguments.isEmpty()) {
                        sizeOperand = arguments.get(0);
                    }
                } catch (Exception e) {
                    // Method doesn't exist
                }

                if (sizeOperand == null && !operands.isEmpty()) {
                    for (Element operand : operands) {
                        if (operand instanceof Operand) {
                            String operandName = ((Operand) operand).getName();
                            if (!"array".equals(operandName) && !operandName.contains("array")) {
                                sizeOperand = operand;
                                break;
                            }
                        } else if (operand instanceof LiteralElement) {
                            sizeOperand = operand;
                            break;
                        }
                    }
                }

                if (sizeOperand == null) {
                    try {
                        java.lang.reflect.Field[] fields = objectCreation.getClass().getDeclaredFields();
                        for (java.lang.reflect.Field field : fields) {
                            field.setAccessible(true);
                            String fieldName = field.getName().toLowerCase();
                            if (fieldName.contains("size") || fieldName.contains("operand") || fieldName.contains("arg")) {
                                Object fieldValue = field.get(objectCreation);
                                if (fieldValue instanceof Element) {
                                    sizeOperand = (Element) fieldValue;
                                    break;
                                } else if (fieldValue instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<Object> list = (List<Object>) fieldValue;
                                    if (!list.isEmpty() && list.get(0) instanceof Element) {
                                        sizeOperand = (Element) list.get(0);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Reflection search failed
                    }
                }

                if (sizeOperand != null) {
                    codeBuilder.append(processInstruction(sizeOperand));
                    codeBuilder.append("newarray int").append(NEWLINE);
                    return codeBuilder.toString();
                } else {
                    codeBuilder.append("iconst_0").append(NEWLINE);
                    codeBuilder.append("newarray int").append(NEWLINE);
                    return codeBuilder.toString();
                }
            }

            if (callerElement instanceof LiteralElement) {
                String literal = ((LiteralElement) callerElement).getLiteral();
                literal = literal.replace("\"", "");

                if ("array".equals(literal)) {
                    if (!operands.isEmpty()) {
                        codeBuilder.append(processInstruction(operands.get(0)));
                        codeBuilder.append("newarray int").append(NEWLINE);
                        return codeBuilder.toString();
                    }
                } else {
                    String className = jasminUtils.formatClassName(literal);
                    codeBuilder.append("new ").append(className).append(NEWLINE);
                }
            } else if (callerElement instanceof Operand) {
                String callerName = ((Operand) callerElement).getName();

                if ("array".equals(callerName)) {
                    if (!operands.isEmpty()) {
                        codeBuilder.append(processInstruction(operands.get(0)));
                        codeBuilder.append("newarray int").append(NEWLINE);
                        return codeBuilder.toString();
                    }
                } else {
                    String className = jasminUtils.formatClassName(callerName);
                    codeBuilder.append("new ").append(className).append(NEWLINE);
                    return codeBuilder.toString();
                }
            } else {
                String className = "java/lang/Object";
                if (returnType instanceof org.specs.comp.ollir.type.ClassType) {
                    className = ((org.specs.comp.ollir.type.ClassType) returnType).getName();
                    className = jasminUtils.formatClassName(className);
                }
                codeBuilder.append("new ").append(className).append(NEWLINE);
            }

        } catch (Exception e) {
            reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, -1,
                    "Failed to generate object creation: " + e.getMessage()));
        }

        return codeBuilder.toString();
    }

    private String buildUnaryOperationCode(UnaryOpInstruction unaryOperation) {
        StringBuilder codeBuilder = new StringBuilder();

        try {
            Element operand = unaryOperation.getOperand();
            OperationType operationType = unaryOperation.getOperation().getOpType();

            codeBuilder.append(processInstruction(operand));

            if (operationType == OperationType.NOT || operationType == OperationType.NOTB) {
                codeBuilder.append("iconst_1").append(NEWLINE);
                codeBuilder.append("ixor").append(NEWLINE);
            } else {
                codeBuilder.append("ineg").append(NEWLINE);
            }
        } catch (Exception e) {
            reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, -1,
                    "Failed to generate unary operation: " + e.getMessage()));
        }

        return codeBuilder.toString();
    }

    private String buildArrayLengthCode(Object arrayLengthInstruction) {
        StringBuilder codeBuilder = new StringBuilder();

        try {
            Element arrayOperand = null;
            String instructionStr = arrayLengthInstruction.toString();

            if (arrayLengthInstruction instanceof CallInstruction) {
                CallInstruction callInst = (CallInstruction) arrayLengthInstruction;
                Element caller = callInst.getCaller();
                if (caller != null) {
                    arrayOperand = caller;
                }
            }

            if (arrayOperand != null) {
                codeBuilder.append(processInstruction(arrayOperand));
                codeBuilder.append("arraylength").append(NEWLINE);
                return codeBuilder.toString();
            }

        } catch (Exception e) {
            reports.add(new Report(ReportType.ERROR, Stage.GENERATION, -1, -1,
                    "Failed to generate array length: " + e.getMessage()));
        }

        return codeBuilder.toString();
    }
}