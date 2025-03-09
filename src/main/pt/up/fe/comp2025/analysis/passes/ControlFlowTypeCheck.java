package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

/**
 * Checks that conditions in if and while statements are boolean expressions.
 * Handles two test cases:
 * 1. intInIfCondition - Using integer in if condition
 * 2. arrayInWhileCondition - Using array in while condition
 */
public class ControlFlowTypeCheck extends AnalysisVisitor {

    private String currentMethod;
    
    /**
     * This method is crucial - it registers our visitors for the exact AST node kinds
     * from the grammar file. The IfStmt and WhileStmt node kinds directly match
     * the labels in the Javamm.g4 grammar file.
     */
    @Override
    public void buildVisitor() {
        // These MUST match the #NodeName labels in the grammar
        addVisit("IfStmt", this::visitIfStmt);
        addVisit("WhileStmt", this::visitWhileStmt);
        addVisit("MethodDecl", this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode node, SymbolTable table) {
        currentMethod = node.get("name");
        return null;
    }

    /**
     * Handles If statements - first child is the condition expression
     */
    private Void visitIfStmt(JmmNode node, SymbolTable table) {
        // Per grammar: 'if' '(' expr ')' stmt 'else' stmt #IfStmt
        // The condition is the first child
        if (node.getNumChildren() >= 1) {
            JmmNode condition = node.getChildren().get(0);
            checkCondition(condition, "if", table);
        }
        return null;
    }

    /**
     * Handles While statements - first child is the condition expression
     */
    private Void visitWhileStmt(JmmNode node, SymbolTable table) {
        // Per grammar: 'while' '(' expr ')' stmt #WhileStmt
        // The condition is the first child
        if (node.getNumChildren() >= 1) {
            JmmNode condition = node.getChildren().get(0);
            checkCondition(condition, "while", table);
        }
        return null;
    }

    /**
     * Check that a condition is boolean. If not, report an error.
     */
    private void checkCondition(JmmNode condition, String statementType, SymbolTable table) {
        String type = determineType(condition, table);
        
        // Key check - the condition must be a boolean expression
        if (!type.equals("boolean")) {
            String message = String.format("Non-boolean expression used as %s condition. Found type: %s", statementType, type);
                           
            // Create and add error report
            Report report = Report.newError(
                Stage.SEMANTIC,
                condition.getLine(),
                condition.getColumn(),
                message,
                null
            );
            addReport(report);
        }
    }

    /**
     * Determine the type of an expression node.
     * Handles all the expression types needed for the tests.
     */
    private String determineType(JmmNode node, SymbolTable table) {
        String kind = node.getKind();
        
        // Handle literal values
        if (kind.equals("IntegerLiteral")) {
            return "int";
        }
        
        if (kind.equals("BooleanLiteral")) {
            return "boolean";
        }
        
        // Handle variable references
        if (kind.equals("VarRefExpr")) {
            String varName = node.get("name");
            return getVariableType(varName, table);
        }
        
        // Handle arrays - crucial for arrayInWhileCondition test
        if (kind.equals("ArrayCreation") || kind.equals("ArrayType") || kind.equals("ArrayLiteral")) {
            return "int[]";  // In this grammar, arrays are int arrays
        }
        
        // Handle binary expressions like comparisons
        if (kind.equals("BinaryExpr")) {
            String op = node.get("op");
            // Logical and comparison operators return boolean
            if (op.matches("&&|\\|\\||==|!=|<|>|<=|>=")) {
                return "boolean";
            } else {
                return "int";  // Arithmetic operations
            }
        }
        
        // Handle unary operations
        if (kind.equals("UnaryOp")) {
            return node.get("op").equals("!") ? "boolean" : "int";
        }
        
        // For parenthesized expressions, inspect the inner expression
        if (kind.equals("Parentheses") && node.getNumChildren() > 0) {
            return determineType(node.getChildren().get(0), table);
        }
        
        return "unknown";
    }
    
    /**
     * Get the type of a variable from the symbol table.
     */
    private String getVariableType(String name, SymbolTable table) {
        if (table == null || currentMethod == null) {
            return "unknown";
        }
        
        // Check method parameters
        for (var param : table.getParameters(currentMethod)) {
            if (param.getName().equals(name)) {
                return param.getType().getName();
            }
        }
        
        // Check local variables
        for (var local : table.getLocalVariables(currentMethod)) {
            if (local.getName().equals(name)) {
                return local.getType().getName();
            }
        }
        
        // Check class fields
        for (var field : table.getFields()) {
            if (field.getName().equals(name)) {
                return field.getType().getName();
            }
        }
        
        return "unknown";
    }
}
