package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;

    public String currentMethod; //used to get the current method that is being visited


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(ARRAY_CREATION, this::visitArrayCreation);
        addVisit(ARRAY_LENGTH, this::visitArrayLength);
        addVisit(ARRAY_SUBSCRIPT, this::visitArraySubscript);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(NEW_OBJECT, this::visitNewObject);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitArrayCreation(JmmNode node, Void unused) {
        // Handle array creation expression
        // Get the size expression from the first child
        var sizeExpr = visit(node.getChild(0));
        
        StringBuilder computation = new StringBuilder();
        
        // Add the computation for the array size
        computation.append(sizeExpr.getComputation());
        
        // Create a new temporary variable for the array
        String arrayType = ".array.i32";
        String tempVar = ollirTypes.nextTemp() + arrayType;
        
        // Generate the OLLIR code for array creation
        computation.append(tempVar).append(SPACE)
                  .append(ASSIGN).append(arrayType).append(SPACE)
                  .append("new(array, ").append(sizeExpr.getCode()).append(")").append(arrayType)
                  .append(END_STMT);
        
        return new OllirExprResult(tempVar, computation);
    }
    private OllirExprResult visitArrayLength(JmmNode node, Void unused) {
        //get the code of the original type
        var arrayReference = visit(node.getChild(0));

        // Create a new temporary variable for the array length
        String lenType = ".i32";
        String tempVar = ollirTypes.nextTemp() + lenType;

        StringBuilder computation = new StringBuilder();

        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(lenType).append(SPACE)
                .append("arraylength( ").append(arrayReference.getCode()).append(")").append(lenType)
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitArraySubscript(JmmNode node, Void unused) {
        //get the code of the original type
        var arrayReference = visit(node.getChild(0));

        //get the code of the expression inside the subscript
        var subscriptExpr = visit(node.getChild(1));

        // Create a new temporary variable for the array subscript
        String arrType = ".i32";
        String tempVar = ollirTypes.nextTemp() + arrType;

        StringBuilder computation = new StringBuilder();

        computation.append(arrayReference.getComputation());
        computation.append(subscriptExpr.getComputation());

        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(arrType).append(SPACE)
                .append(arrayReference.getCode()).append("[")
                .append(subscriptExpr.getCode()).append("]").append(arrType)
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        //method call used when it is used inside expressions (some code is the same as in the normal ollir generator visitor)
        String funcType = ".i32";
        String tempVar = ollirTypes.nextTemp() + funcType;
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
                var result = visit(child);
                args.append(result.getCode());
            }
            else{
                //in this case, we need to visit the node that wil create a temporary variable and then insert that temporary variable into the method call
                var result = visit(child);
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
        methodCall.append(returnType);


        //create the computation
        StringBuilder computation = new StringBuilder();

        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(funcType).append(SPACE)
                .append(methodCall.toString())
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var booleanType = TypeUtils.newBooleanType();
        String ollirBooleanType = ollirTypes.toOllirType(booleanType);
        String newValue = "0";
        if(node.get("value").equals("true")){
            newValue = "1";
        }
        String code = newValue + ollirBooleanType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        types.setCurrentMethod(currentMethod);
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = types.getExprType(node);
        computation.append(node.get("op")).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        types.setCurrentMethod(currentMethod);
        Type type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {
        String className = types.getExprType(node).getName();
        String tempVar = ollirTypes.nextTemp() + "." + className;

        StringBuilder computation = new StringBuilder();

        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(".").append(className).append(SPACE)
                .append("new(").append(className).append(").").append(className)
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
