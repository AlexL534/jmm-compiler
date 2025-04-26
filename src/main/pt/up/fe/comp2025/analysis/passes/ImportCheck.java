package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static pt.up.fe.comp2025.ast.Kind.IMPORT_DECL;

public class ImportCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(IMPORT_DECL, this::visitImportDecl);
    }


    private Void visitImportDecl(JmmNode importDecl, SymbolTable table) {
        List<String> imports = table.getImports();
        Set<String> seen = new HashSet<>();

        for (String imp : imports) {
            if (!seen.add(imp)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        -1, -1,
                        "Duplicate import: " + imp,
                        null
                ));
            }
        }

        return null;
    }
}
