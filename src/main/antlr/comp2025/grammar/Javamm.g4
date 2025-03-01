/*grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : INT ;
PUBLIC : PUBLIC ;
RETURN : 'return' ;

INTEGER : [0-9] ;
ID : [a-zA-Z]+ ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : classDecl EOF
    ;


classDecl
    : CLASS name=ID
        '{'
        methodDecl*
        '}'
    ;

varDecl
    : type name=ID ';'
    ;

type
    : name= INT ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        '(' param ')'
        '{' varDecl* stmt* '}'
    ;

param
    : type name=ID
    ;

stmt
    : expr '=' expr ';' #AssignStmt //
    | RETURN expr ';' #ReturnStmt
    ;

expr
    : expr op= '*' expr #BinaryExpr //
    | expr op= '+' expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | name=ID #VarRefExpr //
    ;
*/

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

INTEGER : [0] | ([1-9][0-9]*);

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
    : 'import' ID ('.' ID)* ';' #ImportStmt
    ;

classDecl
    : CLASS ID ('extends' ID)? '{' varDecl* methodDecl* '}' #ClassDef
    ;

varDecl
    : type ID ';'
    ;

methodDecl
    : (PUBLIC) ? type ID '(' (type ID (',' type ID)*)? ')' '{' varDecl* stmt* RETURN expr ';' '}'
    | (PUBLIC) ? 'static' 'void' 'main' '(' STRING '[' ']' ID ')' '{' varDecl* stmt* '}'
    ;

type
    : INT '[' ']' #ArrayType
    | INT '...' #VarArgType
    | BOOLEAN #BooleanType
    | STRING #StringType
    | INT #IntType
    | ID #IdType
    ;

stmt
    : '{' stmt* '}' #BlockStmt
    | 'if' '(' expr ')' stmt 'else' stmt #IfStmt
    | 'while' '(' expr ')' stmt #WhileStmt
    | expr ';' #ExprStmt
    | ID '=' expr ';' #AssignStmt
    | ID '[' expr ']' '=' expr ';' #ArrayAssignStmt
    ;

expr
    : '(' expr ')' #Parentheses
    | expr '.' 'length' #ArrayLength
    | 'new' INT '[' expr ']' #ArrayCreation
    | 'new' ID '(' ')' #ObjectCreation
    | expr '.' ID '(' (expr (',' expr)*)? ')' #MethodCall
    | expr '.' ID #FieldAccess
    | '!' expr #UnaryOp
    | expr ('++' | '--') #UnaryOp
    | expr ('*' | '/' | '%') expr #BinaryOp
    | expr ('+' | '-') expr #BinaryOp
    | expr ('<' | '<=' | '>' | '>=') expr #BinaryOp
    | expr ('==' | '!=') expr #BinaryOp
    | expr ('&&') expr #BinaryOp
    | expr ('||') expr #BinaryOp
    | expr ('=' | '+=' | '-=' | '*=' | '/=' | '%=') expr #BinaryOp
    | expr '[' expr ']' (expr)? #ArraySubscript
    | '[' (expr (',' expr)*)? ']' #ArrayLiteral
    | 'this' #ThisExpr
    | INTEGER #IntegerLiteral
    | 'true' #BooleanLiteral
    | 'false' #BooleanLiteral
    | ID #VarRefExpr
    ;


