package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import static pt.up.fe.comp2025.ast.Kind.*;

public class InvalidOperationCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        // Use the enum constant directly
        addVisit(BINARY_EXPR, this::visitBinaryOp);
    }

    private boolean isOperationValid(String op, String leftType, String rightType) {
        // Display the check being performed
        System.out.println("Checking operation: '" + op + "' between types '" + leftType + "' and '" + rightType + "'");
        
        // Assignment operations
        if (op.equals("=") || op.equals("+=") || op.equals("-=") || op.equals("*=") || op.equals("/=") || op.equals("%=")) {
            // Simple assignment: must be compatible types
            if (op.equals("=")) {
                return leftType.equals(rightType) || 
                       (isSubtype(rightType, leftType)) || 
                       (rightType.equals("null") && !isPrimitiveType(leftType));
            }
            
            // Compound assignments: usually require numeric types
            return leftType.equals("int") && rightType.equals("int");
        }
        
        // Arithmetic operations: +, -, *, /, %
        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || op.equals("%")) {
            return leftType.equals("int") && rightType.equals("int");
        }

        // Logical operations: &&, ||
        if (op.equals("&&") || op.equals("||")) {
            return leftType.equals("boolean") && rightType.equals("boolean");
        }

        // Comparison operations: <, >, <=, >= (only valid for int types)
        if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
            return leftType.equals("int") && rightType.equals("int");
        }

        // Equality operations: ==, !=
        if (op.equals("==") || op.equals("!=")) {
            // Same types can be compared
            if (leftType.equals(rightType)) {
                return true;
            }
        }

        return false;
    }

    // Helper method to check if a type is a primitive
    private boolean isPrimitiveType(String type) {
        return type.equals("int") || type.equals("boolean");
    }

    // Helper method to check if rightType is a subtype of leftType
    // In a real compiler, this would check the inheritance hierarchy
    private boolean isSubtype(String rightType, String leftType) {
        // Basic implementation - in a real compiler you would check the class hierarchy
        return false; // Placeholder - replace with actual subtype checking
    }

    private String getNodeType(JmmNode node) {
        if (node == null) {
            return "unknown";
        }

        // Check node kind to determine type
        String kind = node.getKind();
        
        // Print the kind for debugging
        System.out.println("Node kind: " + kind);
        
        // Use Kind enum to check node types
        if (INTEGER_LITERAL.check(node)) {
            return "int";
        } 
        else if (kind.equals("BooleanLiteral")) {
            return "boolean";
        } 
        
        // Check if it's an explicit binary operation with determinable result type
        if (BINARY_EXPR.check(node)) {
            String op = node.get("op");
            if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=") || 
                op.equals("==") || op.equals("!=") || op.equals("&&") || op.equals("||")) {
                return "boolean";
            } 
            else if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || op.equals("%")) {
                return "int";
            }
        }
        
        // If node has a value attribute that is "true" or "false"
        if (node.hasAttribute("value")) {
            String value = node.get("value");
            if (value.equals("true") || value.equals("false")) {
                return "boolean";
            }
            // Try to parse as integer
            try {
                Integer.parseInt(value);
                return "int";
            } catch (NumberFormatException e) {
                // Not an integer
            }
        }
        
        // Default unknown
        return "unknown";
    }

    private Void visitBinaryOp(JmmNode node, SymbolTable table) {
        String op = node.get("op");
        
        // Verify we have enough children
        if (node.getNumChildren() < 2) {
            System.out.println("Binary operation node has too few children: " + node.getNumChildren());
            return null;
        }
        
        JmmNode leftNode = node.getChildren().get(0);
        JmmNode rightNode = node.getChildren().get(1);
        
        String leftType = getNodeType(leftNode);
        String rightType = getNodeType(rightNode);
        
        System.out.println("Checking operation: " + op + " between " + leftType + " and " + rightType);
        
        // Check if the operation is valid between these types
        if (!isOperationValid(op, leftType, rightType)) {
            String message = String.format("Invalid operation: %s between %s and %s", op, leftType, rightType);
            System.out.println("Adding error report: " + message);
            
            // Create and add the error report
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