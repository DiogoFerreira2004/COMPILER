package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.type.*;

import java.util.*;

public class StackAnalyzer {
    private final Method method;
    private final JasminUtils jasminUtils;

    public StackAnalyzer(Method method, JasminUtils jasminUtils) {
        this.method = method;
        this.jasminUtils = jasminUtils;
    }

    public int calculateMaxStack() {
        String methodName = method.getMethodName();

        int maxStack = calculateMaxStackFromInstructions();
        int minimumRequired = calculateMinimumStackForMethod();
        maxStack = Math.max(maxStack, minimumRequired);

        if ("main".equals(methodName)) {
            if (method.getInstructions().size() == 1 &&
                    method.getInstructions().get(0) instanceof ReturnInstruction) {
                return 0;
            }
        }

        return Math.min(maxStack, 255);
    }

    private int calculateMaxStackFromInstructions() {
        int maxStack = 1;

        for (Instruction instruction : method.getInstructions()) {
            int instructionStack = calculateInstructionStack(instruction);
            maxStack = Math.max(maxStack, instructionStack);
        }

        return maxStack;
    }

    private int calculateMinimumStackForMethod() {
        int minimum = 1;

        int maxCallArgs = 0;
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof CallInstruction) {
                CallInstruction call = (CallInstruction) inst;
                int args = call.getArguments().size();
                if (!(call instanceof InvokeStaticInstruction)) {
                    args++;
                }
                maxCallArgs = Math.max(maxCallArgs, args);
            } else if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                if (assign.getRhs() instanceof CallInstruction) {
                    CallInstruction call = (CallInstruction) assign.getRhs();
                    int args = call.getArguments().size();
                    if (!(call instanceof InvokeStaticInstruction)) {
                        args++;
                    }
                    maxCallArgs = Math.max(maxCallArgs, args);
                }
            }
        }

        minimum = Math.max(minimum, maxCallArgs);

        if (hasComparisonOperations()) {
            minimum = Math.max(minimum, 3);
        }

        if (hasArrayOperations()) {
            minimum = Math.max(minimum, 3);
        }

        return minimum;
    }

    private boolean hasComparisonOperations() {
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof CondBranchInstruction) {
                return true;
            }
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                if (assign.getRhs() instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) assign.getRhs();
                    OperationType opType = binOp.getOperation().getOpType();
                    if (isComparisonOp(opType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasArrayOperations() {
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                if (assign.getDest() instanceof ArrayOperand) {
                    return true;
                }
                String rhsStr = assign.getRhs().toString().toLowerCase();
                if (rhsStr.contains("arraylength") || rhsStr.contains("length")) {
                    return true;
                }
            }
            if (inst instanceof NewInstruction) {
                NewInstruction newInst = (NewInstruction) inst;
                if (newInst.getReturnType() instanceof ArrayType) {
                    return true;
                }
            }
        }
        return false;
    }

    private int calculateInstructionStack(Instruction instruction) {
        if (instruction instanceof AssignInstruction) {
            return calculateAssignStack((AssignInstruction) instruction);
        } else if (instruction instanceof CallInstruction) {
            return calculateCallStack((CallInstruction) instruction);
        } else if (instruction instanceof ReturnInstruction) {
            return calculateReturnStack((ReturnInstruction) instruction);
        } else if (instruction instanceof CondBranchInstruction) {
            return calculateCondBranchStack((CondBranchInstruction) instruction);
        } else if (instruction instanceof SingleOpCondInstruction) {
            return calculateSingleOpCondStack((SingleOpCondInstruction) instruction);
        } else if (instruction instanceof NewInstruction) {
            return calculateNewStack((NewInstruction) instruction);
        } else if (instruction instanceof BinaryOpInstruction) {
            return calculateBinaryOpStack((BinaryOpInstruction) instruction);
        } else if (instruction instanceof UnaryOpInstruction) {
            return calculateUnaryOpStack((UnaryOpInstruction) instruction);
        } else if (instruction instanceof SingleOpInstruction) {
            return 1;
        } else if (instruction instanceof GotoInstruction) {
            return 0;
        } else {
            return 1;
        }
    }

    private int calculateAssignStack(AssignInstruction assign) {
        Element dest = assign.getDest();
        Instruction rhs = assign.getRhs();

        if (dest instanceof ArrayOperand) {
            return 3;
        } else {
            if (rhs instanceof BinaryOpInstruction) {
                return calculateBinaryOpStack((BinaryOpInstruction) rhs);
            } else {
                return calculateInstructionStack(rhs);
            }
        }
    }

    private int calculateCallStack(CallInstruction call) {
        int argCount = call.getArguments().size();

        if (call instanceof InvokeStaticInstruction) {
            return argCount;
        } else {
            return argCount + 1;
        }
    }

    private int calculateReturnStack(ReturnInstruction ret) {
        try {
            java.lang.reflect.Method getOperand = ret.getClass().getMethod("getOperand");
            Object result = getOperand.invoke(ret);
            if (result instanceof Optional) {
                return ((Optional<?>) result).isPresent() ? 1 : 0;
            } else if (result != null) {
                return 1;
            }
        } catch (Exception e) {
            // No operand
        }
        return 0;
    }

    private int calculateCondBranchStack(CondBranchInstruction branch) {
        if (branch.getCondition() instanceof BinaryOpInstruction) {
            return 2;
        } else {
            return 1;
        }
    }

    private int calculateSingleOpCondStack(SingleOpCondInstruction singleOpCond) {
        return calculateInstructionStack(singleOpCond.getCondition());
    }

    private int calculateNewStack(NewInstruction newInst) {
        Type returnType = newInst.getReturnType();

        if (returnType instanceof ArrayType) {
            return 1;
        } else {
            return 1;
        }
    }

    private int calculateBinaryOpStack(BinaryOpInstruction binOp) {
        OperationType opType = binOp.getOperation().getOpType();

        if (isComparisonOp(opType)) {
            return 2;
        } else {
            return 2;
        }
    }

    private int calculateUnaryOpStack(UnaryOpInstruction unaryOp) {
        OperationType opType = unaryOp.getOperation().getOpType();

        if (opType == OperationType.NOT || opType == OperationType.NOTB) {
            return 2;
        } else {
            return 1;
        }
    }

    private boolean isComparisonOp(OperationType opType) {
        return opType == OperationType.LTH || opType == OperationType.GTH ||
                opType == OperationType.EQ || opType == OperationType.NEQ ||
                opType == OperationType.GTE || opType == OperationType.LTE;
    }
}