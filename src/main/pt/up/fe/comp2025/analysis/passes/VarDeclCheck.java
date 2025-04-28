package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

public class VarDeclCheck extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        var type = varDecl.getChild(0);
        if(type.getKind().equals(Kind.VAR_ARG_TYPE.toString())){
            if(!varDecl.getParent().getKind().equals(Kind.PARAM.toString())){
                var message = String.format("Varargs can only be used as method parameters");
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        varDecl.getLine(),
                        varDecl.getColumn(),
                        message,
                        null)
                );
            }
        }
        return null;
    }
}
