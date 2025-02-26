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

DIGIT : [0-9] ;
DECIMAL : [1-9] DIGIT*;

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
    : 'import' ID ('.' ID)* ';'
    ;

classDecl
    : CLASS ID ('extends' ID)? '{' varDecl* methodDecl* '}'
    ;

varDecl
    : type ID ';'
    ;

methodDecl
    : PUBLIC ? type ID '(' (type ID (',' type ID)*)? ')' '{' varDecl* stmt* RETURN expr ';' '}'
    | PUBLIC ? 'static' 'void' 'main' '(' STRING '[' ']' ID ')' '{' varDecl* stmt* '}'
    ;

type
    : INT '[' ']'
    | INT '...'
    | BOOLEAN
    | STRING
    | INT
    | ID
    ;

stmt
    : '{' stmt* '}'
    | 'if' '(' expr ')' stmt 'else' stmt
    | 'while' '(' expr ')' stmt
    | expr ';'
    | ID '=' expr ';'
    | ID '[' expr ']' '=' expr ';'
    ;

expr
    : expr ('&&' | '<' | '+' | '-' | '*' | '/') expr
    | expr '[' expr ']' (expr)?
    | expr '.' 'length'
    | expr '.' ID '(' (expr (',' expr)*)? ')'
    | 'new' INT '[' expr ']'
    | 'new' ID '(' ')'
    | '!' expr
    | '(' expr ')'
    | '[' (expr (',' expr)*)? ']'
    | (DECIMAL | DIGIT)
    | 'true'
    | 'false'
    | ID
    | 'this'
    ;


