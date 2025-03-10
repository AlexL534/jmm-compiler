package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import static pt.up.fe.comp2025.ast.Kind.*;

public class InvalidOperationCheck extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(BINARY_EXPR, this::visitBinaryOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private boolean isOperationValid(String op, String leftType, String rightType) {
        // Assignment operations
        if (op.equals("=") || op.equals("+=") || op.equals("-=") || op.equals("*=") || op.equals("/=") || op.equals("%=")) {
            // Simple assignment: must be compatible types
            if (op.equals("=")) {
                return leftType.equals(rightType) || 
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
            if (leftType.equals(rightType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if a type is a primitive data type
     */
    private boolean isPrimitiveType(String type) {
        return type.equals("int") || type.equals("boolean");
    }

    private String getNodeType(JmmNode node, SymbolTable table ) {
        if (node == null) {
            return "unknown";
        }

        //check if the node is the name of a variable, field or parameter
        if(node.hasAttribute("name")) {
            Type nodeType = null;

            var parameters = table.getParameters(currentMethod);
            // Var is a parameter
            if (parameters.stream().anyMatch(param -> param.getName().equals(node.get("name")))) {
                for (Symbol param : parameters) {
                    if (param.getName().equals(node.get("name"))) {
                        nodeType = param.getType();

                    }
                }
            }

            // Var is a declared variable, return
            var locals = table.getLocalVariables(currentMethod);
            if (locals.stream()
                    .anyMatch(varDecl -> varDecl.getName().equals(node.get("name")))) {
                for (Symbol varDecl : locals) {
                    if (varDecl.getName().equals(node.get("name"))) {
                        nodeType = varDecl.getType();
                    }
                }

            }

            // Var is a field
            var fields = table.getFields();
            if (fields.stream()
                    .anyMatch(field -> field.getName().equals(node.get("name")))) {
                for (Symbol field : fields) {
                    if (field.getName().equals(node.get("name"))) {
                        nodeType = field.getType();
                    }
                }

            }

            //check if the node was found and is a variable/field/parameter
            if (nodeType != null) {
                if (nodeType.isArray()){
                    return "array" + nodeType.getName();
                }
                else if (nodeType.getName().equals("int")) {
                    return "int";
                } else if (nodeType.getName().equals("boolean")) {
                    return "boolean";
                } else {
                    return "unknown";
                }
            }
        }

        if (INTEGER_LITERAL.check(node)) {
            return "int";
        }
        else if (BOOLEAN_LITERAL.check(node)) {
            return "boolean";
        }




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
        
        if (node.hasAttribute("value")) {
            String value = node.get("value");
            if (value.equals("true") || value.equals("false")) {
                return "boolean";
            }
            try {
                Integer.parseInt(value);
                return "int";
            } catch (NumberFormatException e) {
                // Not an integer
            }
        }
        
        return "unknown";
    }

    private Void visitBinaryOp(JmmNode node, SymbolTable table) {
        String op = node.get("op");
        
        if (node.getNumChildren() < 2) {
            return null;
        }
        
        JmmNode leftNode = node.getChildren().get(0);
        JmmNode rightNode = node.getChildren().get(1);
        
        String leftType = getNodeType(leftNode, table);
        String rightType = getNodeType(rightNode, table);
        
        if (!isOperationValid(op, leftType, rightType)) {
            String message = String.format("Invalid operation: %s between %s and %s", op, leftType, rightType);
            
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