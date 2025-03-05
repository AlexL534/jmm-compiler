package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

public class ArrayOperationCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit("ArraySubscript", this::visitArrayAccess);
        addVisit("ArrayAssignStmt", this::visitArrayAssignment);
    }

    private Void visitArrayAccess(JmmNode arrayAccess, SymbolTable table) {
        // First child is the array expression
        JmmNode arrayExpr = arrayAccess.getChildren().get(0);
        // Second child is the index expression
        JmmNode indexExpr = arrayAccess.getChildren().get(1);
        
        checkArrayAccess(arrayExpr, indexExpr, table);
        return null;
    }

    private Void visitArrayAssignment(JmmNode arrayAssign, SymbolTable table) {
        // The array identifier is available in the node itself
        String arrayName = arrayAssign.get("name");
        // The index is the first child
        JmmNode indexExpr = arrayAssign.getChildren().get(0);
        // Create a fake node for array identifier to reuse checks
        // In a real implementation, resolve the array identifier properly
        checkArrayAccessByName(arrayName, indexExpr, table);
        
        return null;
    }

    private void checkArrayAccess(JmmNode arrayExpr, JmmNode indexExpr, SymbolTable table) {
        // Check that the array expression is actually an array
        String arrayType = getExpressionType(arrayExpr, table);
        
        if (!arrayType.endsWith("[]")) {
            String message = "Array access performed on non-array type: " + arrayType;
            
            addReport(Report.newError(
                Stage.SEMANTIC,
                arrayExpr.getLine(),
                arrayExpr.getColumn(),
                message,
                null
            ));
        }
        
        // Check that the index is an integer
        String indexType = getExpressionType(indexExpr, table);
        
        if (!indexType.equals("int")) {
            String message = "Array index must be an integer, found: " + indexType;
            
            addReport(Report.newError(
                Stage.SEMANTIC,
                indexExpr.getLine(),
                indexExpr.getColumn(),
                message,
                null
            ));
        }
    }

    private void checkArrayAccessByName(String arrayName, JmmNode indexExpr, SymbolTable table) {
        // Check that this variable is indeed an array
        // This is a simplified version - in real code you'd resolve the variable type
        String arrayType = resolveVariableType(arrayName, table);
        
        if (!arrayType.endsWith("[]")) {
            String message = "Array access performed on non-array variable: " + arrayName +
                             " of type " + arrayType;
            
            addReport(Report.newError(
                Stage.SEMANTIC,
                indexExpr.getLine(), // Not ideal, but we need line info from somewhere
                indexExpr.getColumn(),
                message,
                null
            ));
        }
        
        // Check that the index is an integer
        String indexType = getExpressionType(indexExpr, table);
        
        if (!indexType.equals("int")) {
            String message = "Array index must be an integer, found: " + indexType;
            
            addReport(Report.newError(
                Stage.SEMANTIC,
                indexExpr.getLine(),
                indexExpr.getColumn(),
                message,
                null
            ));
        }
    }
    
    private String resolveVariableType(String varName, SymbolTable table) {
        // This would connect to your existing variable resolution logic
        // Simplified implementation - would need to access current method context
        
        // For now just return "unknown" - replace with actual implementation
        return "unknown";
    }
    
    private String getExpressionType(JmmNode node, SymbolTable table) {
        // Reuse your existing type resolution logic
        // For now just a stub - replace with actual type resolution
        
        String kind = node.getKind();
        
        if (kind.equals("IntegerLiteral")) {
            return "int";
        }
        else if (kind.equals("BooleanLiteral")) {
            return "boolean";
        }
        else if (kind.equals("ArrayType") || (kind.equals("IdType") && node.get("value").endsWith("[]"))) {
            return "int[]"; // Assuming arrays are of int type as per grammar
        }
        
        return "unknown";
    }
}
