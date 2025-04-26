package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.IMPORT_DECL;

public class ImportCheck extends AnalysisVisitor {
    private final Map<String, String> simpleNameToFullImport = new HashMap<>();

    @Override
    public void buildVisitor() {
        addVisit(IMPORT_DECL, this::visitImportDecl);
    }

    private Void visitImportDecl(JmmNode importDecl, SymbolTable table) {
        List<String> imports = table.getImports();
        Set<String> seenFullImports = new HashSet<>();

        for (String imp : imports) {
            imp = imp.replaceAll("\\s*\\.\\s*", "."); // removes spaces after '.' in imports (x.y instead of x. y)
            // duplicate full imports
            if (!seenFullImports.add(imp)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        -1, -1,
                        "Duplicate import: " + imp,
                        null
                ));
                continue;
            }

            // checks same simple name, except *
            if (!imp.endsWith(".*")) {
                String simpleName = getSimpleName(imp);
                if (simpleNameToFullImport.containsKey(simpleName) &&
                        !simpleNameToFullImport.get(simpleName).equals(imp)) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            -1, -1,
                            "Import name conflict for '" + simpleName + "' between " +
                                    simpleNameToFullImport.get(simpleName) + " and " + imp,
                            null
                    ));
                } else {
                    simpleNameToFullImport.put(simpleName, imp);
                }
            }
        }
        return null;
    }

    private String getSimpleName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return (lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName).trim();
    }
}