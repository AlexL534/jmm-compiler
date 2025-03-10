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

public class ArrayOperationCheck extends AnalysisVisitor {
    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(ARRAY_SUBSCRIPT, this::visitArrayAccess);
        addVisit(ARRAY_LITERAL, this::visitArrayLiteral);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }
    
    private Void visitArrayLiteral(JmmNode arrayLiteral, SymbolTable table) {
        // Check that all elements in an array literal are of the same type
        String expectedType = "int";

        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);
        
        for (JmmNode element : arrayLiteral.getChildren()) {
            Type type = utils.getExprType(element);
            String elementType = type.getName();
            
            if (!elementType.equals(expectedType)) {
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
        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);

        // Check that the array expression is actually an array
        Type type = utils.getExprType(arrayExpr);

        if(type == null){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayExpr.getLine(),
                    arrayExpr.getColumn(),
                    "Error while finding the type of the array",
                    null
            ));
            return;
        }
        String arrayType = type.getName();
        
        if (!type.isArray()) {
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
        type = utils.getExprType(indexExpr);

        if (type == null) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayExpr.getLine(),
                    arrayExpr.getColumn(),
                    "Error while finding the type of the array index: " + indexExpr.toString(),
                    null
            ));
            return;
        }
        String indexType = type.getName();
        
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

}
