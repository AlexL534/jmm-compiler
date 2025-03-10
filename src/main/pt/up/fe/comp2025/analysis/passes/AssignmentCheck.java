package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import static pt.up.fe.comp2025.ast.Kind.RETURN_STMT;

public class AssignmentCheck extends AnalysisVisitor {
    private String currentMethod;
    @Override
    public void buildVisitor() {
        //addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        //addVisit(RETURN_STMT, this::visitReturnStmt);
    }

}
