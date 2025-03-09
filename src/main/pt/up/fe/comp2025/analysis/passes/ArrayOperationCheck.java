package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import static pt.up.fe.comp2025.ast.Kind.*;

public class ArrayOperationCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(ARRAY_SUBSCRIPT, this::visitArrayAccess);
        addVisit(ARRAY_LITERAL, this::visitArrayLiteral);
        // Add direct visit for binary expressions with assignment
        addVisit(BINARY_EXPR, this::visitBinaryExpr);
    }
    
    // New method to catch assignment of array literals in binary expressions
    private Void visitBinaryExpr(JmmNode node, SymbolTable table) {
        if (!node.get("op").equals("=")) {
            return null; // Only process assignment operations
        }
        
        JmmNode leftExpr = node.getChildren().get(0);
        JmmNode rightExpr = node.getChildren().get(1);
        
        // We're only interested in cases where right side is an array literal
        if (!ARRAY_LITERAL.check(rightExpr)) {
            return null;
        }
        
        // Get the type of the left side
        String leftType;
        if (VAR_REF_EXPR.check(leftExpr)) {
            leftType = getVariableType(leftExpr.get("name"), table);
        } else {
            leftType = getExpressionType(leftExpr, table);
        }
        
        // Check if the left side is an array type
        if (!leftType.endsWith("[]")) {
            String message = "Cannot assign array literal to non-array type " + leftType;
            
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

    private Void visitArrayLiteral(JmmNode arrayLiteral, SymbolTable table) {
        // Check that all elements in an array literal are of the same type
        String expectedType = null;
        
        for (JmmNode element : arrayLiteral.getChildren()) {
            String elementType = getExpressionType(element, table);
            
            if (expectedType == null) {
                expectedType = elementType;
            } else if (!elementType.equals(expectedType)) {
                String message = "Array literals must have consistent element types. " +
                                "Found " + elementType + " but expected " + expectedType;
                
                addReport(Report.newError(
                    Stage.SEMANTIC,
                    element.getLine(),
                    element.getColumn(),
                    message,
                    null
                ));
            }
        }
        
        return null;
    }

    private Void visitArrayAccess(JmmNode arrayAccess, SymbolTable table) {
        // First child is the array expression
        JmmNode arrayExpr = arrayAccess.getChildren().get(0);
        // Second child is the index expression
        JmmNode indexExpr = arrayAccess.getChildren().get(1);
        
        checkArrayAccess(arrayExpr, indexExpr, table);
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
        // First check that the variable is actually an array
        String arrayType = getVariableType(arrayName, table);
        
        if (!arrayType.endsWith("[]")) {
            String message = "Array access performed on non-array type: " + arrayType;
            
            addReport(Report.newError(
                Stage.SEMANTIC,
                indexExpr.getLine(), // Using index node for location since we don't have the array identifier node
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
    
    private String getExpressionType(JmmNode node, SymbolTable table) {
        if (INTEGER_LITERAL.check(node)) {
            return "int";
        }
        else if (BOOLEAN_LITERAL.check(node)) {
            return "boolean";
        }
        else if (STRING_LITERAL.check(node)) {
            return "String";
        }
        else if (ARRAY_TYPE.check(node) || (ID_TYPE.check(node) && node.get("value").endsWith("[]"))) {
            String baseType = ARRAY_TYPE.check(node) ? 
                node.getChildren().get(0).get("value") : 
                node.get("value").substring(0, node.get("value").length() - 2);
            
            return baseType + "[]";
        }
        else if (ARRAY_LITERAL.check(node)) {
            if (node.getNumChildren() > 0) {
                String elementType = getExpressionType(node.getChildren().get(0), table);
                if (elementType.endsWith("[]")) {
                    elementType = elementType.substring(0, elementType.length() - 2);
                }
                return elementType + "[]";
            }
            return "Object[]";
        }
        else if (VAR_REF_EXPR.check(node)) {
            return getVariableType(node.get("name"), table);
        }
        
        return "unknown";
    }
    
    private String getVariableType(String name, SymbolTable table) {
        // Check method parameters
        for (var methodsParam : table.getMethods()) {
            for (var param : table.getParameters(methodsParam)) {
                if (param.getName().equals(name)) {
                    return param.getType().getName() + (param.getType().isArray() ? "[]" : "");
                }
            }
            
            // Check local variables
            for (var local : table.getLocalVariables(methodsParam)) {
                if (local.getName().equals(name)) {
                    return local.getType().getName() + (local.getType().isArray() ? "[]" : "");
                }
            }
        }
        
        // Check class fields
        for (var field : table.getFields()) {
            if (field.getName().equals(name)) {
                return field.getType().getName() + (field.getType().isArray() ? "[]" : "");
            }
        }
        
        return "unknown";
    }
}
