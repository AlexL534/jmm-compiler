package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import static pt.up.fe.comp2025.ast.Kind.*;

public class InvalidOperationCheck extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(BINARY_EXPR, this::visitBinaryOp);
        addVisit(THIS_EXPR, this::thisOperations);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void thisOperations(JmmNode node, SymbolTable table) {
        //checks if the "this" operation is being done inside the main function (the only static and void function allowed in the language)
        if(table.getReturnType(currentMethod).getName().equals("void")) {
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Cannot use a 'this' operator inside a static function",
                    null
            );

            addReport(report);
        }
        return null;
    }

    private boolean isOperationValid(String op, String leftType, String rightType, boolean isArrayLeft, boolean isArrayRight) {
        // Arithmetic operations: +, -, *, /
        return switch (op) {
            case "+", "-", "*", "/" ->
                    leftType.equals("int") && rightType.equals("int") && !isArrayLeft && !isArrayRight;


            // Logical operations: &&
            case "&&" -> leftType.equals("boolean") && rightType.equals("boolean");


            // Comparison operations: < (only valid for int types)
            case "<" -> leftType.equals("int") && rightType.equals("int") && !isArrayLeft && !isArrayRight;
            default -> false;
        };

    }

    private Void visitBinaryOp(JmmNode node, SymbolTable table) {
        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);

        String op = node.get("op");
        
        if (node.getNumChildren() < 2) {
            return null;
        }
        
        JmmNode leftNode = node.getChildren().get(0);
        JmmNode rightNode = node.getChildren().get(1);

        Type leftType = utils.getExprType(leftNode);
        Type rightType = utils.getExprType(rightNode);

        String leftTypeName = "unknown";
        boolean isArrayLeft = false;
        if(leftType != null) {
            leftTypeName = leftType.getName();
            isArrayLeft = leftType.isArray();
        }
        String rightTypeName = "unknown";
        boolean isArrayRight = false;
        if(rightType != null) {
            rightTypeName = rightType.getName();
            isArrayRight = rightType.isArray();
        }

        
        if (!isOperationValid(op, leftTypeName, rightTypeName,isArrayLeft,isArrayRight  )) {
            String  message = String.format("Invalid operation: %s between %s and %s", op, leftTypeName, rightTypeName);
            
            Report report = Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null
            );
            
            addReport(report);
        }
        
        return null;
    }
}