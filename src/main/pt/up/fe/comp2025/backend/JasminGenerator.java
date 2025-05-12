package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.LiteralElement;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Operand;
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

        code.append(".super ").append(fullSuperClass).append(NL);

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


        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
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

        // generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());


        // TODO: Hardcoded for int type, needs to be expanded
        code.append("istore ").append(reg.getVirtualReg()).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());

        // TODO: Hardcoded for int type, needs to be expanded
        return "iload " + reg.getVirtualReg() + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        // TODO: Hardcoded for int type, needs to be expanded
        var typePrefix = "i";

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "add";
            case MUL -> "mul";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(typePrefix + op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // TODO: Hardcoded for int type, needs to be expanded
        code.append("ireturn").append(NL);

        return code.toString();
    }

    private String generateNewInstruction(NewInstruction newInstruction){
        var code = new StringBuilder();

        var caller = (Operand) newInstruction.getCaller();
        var callerType = caller.getType();

        switch(callerType.toString()){
            case "INT32[]":
                code.append("newarray int").append(NL);
                break;
            default:
                code.append("new ").append(caller.getName()).append(NL);
        }

        return code.toString();
    }

    private String generateInvokeSpecialInstruction(InvokeSpecialInstruction invokeSpecialInstruction) {
        var code = new StringBuilder();
        code.append("invokenonvirtual ");

        var caller = (Operand) invokeSpecialInstruction.getCaller();
        var callerName = caller.getName();

        code.append(callerName).append("/<init>()V").append(NL);

        return code.toString();
    }

    private String generatePutFieldInstruction(PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();

        var operands = putFieldInstruction.getOperands();

        var className = ollirResult.getOllirClass().getClassName();
        var fieldOperand =(Operand) operands.get(1);
        var field = fieldOperand.getName();
        var literalElement = operands.get(2);
        var literalType = types.ollirToJasminType(literalElement.getType());
        var pushCode = apply(literalElement);

        code.append("aload 0").append(NL); //load the "this" value
        code.append(pushCode).
                append("putfield ").append(className).append("/").append(field).append(" ").append(literalType).append(NL);

        return code.toString();
    }

    private String generateGetFieldInstruction(GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();

        var operands = getFieldInstruction.getOperands();
        var className = ollirResult.getOllirClass().getClassName();
        var fieldOperand = (Operand) operands.get(1);
        var type = types.ollirToJasminType(getFieldInstruction.getFieldType());

        code.append("aload 0").append(NL); //load the "this" value
        code.append("getfield ").append(className).append("/").append(fieldOperand.getName()).append(" ").append(type).append(NL);
        return code.toString();
    }
}