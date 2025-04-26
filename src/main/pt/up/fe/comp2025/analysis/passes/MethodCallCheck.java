package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;

public class MethodCallCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.METHOD_CALL, this::visitMethodCall);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitMethodCall(JmmNode node, SymbolTable table) {
        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);
        String methodName = node.get("name");

        //direct calls (no receiver)
        if (node.getNumChildren() == 0) {
            if (!table.getMethods().contains(methodName)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Method '" + methodName + "' not found in current class",
                        null));
            }
            return null;
        }

        JmmNode caller = node.getChild(0);
        Type callerType = utils.getExprType(caller);

        // 'this' calls
        if (caller.getKind().equals(Kind.THIS_EXPR.getNodeName())) {
            if (!table.getMethods().contains(methodName)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        "Method '" + methodName + "' not found in current class hierarchy",
                        null));
            }
            return null;
        }

        //external class calls
        if (callerType == null) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Cannot resolve type of method caller",
                    null));
            return null;
        }

        String typeName = callerType.getName();

        //check if class is imported or exists
        if (!isTypeAvailable(typeName, table)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Class '" + typeName + "' is not imported or doesn't exist",
                    null));
            return null;
        }

        //check if method exists in target class
        if (!isMethodAvailable(typeName, methodName, table)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Method '" + methodName + "' not found in class '" + typeName + "'",
                    null));
        }

        return null;
    }

    private boolean isTypeAvailable(String typeName, SymbolTable table) {
        // check if it's the current class
        if (typeName.equals(table.getClassName())) {
            return true;
        }

        // check imports
        return table.getImports().stream()
                .anyMatch(imp -> imp.equals(typeName) ||
                        imp.endsWith("." + typeName) ||
                        imp.endsWith(".*"));
    }

    private boolean isMethodAvailable(String className, String methodName, SymbolTable table) {
        // check current class and its hierarchy
        if (className.equals(table.getClassName())) {
            return table.getMethods().contains(methodName);
        }

        // check external classes (imported)
        return isMethodInImportedClass(className, methodName, table);
    }

    private boolean isMethodInImportedClass(String className, String methodName, SymbolTable table) {
        List<String> imports = table.getImports();
        for (String imp : imports) {
            if (imp.equals(className) || imp.endsWith("." + className)) {
                return true;
            }
        }
        return false;
    }
}
