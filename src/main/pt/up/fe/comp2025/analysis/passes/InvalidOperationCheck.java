package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

public class InvalidOperationCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private boolean isOperationValid(String op, String leftType, String rightType) {
        // Arithmetic operations: +, -, *, /, %
        if ((op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || op.equals("%")) &&
                (leftType.equals("int") && rightType.equals("int"))) {
            return true;
        }

        // Logical operations: &&, ||
        if ((op.equals("&&") || op.equals("||")) &&
                (leftType.equals("boolean") && rightType.equals("boolean"))) {
            return true;
        }

        // Comparison operations: <, >, <=, >= (only valid for int types)
        if ((op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) &&
                (leftType.equals("int") && rightType.equals("int"))) {
            return true;
        }

        // Equality operations: ==, !=
        if (op.equals("==") || op.equals("!=")) {
            // Allow comparisons between identical types
            if (leftType.equals(rightType)) {
                return true;
            }
            // Allow integer type promotion (int == float)
            if ((leftType.equals("int") && rightType.equals("float")) ||
                    (leftType.equals("float") && rightType.equals("int"))) {
                return true;
            }
            // Allow object reference comparisons
            if (!leftType.equals("int") && !leftType.equals("boolean") &&
                    !rightType.equals("int") && !rightType.equals("boolean")) {
                return true; // Assume reference types can be compared
            }
        }

        return false;
    }

    private Void visitBinaryOp(JmmNode node, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        String op = node.get("op");
        String leftType = node.getChildren().get(0).hasAttribute("type") ? node.getChildren().get(0).get("type") : "unknown";
        String rightType = node.getChildren().get(1).hasAttribute("type") ? node.getChildren().get(1).get("type") : "unknown";

        // System.out.print("op: " + op + ", leftType: " + leftType + ", rightType: " + rightType + "\n");

        if (!isOperationValid(op, leftType, rightType)) {
            // Create error report
            var message = String.format("Invalid operation: %s between %s and %s", op, leftType, rightType);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null)
            );
        }

        return null;
    }

}