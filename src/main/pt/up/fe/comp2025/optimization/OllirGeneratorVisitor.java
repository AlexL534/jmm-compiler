package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.Objects;
import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;

    private String currentMethod;


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImport);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(ARRAY_CREATION, this::visitArrayCreation);
        addVisit(BLOCK_STMT, this::visitBlockStmt);
        addVisit(ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);

        //setDefaultVisit(this::defaultVisit);
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        String childCode = visit(node.getChild(0));
        return childCode;
    }

    private String visitBlockStmt(JmmNode node, Void unused) {
        StringBuilder block = new StringBuilder();

        for(var child : node.getChildren()) {
            block.append(visit(child));
        }

        return block.toString();

    }

    private String visitImport(JmmNode node, Void unused){
        StringBuilder importStmt = new StringBuilder();
        importStmt.append("import ");

        for(var importValue: table.getImports()) {
            if(importValue.contains(node.get("ID"))){
                importStmt.append(node.get("ID"));
            }
        }

        return importStmt.toString() + SPACE + END_STMT;
    }

    private String visitArrayCreation(JmmNode node, Void unused) {
        // This is just in case the array creation is used as a statement on its own
        // Usually it will be handled by OllirExprGeneratorVisitor
        var arrExpr = exprVisitor.visit(node);
        return arrExpr.getComputation();
    }

    private String visitArrayAssignStmt(JmmNode node, Void unused) {
        String varName = node.get("varName");
        String firstChildCode = exprVisitor.visit(node.getChild(0)).getCode();
        String secondChildCode = exprVisitor.visit(node.getChild(1)).getCode();
        secondChildCode += END_STMT;
        if(firstChildCode.equals("")){
            firstChildCode = visit(node.getChild(0));
        }
        if(secondChildCode.equals("")){
            secondChildCode = visit(node.getChild(1));
        }

        StringBuilder res = new StringBuilder();

        res.append(varName).append("[").append(firstChildCode).append("]").append(".i32").append(SPACE)
                .append(ASSIGN).append(".i32").append(SPACE)
                .append(secondChildCode.trim());

        return res.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        // Set the current method in the expression visitor
        exprVisitor.currentMethod = this.currentMethod;
        
        var rhs = exprVisitor.visit(node.getChild(0));

        StringBuilder code = new StringBuilder();

        if (node.getChild(0).getKind().equals(NEW_OBJECT.getNodeName()) ||
                node.getChild(0).getKind().equals(OBJECT_CREATION.getNodeName())) {

            String className = types.getExprType(node.getChild(0)).getName();
            String tempVar = "tmp" + ollirTypes.nextTemp("obj") + "." + className;
            String varName = node.get("varName") + "." + className;

            code.append(tempVar).append(" :=.").append(className)
                    .append(" new(").append(className).append(").").append(className)
                    .append(END_STMT);

            code.append("invokespecial(").append(tempVar).append(", \"<init>\").V")
                    .append(END_STMT);

            code.append(varName).append(" :=.").append(className)
                    .append(" ").append(tempVar)
                    .append(END_STMT);

            return code.toString();
        }

        // code to compute the children
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        var left = node;
        types.setCurrentMethod(currentMethod);
        Type thisType = types.getExprType(left);
        String typeString = ollirTypes.toOllirType(thisType);
        var varCode = left.get("varName") + typeString;


        code.append(varCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {
        // Get the actual return type from the symbol table
        Type retType = table.getReturnType(currentMethod);

        StringBuilder code = new StringBuilder();

        // Set the current method in the expression visitor
        exprVisitor.currentMethod = this.currentMethod;
        
        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;

        code.append(expr.getComputation());
        code.append("ret");
        code.append(ollirTypes.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = node.getBoolean("isPublic", false);

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        code.append(name);

        // params
        exprVisitor.currentMethod = name;
        currentMethod = name;
        
        StringBuilder listParamsCode = new StringBuilder();
        if(node.getNumChildren() > 0) {
            var paramsNodes = node.getChildren(PARAM);
            for (int i = 0; i < paramsNodes.size(); i++) {
                var paramCode = visit(paramsNodes.get(i));
                listParamsCode.append(paramCode);
                if (i < paramsNodes.size() - 1) {
                    listParamsCode.append(",");
                }
            }
        }
        code.append("(" + listParamsCode + ")");

        // return type
        var returnType = table.getReturnType(node.get("name"));
        var retType = ollirTypes.toOllirType(returnType);
        code.append(retType);
        code.append(L_BRACKET);

        // rest of its children stmts
        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);

        var returnCode = node.getChildren(RETURN_STMT).stream()
                        .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

       if(returnCode.trim().equals("") && stmtsCode.trim().endsWith(":")){
           //end if stmt needs the return stmt even when the return type is void
            returnCode = "ret" + ollirTypes.toOllirType(TypeUtils.newVoidType()) + END_STMT;
        }
        code.append(returnCode);
        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        String superClass = table.getSuper();
        if(!superClass.isEmpty()) {
            code.append(" extends ").append(superClass);
        }
        
        code.append(L_BRACKET);
        code.append(NL);
        code.append(NL);

        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }

    private String visitMethodCall(JmmNode node, Void unused) {
        types.setCurrentMethod(currentMethod);


        String methodName = node.get("name");
        var firstChildType = types.getExprType(node.getChild(0)).getName();
        StringBuilder methodCall = new StringBuilder();
        if(firstChildType.equals(table.getClassName())){
            methodCall.append("invokevirtual(this.").append(table.getClassName()).append(", \"").append(methodName).append("\", ");
        }
        else{
            String methodObject = "";
            for(var imp : table.getImports()){
                if(imp.equals(node.getChild(0).get("name"))){
                    methodObject = imp;
                }
            }
            methodCall.append("invokestatic(").append(methodObject).append(", \"").append(methodName).append("\", ");
        }
        StringBuilder args = new StringBuilder();
        for (int i = 1; i < node.getChildren().size(); i++ ) {
            JmmNode child = node.getChildren().get(i);
            Type nodeType = types.getExprType(child);
            String ollirType = ollirTypes.toOllirType(nodeType);
            var kind = child.getKind();

            if(Kind.fromString(kind).equals(INTEGER_LITERAL) || Kind.fromString(kind).equals(BOOLEAN_LITERAL)) {
                args.append(child.get("value"));
            }
            else if (Kind.fromString(kind).equals(VAR_REF_EXPR)) {
                var result = exprVisitor.visit(child);
                args.append(result.getCode());
            }
            else{
                //in this case, we need to visit the node that wil create a temporary variable and then insert that temporary variable into the method call
                var result = exprVisitor.visit(child);
                methodCall = new StringBuilder(result.getComputation() + methodCall.toString());
                methodCall.append(result.getCode());
            }

            args.append(ollirType);
            if(i < node.getChildren().size() - 2) {
                args.append(", ");
            }
        }
        methodCall.append(args);
        methodCall.append(")");
        String returnType = ".V";
        if(firstChildType.equals(table.getClassName())) {
            returnType = ollirTypes.toOllirType(table.getReturnType(methodName));
        }
        methodCall.append(returnType).append(END_STMT);
        return methodCall.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        
        String ifEndLabel = "if_end_" + ollirTypes.nextTemp("if");
        String elseLabel = "else_" + ollirTypes.nextTemp("else");
        
        JmmNode conditionNode = node.getChild(0);
        var conditionResult = exprVisitor.visit(conditionNode);
        
        code.append(conditionResult.getComputation());
        
        code.append("if (" + conditionResult.getCode() + ") goto " + elseLabel + END_STMT);
        
        if (node.getNumChildren() > 1) {
            JmmNode thenNode = node.getChild(1);
            String thenCode = visit(thenNode);
            code.append(thenCode);
        }
        
        code.append("goto " + ifEndLabel + END_STMT);
        
        code.append(elseLabel + ":" + NL);

        if (node.getNumChildren() > 2) {
            JmmNode elseNode = node.getChild(2);
            String elseCode = visit(elseNode);
            code.append(elseCode);
        }
        
        code.append(ifEndLabel + ":" + NL);
        
        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        
        String whileCondLabel = "while_cond_" + ollirTypes.nextTemp("while");
        String whileBodyLabel = "while_body_" + ollirTypes.nextTemp("while");
        String whileEndLabel = "while_end_" + ollirTypes.nextTemp("while");
        
        code.append(whileCondLabel + ":" + NL);
        
        JmmNode conditionNode = node.getChild(0);
        var conditionResult = exprVisitor.visit(conditionNode);
        
        code.append(conditionResult.getComputation());
        
        code.append("if (" + conditionResult.getCode() + ") goto " + whileBodyLabel + END_STMT);
        code.append("goto " + whileEndLabel + END_STMT);
        
        code.append(whileBodyLabel + ":" + NL);
        
        if (node.getNumChildren() > 1) {
            JmmNode bodyNode = node.getChild(1);
            String bodyCode = visit(bodyNode);
            code.append(bodyCode);
        }
        
        code.append("goto " + whileCondLabel + END_STMT);
        
        code.append(whileEndLabel + ":" + NL);
        
        return code.toString();
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
