package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {

        reports = new ArrayList<>();

        var imports = buildImports(root);
        var classDeclarations = root.getChildren(CLASS_DECL);
        if(classDeclarations.isEmpty()){
            reports.add(newError(root, "Class declaration not found"));
            throw new RuntimeException("Class declaration not found");
        }
        var classDecl = classDeclarations.getFirst();
        var superClass = "";
        if(classDecl.hasAttribute("superClass")){
            superClass = classDecl.get("superClass");
        }

        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");
        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(imports, className,superClass,fields, methods, returnTypes, params, locals);
    }

    private List<String> buildImports(JmmNode root) {
        List<String> list = new ArrayList<>();

        for(var child : root.getChildren(IMPORT_DECL)) {
            var valuesStr = child.get("value").substring(1, child.get("value").length() - 1);
            var finalValue = String.join(".", valuesStr.split(","));
            list.add(finalValue);
        }
        return list;
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        List <Symbol> fields = new ArrayList<>();
        List < String> names = new ArrayList<>();
        for(var field : classDecl.getChildren(VAR_DECL)){
            var name = field.get("name");
            var type = TypeUtils.convertType(field.getChild(0));
            var symbol = new Symbol(type,name);
            if(names.contains(symbol.getName())){
                var report = newError(field, "Duplicate field " + name);
                reports.add(report);
            }
            names.add(symbol.getName());
            fields.add(symbol);
        }

        return fields;
    }


    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            var typeDecl = method.getChildren(RETURN_TYPE);

            if(typeDecl.isEmpty()){
                typeDecl = method.getChildren(RETURN_TYPE_MAIN);
                if(typeDecl.isEmpty()){
                    var report = newError(method, "Invalid main method: invalid return type");
                    reports.add(report);
                }
                map.put(name, TypeUtils.newVoidType());
                continue;
            }

            var returnType = TypeUtils.convertType(typeDecl.getFirst().getChild(0));
            map.put(name, returnType);
        }

        return map;
    }


    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            List<Symbol> params;
            if(name.equals("main")){
                params = method.getChildren(MAIN_PARAM).stream()
                        .map(param -> new Symbol(TypeUtils.newStringArrayType(), param.get("name")))
                        .toList();
            }else {
                params = method.getChildren(PARAM).stream()
                        .map(param -> new Symbol(TypeUtils.convertType(param.getChild(0)), param.get("name")))
                        .toList();
            }

            List<String> auxList = new ArrayList<>();
            for(var param : params){
                if(auxList.contains(param.getName())){
                    var report = newError(method, "Repeated Params in " + method.get("name"));
                    reports.add(report);
                }
                auxList.add(param.getName());
            }

            map.put(name, params);
        }

        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        var map = new HashMap<String, List<Symbol>>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            var locals = method.getChildren(VAR_DECL).stream()
                    .map(varDecl -> new Symbol(TypeUtils.convertType(varDecl.getChild(0)), varDecl.get("name")))
                    .toList();

            List<String> auxList = new ArrayList<>();
            for(var local : locals){
                if(auxList.contains(local.getName())){
                    var report = newError(method, "Repeated locals in " + method.get("name"));
                    reports.add(report);
                }
                auxList.add(local.getName());
            }

            map.put(name, locals);
        }

        return map;
    }

    private List<String> buildMethods(JmmNode classDecl) {

        var methods = new ArrayList<String>();
        for(var method : classDecl.getChildren(METHOD_DECL)){
            if(methods.contains(method.get("name"))){
                var report = newError(method, "Repeated method " + method.get("name"));
                reports.add(report);
            }
            methods.add(method.get("name"));
        }

        return methods;
    }


}
