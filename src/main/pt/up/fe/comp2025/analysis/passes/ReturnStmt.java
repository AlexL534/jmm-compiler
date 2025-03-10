package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import static pt.up.fe.comp2025.ast.Kind.BINARY_EXPR;
import static pt.up.fe.comp2025.ast.Kind.RETURN_STMT;

public class ReturnStmt extends AnalysisVisitor {
    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }


    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);

        Type exprType = utils.getExprType(returnStmt.getChild(0));

        if(exprType == null){
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    returnStmt.getLine(),
                    returnStmt.getColumn(),
                    "Could not evaluate the return expression",
                    null
            );

            return null;
        }

        //check if any of the types is imported (imported classes don't need type checking)
        for(String imp : table.getImports() ){
            if(imp.equals(exprType.getName()) ){
                return null;
            }
        }

        //Extended classes to imports are ignored in type check
        if(table.getImports().contains(table.getSuper()) && (table.getClassName().equals(exprType.getName()))){
            return null;
        }

        Type methodType = table.getReturnType(currentMethod);
        if((!methodType.getName().equals(exprType.getName())) || (methodType.isArray() != exprType.isArray())){
            String message = String.format("Invalid return type: %s and %s", exprType.getName(), methodType.getName());
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    returnStmt.getLine(),
                    returnStmt.getColumn(),
                    message,
                    null
            );

            addReport(report);
        }



        return null;
    }
}
