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
    : 'import' value+=ID ('.' value+=ID)* ';' #ImportStmt
    ;

classDecl
    : CLASS name=ID ('extends' superClass=ID)? '{' varDecl* methodDecl* '}' #ClassDef
    ;

varDecl
    : type name=ID ';'
    ;

param
    : type name=ID
    ;

returnType
    : type
    ;

methodDecl
    : (PUBLIC) ? returnType name=ID '(' (param (',' param)*)? ')' '{' varDecl* stmt* RETURN expr ';' '}'
    | (PUBLIC) ? 'static' 'void' name='main' '(' STRING '[' ']' ID ')' '{' varDecl* stmt* '}'
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
    | 'new' name=ID '(' ')' #ObjectCreation
    | expr '.' name=ID '(' (expr (',' expr)*)? ')' #MethodCall
    | expr '.' name=ID #FieldAccess
    | op='!' expr #UnaryOp
    | expr op=('++' | '--') #UnaryOp
    | expr op=('*' | '/' | '%') expr #BinaryOp
    | expr op=('+' | '-') expr #BinaryOp
    | expr op=('<' | '<=' | '>' | '>=') expr #BinaryOp
    | expr op = ('==' | '!=') expr #BinaryOp
    | expr op = '&&' expr #BinaryOp
    | expr op='||' expr #BinaryOp
    | expr op=('=' | '+=' | '-=' | '*=' | '/=' | '%=') expr #BinaryOp
    | expr '[' expr ']' (expr)? #ArraySubscript
    | '[' (expr (',' expr)*)? ']' #ArrayLiteral
    | 'this' #ThisExpr
    | value=INTEGER #IntegerLiteral
    | value='true' #BooleanLiteral
    | value='false' #BooleanLiteral
    | name=ID #VarRefExpr
    ;


