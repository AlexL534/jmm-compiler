grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
BOOLEAN: 'boolean';
STRING: 'String';

INTEGER : [0-9]+;

ID : [a-zA-Z_$][a-zA-Z_0-9$]* ;

WS : [ \t\n\r\f]+ -> skip ;

COMMENT
        : '/*' .*? '*/' -> channel(HIDDEN)
        ;

LINE_COMMENT
        : '//' ~[\r\n]* -> channel(HIDDEN)
        ;

program
    : importDecl* classDecl EOF
    ;

importDecl
    : 'import' value+=ID ('.' value+=ID)* ';' #ImportStmt
    ;

classDecl
    : CLASS name=ID ('extends' superClass=ID)? '{' varDecl* methodDecl* '}' #ClassDef
    ;

varDecl
    : type name=ID ('=' expr)? ';'
    ;

param
    : type name=ID
    ;

mainParam
    : STRING '[' ']' name=ID
    ;

returnType
    : type
    ;

returnTypeMain
    : 'static' 'void'
    ;

returnStmt
    : RETURN expr? ';'
    ;

methodDecl
    returns [boolean isPublic]
    : (PUBLIC) ? returnType name=ID '(' (param (',' param)*)? ')' '{' varDecl* stmt* returnStmt '}'
        {
           $isPublic = $PUBLIC != null;
        }
    | (PUBLIC) ? returnTypeMain name=ID '(' mainParam ')' '{' varDecl* stmt* '}'
        {
           $isPublic = $PUBLIC != null;
        }
    ;

type
    : value = INT '[' ']' #ArrayType
    | value = INT '...' #VarArgType
    | value = BOOLEAN #BooleanType
    | value = STRING #StringType
    | value = INT #IntType
    | value = ID #IdType
    ;

stmt
    : '{' stmt* '}' #BlockStmt
    | 'if' '(' expr ')' stmt ('else' stmt)? #IfStmt
    | 'while' '(' expr ')' stmt #WhileStmt
    | expr ';' #ExprStmt
    | varName=ID '=' expr ';' #AssignStmt
    | varName=ID '[' expr ']' '=' expr ';' #ArrayAssignStmt
    ;

expr
    : '(' expr ')' #Parentheses
    | expr '.' 'length' #ArrayLength
    | 'new' INT '[' expr ']' #ArrayCreation
    | 'new' name=ID '(' ')' #ObjectCreation
    | expr '[' expr ']' #ArraySubscript
    | expr '.' name=ID '(' (expr (',' expr)*)? ')' #MethodCall
    | expr '.' name=ID #FieldAccess
    | op='!' expr #UnaryOp
    | expr op=('*' | '/' ) expr #BinaryExpr
    | expr op=('+' | '-') expr #BinaryExpr
    | expr op='<' expr #BinaryExpr
    | expr op = '&&' expr #BinaryExpr
    | '[' (expr (',' expr)*)? ']' #ArrayLiteral
    | 'this' #ThisExpr
    | value=INTEGER #IntegerLiteral
    | value='true' #BooleanLiteral
    | value='false' #BooleanLiteral
    | name=ID #VarRefExpr
    ;


