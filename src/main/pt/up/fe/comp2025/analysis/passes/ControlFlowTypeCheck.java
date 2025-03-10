package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import static pt.up.fe.comp2025.ast.Kind.*;

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
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(METHOD_DECL, this::visitMethodDecl);
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
        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);

        String type = utils.getExprType(condition).getName();//determineType(condition, table);
        
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


}
