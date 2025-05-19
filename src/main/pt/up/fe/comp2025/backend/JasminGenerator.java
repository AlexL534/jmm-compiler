package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.Type;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final JasminUtils types;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        types = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(NewInstruction.class,this::generateNewInstruction);
        generators.put(InvokeSpecialInstruction.class, this::generateInvokeSpecialInstruction);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstruction);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstruction);
        generators.put(CondBranchInstruction.class, this::generateCondBranchInstruction);
        generators.put(InvokeStaticInstruction.class, this::generateInvokeStaticInstruction);
        generators.put(InvokeVirtualInstruction.class, this::generateInvokeVirtualInstruction);
        generators.put(ArrayOperand.class, this::generateArrayOperand);
        generators.put(GotoInstruction.class, this::generateGotoInstruction);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOpInstructions);

    }

    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));

        return code.toString();
    }


    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        // TODO: When you support 'extends', this must be updated
        var superClass = ollirResult.getOllirClass().getSuperClass();
        var fullSuperClass = superClass;
        if(fullSuperClass == null) {
            fullSuperClass = "java/lang/Object";
        }

        code.append(".super ").append(fullSuperClass).append(NL).append(NL);

        // Generate field declarations
        for (Field field : ollirResult.getOllirClass().getFields()) {
            String modifier = types.getModifier(field.getFieldAccessModifier());
            String fieldName = field.getFieldName();
            String fieldType = types.ollirToJasminType(field.getFieldType());
            code.append(".field ").append(modifier).append(fieldName).append(" ").append(fieldType).append(NL);
        }
        code.append(NL);

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {
        //System.out.println("STARTING METHOD " + method.getMethodName());
        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = types.getModifier(method.getMethodAccessModifier());

        var methodName = method.getMethodName();

        // TODO: Hardcoded param types and return type, needs to be expanded
        var paramsList = method.getParams();
        StringBuilder params = new StringBuilder();

        if(!methodName.equals("main")) {
            for (int i = 0; i < paramsList.size(); i++) {
                var param = (Operand) paramsList.get(i);
                params.append(types.ollirToJasminType(param.getType()));
            }
        }
        else{
            params.append("[Ljava/lang/String;");
        }

        var returnType = types.ollirToJasminType(method.getReturnType());

        code.append("\n.method ").append(modifier);
        if(methodName.equals("main")) {
            code.append("static ");
        }
        code.append(methodName)
                .append("(" + params + ")" + returnType).append(NL);


        // Calculate and add limits
        int limitStack = calculateLimitStack(method);
        int limitLocals = calculateLimitLocals(method);
        code.append(TAB).append(".limit stack ").append(limitStack).append(NL);
        code.append(TAB).append(".limit locals ").append(limitLocals).append(NL);

        for (var inst : method.getInstructions()) {
            var labels = method.getLabels(inst);
            for(var label: labels){
                code.append(label).append(":").append(NL);
            }
            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;
        //System.out.println("ENDING METHOD " + method.getMethodName());
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();
        var lhs = assign.getDest();
        var rhs = assign.getRhs();

        // Check for special case of i = i + 1 which can be optimized to iinc
        if (lhs instanceof Operand && rhs instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) rhs;
            if (binOp.getOperation().getOpType() == OperationType.ADD) {
                var operands = binOp.getOperands();
                Element leftOperand = operands.get(0);
                Element rightOperand = operands.get(1);
                
                // Check if i = i + 1 pattern
                if (leftOperand instanceof Operand && rightOperand instanceof LiteralElement) {
                    Operand leftOp = (Operand) leftOperand;
                    LiteralElement rightLit = (LiteralElement) rightOperand;
                    
                    if (leftOp.getName().equals(((Operand)lhs).getName())) {
                        try {
                            int increment = Integer.parseInt(rightLit.getLiteral());
                            if (increment >= -128 && increment <= 127) { // iinc supports -128 to 127
                                var reg = currentMethod.getVarTable().get(leftOp.getName());
                                code.append("iinc ").append(reg.getVirtualReg()).append(" ").append(increment).append(NL);
                                return code.toString();
                            }
                        } catch (NumberFormatException e) {
                            // Not a number, continue with normal path
                        }
                    }
                }
                
                // Check if i = 1 + i pattern
                if (leftOperand instanceof LiteralElement && rightOperand instanceof Operand) {
                    LiteralElement leftLit = (LiteralElement) leftOperand;
                    Operand rightOp = (Operand) rightOperand;
                    
                    if (rightOp.getName().equals(((Operand)lhs).getName())) {
                        try {
                            int increment = Integer.parseInt(leftLit.getLiteral());
                            if (increment >= -128 && increment <= 127) {
                                var reg = currentMethod.getVarTable().get(rightOp.getName());
                                code.append("iinc ").append(reg.getVirtualReg()).append(" ").append(increment).append(NL);
                                return code.toString();
                            }
                        } catch (NumberFormatException e) {
                            // Not a number, continue with normal path
                        }
                    }
                }
            }
        }
        
        // Array assignment case: a[i] = x
        if (lhs instanceof ArrayOperand) {
            ArrayOperand arrayOp = (ArrayOperand) lhs;
            
            // Get the array reference
            String baseName = arrayOp.getName();
            var baseReg = currentMethod.getVarTable().get(baseName);
            code.append(types.getOptimizedLoad("a", baseReg.getVirtualReg())).append(NL);
            
            // Get the index 
            for (Element indexElem : arrayOp.getIndexOperands()) {
                code.append(apply(indexElem));
            }
            
            // Get the value to store
            code.append(apply(rhs));
            
            // Store the value in the array
            // For arrays, we'll use the corresponding primitive type
            String typePrefix = "i"; // Default to int for array storage
            code.append(typePrefix + "astore").append(NL);
            
            return code.toString();
        }
        
        // Standard assignment
        // Generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // Get register
        var reg = currentMethod.getVarTable().get(operand.getName());
        int virtualReg = reg.getVirtualReg();

        // Use the appropriate type prefix and optimized store instruction
        String typePrefix = "i"; // Default to int for primitive types
        if (operand.getType() instanceof org.specs.comp.ollir.type.ClassType) {
            typePrefix = "a"; // Use "a" for object references
        }
        code.append(types.getOptimizedStore(typePrefix, virtualReg)).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        String typeStr = literal.getType().toString();
        if (typeStr.equals("INT32")) {
            // Use the most efficient instruction for loading constants
            try {
                int value = Integer.parseInt(literal.getLiteral());
                return types.getIntegerLoadInstruction(value) + NL;
            } catch (NumberFormatException e) {
                // If it's not a regular integer, use default ldc
                return "ldc " + literal.getLiteral() + NL;
            }
        } else if (typeStr.equals("BOOLEAN")) {
            // For booleans, convert true/false to 1/0
            if (literal.getLiteral().equals("1")) {
                return "iconst_1" + NL;
            } else {
                return "iconst_0" + NL;
            }
        } else {
            return "ldc " + literal.getLiteral() + NL;
        }
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());
        int virtualReg = reg.getVirtualReg();
        
        // Get the appropriate prefix based on type
        String typePrefix = types.getTypePrefix(operand.getType());
        
        // Use optimized load instructions
        return types.getOptimizedLoad(typePrefix, virtualReg) + NL;
    }

    private String lessThanOp(Element left, Element right){
        var code = new StringBuilder();

        var leftLiteral = (LiteralElement) left;
        var leftValue = leftLiteral.getLiteral();
        var rightLiteral = (LiteralElement) right;
        var rightValue = rightLiteral.getLiteral();


        if(Integer.parseInt(leftValue) < Integer.parseInt(rightValue)){
            code.append("ldc 1").append(NL);
        }
        else{
            code.append("ldc 0").append(NL);
        }


        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();
        OperationType opType = binaryOp.getOperation().getOpType();
        var operands = binaryOp.getOperands();
        Element leftOperand = operands.get(0);
        Element rightOperand = operands.get(1);

        // Check if this is a comparison with zero for optimized branching
        if (isComparisonWithZero(opType, rightOperand) && !(leftOperand instanceof LiteralElement)) {
            // For zero comparisons, only load the left operand
            code.append(apply(leftOperand));
            // Don't apply comparison instruction here - it will be handled in condition branch
            return code.toString();
        }
        
        // Check if this is a boolean operation that needs special handling
        if (opType == OperationType.ANDB || opType == OperationType.ORB) {
            return generateBooleanOperation(opType, leftOperand, rightOperand);
        }

        // For normal operations, load both operands
        code.append(apply(leftOperand));
        code.append(apply(rightOperand));

        // Get the appropriate type prefix
        String typePrefix = types.getTypePrefix(leftOperand.getType());
        
        // Apply operation
        switch (opType) {
            case ADD:
                code.append(typePrefix + "add").append(NL);
                break;
            case SUB:
                code.append(typePrefix + "sub").append(NL);
                break;
            case MUL:
                code.append(typePrefix + "mul").append(NL);
                break;
            case DIV:
                code.append(typePrefix + "div").append(NL);
                break;
            case LTH:
                // Compare and push 1 or 0 to the stack
                String ifLessLabel = "if_less_" + types.getCurrentTempLabel();
                String endLabel = "end_comparison_" + types.getCurrentTempLabel();
                code.append("if_icmplt ").append(ifLessLabel).append(NL);
                code.append("iconst_0").append(NL); // False case
                code.append("goto ").append(endLabel).append(NL);
                code.append(ifLessLabel).append(":").append(NL);
                code.append("iconst_1").append(NL); // True case
                code.append(endLabel).append(":").append(NL);
                break;
            case GTH:
                String ifGreaterLabel = "if_greater_" + types.getCurrentTempLabel();
                String endGreaterLabel = "end_comparison_" + types.getCurrentTempLabel();
                code.append("if_icmpgt ").append(ifGreaterLabel).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto ").append(endGreaterLabel).append(NL);
                code.append(ifGreaterLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append(endGreaterLabel).append(":").append(NL);
                break;
            case GTE:
                String ifGteLabel = "if_gte_" + types.getCurrentTempLabel();
                String endGteLabel = "end_comparison_" + types.getCurrentTempLabel();
                code.append("if_icmpge ").append(ifGteLabel).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto ").append(endGteLabel).append(NL);
                code.append(ifGteLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append(endGteLabel).append(":").append(NL);
                break;
            case LTE:
                String ifLteLabel = "if_lte_" + types.getCurrentTempLabel();
                String endLteLabel = "end_comparison_" + types.getCurrentTempLabel();
                code.append("if_icmple ").append(ifLteLabel).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto ").append(endLteLabel).append(NL);
                code.append(ifLteLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append(endLteLabel).append(":").append(NL);
                break;
            case EQ:
                String ifEqLabel = "if_eq_" + types.getCurrentTempLabel();
                String endEqLabel = "end_comparison_" + types.getCurrentTempLabel();
                code.append("if_icmpeq ").append(ifEqLabel).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto ").append(endEqLabel).append(NL);
                code.append(ifEqLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append(endEqLabel).append(":").append(NL);
                break;
            case NEQ:
                String ifNeqLabel = "if_neq_" + types.getCurrentTempLabel();
                String endNeqLabel = "end_comparison_" + types.getCurrentTempLabel();
                code.append("if_icmpne ").append(ifNeqLabel).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto ").append(endNeqLabel).append(NL);
                code.append(ifNeqLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append(endNeqLabel).append(":").append(NL);
                break;
            default:
                throw new NotImplementedException("Operation not implemented: " + opType);
        }

        return code.toString();
    }
    
    private boolean isComparisonWithZero(OperationType opType, Element rightOperand) {
        if (rightOperand instanceof LiteralElement && 
            (opType == OperationType.LTH || opType == OperationType.GTH || 
             opType == OperationType.GTE || opType == OperationType.LTE ||
             opType == OperationType.EQ || opType == OperationType.NEQ)) {
            
            try {
                int value = Integer.parseInt(((LiteralElement) rightOperand).getLiteral());
                return value == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
    
    private String generateBooleanOperation(OperationType opType, Element leftOperand, Element rightOperand) {
        var code = new StringBuilder();
        String endLabel = "boolean_op_end_" + types.getCurrentTempLabel();
        String shortCircuitLabel = "short_circuit_" + types.getCurrentTempLabel();
        
        if (opType == OperationType.ANDB) {
            // AND operation: if first is false, result is false
            code.append(apply(leftOperand));
            code.append("ifeq ").append(shortCircuitLabel).append(NL); // If left is 0, jump to shortCircuit
            code.append(apply(rightOperand));
            code.append("goto ").append(endLabel).append(NL);
            
            // Short circuit - result is false (0)
            code.append(shortCircuitLabel).append(":").append(NL);
            code.append("iconst_0").append(NL);
            
            // End of operation
            code.append(endLabel).append(":").append(NL);
        } else if (opType == OperationType.ORB) {
            // OR operation: if first is true, result is true
            code.append(apply(leftOperand));
            code.append("ifne ").append(shortCircuitLabel).append(NL); // If left is not 0, jump to shortCircuit
            code.append(apply(rightOperand));
            code.append("goto ").append(endLabel).append(NL);
            
            // Short circuit - result is true (1)
            code.append(shortCircuitLabel).append(":").append(NL);
            code.append("iconst_1").append(NL);
            
            // End of operation
            code.append(endLabel).append(":").append(NL);
        }
        
        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        
        // Check if there's an operand to return
        if (returnInst.hasReturnValue()) {
            Optional<Element> optOperand = returnInst.getOperand();
            if (optOperand.isPresent()) {
                Element operand = optOperand.get();
                code.append(apply(operand));
                
                // Use the appropriate return instruction based on return type
                String typePrefix = types.getTypePrefix(operand.getType());
                code.append(typePrefix + "return").append(NL);
                return code.toString();
            }
        }
        
        // Void return (no operand or operand not present)
        code.append("return").append(NL);
        return code.toString();
    }

    private String generateNewInstruction(NewInstruction newInstruction) {
        var code = new StringBuilder();

        var caller = (Operand) newInstruction.getCaller();
        var callerType = caller.getType();
        var typeStr = callerType.toString();

        // Check if this is an array instantiation
        if (typeStr.endsWith("[]")) {
            // For arrays, we need the size first
            var operands = newInstruction.getOperands();
            if (operands.size() > 0) {
                // Load the array size
                code.append(apply(operands.get(0)));
            }
            
            // Create the array of the appropriate type
            if (typeStr.equals("INT32[]")) {
                code.append("newarray int").append(NL);
            } else if (typeStr.equals("BOOLEAN[]")) {
                code.append("newarray boolean").append(NL);
            } else if (callerType instanceof org.specs.comp.ollir.type.ClassType) {
                // For object arrays
                String className = ((org.specs.comp.ollir.type.ClassType) callerType).getName().replace("[]", "");
                code.append("anewarray ").append(className).append(NL);
            } else {
                // Default to int array if type is unknown
                code.append("newarray int").append(NL);
            }
        } else {
            // For objects, just create the object
            code.append("new ").append(caller.getName()).append(NL);
            
            // If there are constructor arguments, we'll need to handle them in the invoke special instruction
        }

        return code.toString();
    }

    private String generateInvokeSpecialInstruction(InvokeSpecialInstruction invokeSpecialInstruction) {
        var code = new StringBuilder();
        
        // First, load the object reference
        Element caller = invokeSpecialInstruction.getCaller();
        code.append(apply(caller));
        
        // Build the method signature with parameters
        String callerName;
        if (caller instanceof Operand) {
            callerName = ((Operand) caller).getName();
        } else {
            callerName = caller.toString();
        }
        
        var args = invokeSpecialInstruction.getArguments();
        var argTypes = new StringBuilder();
        
        // Load any constructor arguments
        for (Element arg : args) {
            argTypes.append(types.ollirToJasminType(arg.getType()));
            code.append(apply(arg));
        }
        
        // Generate the constructor invocation
        code.append("invokespecial ");
        
        // Handle super() constructor calls specially
        if (callerName.equals("super")) {
            String superClass = ollirResult.getOllirClass().getSuperClass();
            if (superClass == null) {
                superClass = "java/lang/Object";
            }
            code.append(superClass);
        } else if (caller.getType() instanceof org.specs.comp.ollir.type.ClassType) {
            // Use the class type name if available
            code.append(((org.specs.comp.ollir.type.ClassType) caller.getType()).getName());
        } else {
            // Otherwise use the caller name
            code.append(callerName);
        }
        
        code.append("/<init>(")
            .append(argTypes)
            .append(")V")
            .append(NL);

        return code.toString();
    }

    private String generateInvokeStaticInstruction(InvokeStaticInstruction invokeStaticInstruction) {
        var code = new StringBuilder();

        // Get caller class name
        var caller = (Operand) invokeStaticInstruction.getCaller();
        var callerName = caller.getName();
        
        // Get method name - try multiple approaches to handle different OLLIR formats
        String methodName;
        
        // First try to get method name from the method name property
        if (invokeStaticInstruction.getMethodName() != null) {
            if (invokeStaticInstruction.getMethodName() instanceof LiteralElement) {
                methodName = ((LiteralElement) invokeStaticInstruction.getMethodName()).getLiteral();
            } else {
                // If it's not a literal, try to get a string representation
                methodName = invokeStaticInstruction.getMethodName().toString();
            }
        } else {
            // Fall back to getting method name from the second operand if available
            var operands = invokeStaticInstruction.getOperands();
            if (operands.size() >= 2 && operands.get(1) instanceof LiteralElement) {
                methodName = ((LiteralElement) operands.get(1)).getLiteral();
            } else {
                // Default to a placeholder if we can't determine the method name
                methodName = "unknownMethod";
            }
        }
        
        // Process arguments
        var args = invokeStaticInstruction.getArguments();
        var argTypes = new StringBuilder();

        for(var arg : args){
            argTypes.append(types.ollirToJasminType(arg.getType()));
            code.append(apply(arg));
        }

        // Generate invokestatic instruction
        code.append("invokestatic ");
        code.append(callerName)
                .append("/").append(methodName).append("(").append(argTypes).append(")")
                .append(types.ollirToJasminType(invokeStaticInstruction.getReturnType())).append(NL);

        return code.toString();
    }

    private String generatePutFieldInstruction(PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();

        var operands = putFieldInstruction.getOperands();
        
        // Get the object reference from first operand
        Element firstOp = operands.get(0);
        code.append(apply(firstOp));
        
        // Get field name from second operand
        var fieldOperand =(Operand) operands.get(1);
        var field = fieldOperand.getName();
        
        // Get value to store from third operand
        var valueElement = operands.get(2);
        code.append(apply(valueElement));
        
        // Determine the class name from the object type
        String className;
        if (firstOp instanceof Operand && ((Operand) firstOp).getName().equals("this")) {
            className = ollirResult.getOllirClass().getClassName();
        } else if (firstOp.getType() instanceof org.specs.comp.ollir.type.ClassType) {
            className = ((org.specs.comp.ollir.type.ClassType) firstOp.getType()).getName();
        } else {
            className = ollirResult.getOllirClass().getClassName();
        }
        
        // Get field type from the instruction - use the correct type
        var fieldType = types.ollirToJasminType(putFieldInstruction.getFieldType());
        
        // Generate the putfield instruction
        code.append("putfield ")
            .append(className)
            .append("/")
            .append(field)
            .append(" ")
            .append(fieldType)
            .append(NL);

        return code.toString();
    }

    private String generateGetFieldInstruction(GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();

        var operands = getFieldInstruction.getOperands();
        
        // Get the object reference
        Element firstOp = operands.get(0);
        code.append(apply(firstOp));
        
        // Get the field name
        var fieldOperand = (Operand) operands.get(1);
        var fieldName = fieldOperand.getName();
        var type = types.ollirToJasminType(getFieldInstruction.getFieldType());
        
        // Get the class name
        String className;
        if (firstOp instanceof Operand && ((Operand) firstOp).getName().equals("this")) {
            className = ollirResult.getOllirClass().getClassName();
        } else if (firstOp.getType() instanceof org.specs.comp.ollir.type.ClassType) {
            className = ((org.specs.comp.ollir.type.ClassType) firstOp.getType()).getName();
        } else {
            className = ollirResult.getOllirClass().getClassName();
        }
        
        code.append("getfield ")
            .append(className)
            .append("/")
            .append(fieldName)
            .append(" ")
            .append(type)
            .append(NL);
            
        return code.toString();
    }

    private String generateCondBranchInstruction(CondBranchInstruction condBranchInstruction) {
        var code = new StringBuilder();
        var condition = condBranchInstruction.getCondition();
        var label = condBranchInstruction.getLabel();
        
        if (condition instanceof BinaryOpInstruction) {
            BinaryOpInstruction binaryOp = (BinaryOpInstruction) condition;
            Operation operation = binaryOp.getOperation();
            OperationType opType = operation.getOpType();
            Element leftOperand = binaryOp.getOperands().get(0);
            Element rightOperand = binaryOp.getOperands().get(1);
            
            // For comparisons with zero, optimize the branch instruction
            if (isComparisonWithZero(opType, rightOperand) && rightOperand instanceof LiteralElement) {
                code.append(apply(leftOperand));
                
                // Use the appropriate conditional branch instruction
                switch (opType) {
                    case LTH:
                        code.append("iflt ").append(label).append(NL);
                        break;
                    case GTH:
                        code.append("ifgt ").append(label).append(NL);
                        break;
                    case GTE:
                        code.append("ifge ").append(label).append(NL);
                        break;
                    case LTE:
                        code.append("ifle ").append(label).append(NL);
                        break;
                    case EQ:
                        code.append("ifeq ").append(label).append(NL);
                        break;
                    case NEQ:
                        code.append("ifne ").append(label).append(NL);
                        break;
                    default:
                        // For other operations, use the default approach
                        code.append("ifne ").append(label).append(NL);
                }
                return code.toString();
            }
            
            // For regular comparison operations between two values
            code.append(apply(leftOperand));
            code.append(apply(rightOperand));
            
            switch (opType) {
                case LTH:
                    code.append("if_icmplt ").append(label).append(NL);
                    break;
                case GTH:
                    code.append("if_icmpgt ").append(label).append(NL);
                    break;
                case GTE:
                    code.append("if_icmpge ").append(label).append(NL);
                    break;
                case LTE:
                    code.append("if_icmple ").append(label).append(NL);
                    break;
                case EQ:
                    code.append("if_icmpeq ").append(label).append(NL);
                    break;
                case NEQ:
                    code.append("if_icmpne ").append(label).append(NL);
                    break;
                default:
                    // For other operations, generate a normal comparison
                    code.append("ifne ").append(label).append(NL);
            }
        } else if (condition instanceof SingleOpInstruction && ((SingleOpInstruction) condition).getSingleOperand() instanceof LiteralElement) {
            // Handle literal boolean values inside SingleOpInstruction
            SingleOpInstruction singleOp = (SingleOpInstruction) condition;
            LiteralElement literalElement = (LiteralElement) singleOp.getSingleOperand();
            String literal = literalElement.getLiteral();
            
            // For true literals, always branch; for false literals, never branch
            if (literal.equals("true") || literal.equals("1")) {
                code.append("goto ").append(label).append(NL);
            }
            // If false, do nothing - will fall through
        } else {
            // For other types of conditions, load the value and branch if nonzero
            code.append(apply(condition));
            code.append("ifne ").append(label).append(NL);
        }
        
        return code.toString();
    }

    private String generateGotoInstruction(GotoInstruction gotoInstruction) {
        var code = new StringBuilder();


        code.append("goto ").append(gotoInstruction.getLabel()).append(NL);
        return code.toString();
    }

    private String generateArrayOperand(ArrayOperand arrayOperand) {
        var code = new StringBuilder();
        
        // Load the array reference
        String baseName = arrayOperand.getName();
        var baseReg = currentMethod.getVarTable().get(baseName);
        
        // Arrays are always reference types, so use "a" prefix for loading the array reference
        code.append(types.getOptimizedLoad("a", baseReg.getVirtualReg())).append(NL);
        
        // Load the index
        var indexOperands = arrayOperand.getIndexOperands();
        for (Element indexElem : indexOperands) {
            code.append(apply(indexElem));
        }
        
        // Get the value from the array - use the appropriate type prefix
        // Determine element type based on array type
        String typePrefix = "i"; // Default to int
        Type type = arrayOperand.getType();
        if (type instanceof org.specs.comp.ollir.type.ArrayType) {
            Type elemType = ((org.specs.comp.ollir.type.ArrayType) type).getElementType();
            typePrefix = types.getTypePrefix(elemType);
        }
        
        code.append(typePrefix + "aload").append(NL);
        
        return code.toString();
    }
    
    private String generateInvokeVirtualInstruction(InvokeVirtualInstruction invokeVirtualInstruction) {
        var code = new StringBuilder();
        
        // Load the object reference (first operand)
        var operands = invokeVirtualInstruction.getOperands();
        Element firstOp = operands.get(0);
        code.append(apply(firstOp));
        
        // Load the method arguments
        var argTypes = new StringBuilder();
        
        for (int i = 2; i < operands.size(); i++) { // Skip the first (object) and second (method name) operands
            Element arg = operands.get(i);
            argTypes.append(types.ollirToJasminType(arg.getType()));
            code.append(apply(arg));
        }
        
        // Generate the method call
        String className;
        if (firstOp.getType() instanceof org.specs.comp.ollir.type.ClassType) {
            className = ((org.specs.comp.ollir.type.ClassType) firstOp.getType()).getName();
        } else if (firstOp instanceof Operand) {
            // If it's a named reference, try to get the class name from the variable name
            className = ((Operand) firstOp).getName();
        } else {
            // Default to current class if we can't determine
            className = ollirResult.getOllirClass().getClassName();
        }
        
        String methodName;
        if (operands.get(1) instanceof LiteralElement) {
            methodName = ((LiteralElement) operands.get(1)).getLiteral();
        } else {
            methodName = operands.get(1).toString();
        }
        
        var returnType = types.ollirToJasminType(invokeVirtualInstruction.getReturnType());
        
        code.append("invokevirtual ")
            .append(className)
            .append("/")
            .append(methodName)
            .append("(")
            .append(argTypes)
            .append(")")
            .append(returnType)
            .append(NL);
        
        return code.toString();
    }

    private String generateUnaryOpInstructions(UnaryOpInstruction unaryOpInstruction) {
        StringBuilder code = new StringBuilder();
        Operand op = (Operand) unaryOpInstruction.getOperand();
        code.append(apply(op));
        code.append("iconst_1").append(NL);
        code.append("ixor").append(NL);
        return code.toString();
    }
    
    /**
     * Calculate the maximum number of local variables needed for a method.
     */
    private int calculateLimitLocals(Method method) {
        int maxReg = 0;
        
        // Start with the base value:
        // - Non-static methods have 'this' in register 0
        // - Parameters start at 0 for static methods, 1 for non-static methods
        boolean isStatic = method.getMethodName().equals("main");
        int startIndex = isStatic ? 0 : 1;
        
        // Check parameters
        int paramIndex = startIndex;
        for (Element param : method.getParams()) {
            paramIndex++;
            // For double-sized types, increment again (unlikely in JMM but good practice)
            String typeStr = param.getType().toString();
            if (typeStr.equals("DOUBLE") || typeStr.equals("LONG")) {
                paramIndex++;
            }
        }
        maxReg = Math.max(maxReg, paramIndex);
        
        // Check local variables
        for (Descriptor descriptor : method.getVarTable().values()) {
            maxReg = Math.max(maxReg, descriptor.getVirtualReg() + 1); // +1 because registers are zero-based
        }
        
        return maxReg;
    }
    
    /**
     * Calculate the maximum stack size needed for a method.
     * This is a simplified version, a more accurate implementation would track
     * stack depths for each instruction.
     */
    private int calculateLimitStack(Method method) {
        int maxStack = 0;
        int currentStack = 0;
        
        for (Instruction instruction : method.getInstructions()) {
            // Calculate stack changes for different instruction types
            if (instruction instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) instruction;
                Instruction rhs = assign.getRhs();
                
                // Handle different types of right-hand side expressions
                if (rhs instanceof BinaryOpInstruction) {
                    // Binary operations pop 2 values and push 1
                    currentStack = Math.max(2, currentStack); // Need at least 2 slots
                } else if (rhs instanceof NewInstruction) {
                    // New array needs index + array reference
                    NewInstruction newInst = (NewInstruction) rhs;
                    String typeStr = newInst.getCaller().getType().toString();
                    if (typeStr.endsWith("[]")) {
                        currentStack = Math.max(2, currentStack);
                    } else {
                        currentStack = Math.max(1, currentStack); 
                    }
                } else if (rhs instanceof InvokeVirtualInstruction) {
                    // Method calls need space for all arguments + this
                    int argCount = ((InvokeVirtualInstruction) rhs).getOperands().size() - 1; // -1 for object reference
                    currentStack = Math.max(argCount + 1, currentStack);
                } else if (rhs instanceof InvokeStaticInstruction) {
                    // Static method calls need space for all arguments
                    int argCount = ((InvokeStaticInstruction) rhs).getOperands().size() - 1; // -1 for class name
                    currentStack = Math.max(argCount, currentStack);
                } else {
                    // Simple expression (literal, variable)
                    currentStack = Math.max(1, currentStack);
                }
            } 
            else if (instruction instanceof ReturnInstruction) {
                ReturnInstruction ret = (ReturnInstruction) instruction;
                if (ret.hasReturnValue()) {
                    currentStack = Math.max(1, currentStack);
                }
            }
            else if (instruction instanceof CondBranchInstruction) {
                // Condition branch typically needs 2 slots for comparison
                currentStack = Math.max(2, currentStack);
            }
            else if (instruction instanceof PutFieldInstruction || instruction instanceof GetFieldInstruction) {
                // Field access needs ref + value for put, just ref for get
                currentStack = Math.max(2, currentStack); 
            }
            
            maxStack = Math.max(maxStack, currentStack);
        }
        
        // A minimum stack size is necessary
        return Math.max(2, maxStack);
    }


}