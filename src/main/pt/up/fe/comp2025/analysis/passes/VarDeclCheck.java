package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class VarDeclCheck extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
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

        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);
        String typeName = utils.getExprType(varDecl).getName();

    for(var imp : table.getImports()){
        var impSubstring = imp.split("\\.");
        if(typeName.equals(impSubstring[impSubstring.length - 1].strip())){
            return null;
        }
    }
    if(typeName.equals(table.getClassName())){
        return null;
    }
    if(typeName.equals(table.getSuper())){
        return null;
    }
    if(typeName.equals("int")){
        return null;
    }
    if(typeName.equals("boolean")){
        return null;
    }

    var message = String.format("Unsupported variable type: %s", typeName);
    addReport(Report.newError(
            Stage.SEMANTIC,
            varDecl.getLine(),
            varDecl.getColumn(),
            message,
            null)
    );

        return null;
    }
}
