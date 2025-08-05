grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

IMPORT: 'import'; CLASS: 'class'; EXTENDS: 'extends'; PUBLIC: 'public'; STATIC: 'static';
VOID: 'void'; STRING: 'String'; BOOLEAN: 'boolean'; TRUE: 'true'; FALSE: 'false';
RETURN: 'return'; IF: 'if'; ELSE: 'else'; WHILE: 'while'; NEW: 'new'; THIS: 'this';
MAIN: 'main'; INT_TYPE: 'int'; ELLIPSIS: '...'; LENGTH: 'length';

LPAREN: '('; RPAREN: ')'; LBRACE: '{'; RBRACE: '}'; LBRACK: '['; RBRACK: ']';
SEMI: ';'; COMMA: ','; DOT: '.'; ASSIGN: '='; PLUS: '+'; MINUS: '-'; STAR: '*';
DIV: '/'; NOT: '!'; LT: '<'; BT: '>'; BOE: '>='; LOE: '<='; MOD: '%'; EQ: '=='; NOT_EQ: '!=';
AND: '&&'; OR: '||';

COMMENT: '//' ~[\r\n]* -> skip;
COMMENT2: '/*' .*? '*/' -> skip;

INTEGER: [0-9]+;
ID: [a-zA-Z_][a-zA-Z_0-9]*;
WS: [ \t\r\n\f]+ -> skip;

program: importDeclaration* classDeclaration* EOF;

importDeclaration: IMPORT qualifiedName SEMI #IMPORT_DECLARATION;
qualifiedName: firstPart=ID (DOT otherParts+=ID)*;
importDecl: importDeclaration;
importDeclarationMulti: IMPORT qualifiedName (COMMA qualifiedName)+ SEMI;

classDeclaration: CLASS name=ID (EXTENDS extendsId=ID)? LBRACE classBody RBRACE #CLASS_DECL;
classBody: (varDeclaration | methodDecl)* #CLASS_BODY;

varDeclaration: type name=ID SEMI #VAR_DECL
              | type name=ID ASSIGN expr SEMI #VAR_DECL;

type: name=INT_TYPE arraySuffix? #TYPE
    | name=BOOLEAN arraySuffix? #TYPE
    | name=STRING arraySuffix? #TYPE
    | name=ID arraySuffix? #TYPE;

functionType: name=VOID #FUNCTION_TYPE
           | type #FUNCTION_TYPE;

arraySuffix: (LBRACK RBRACK)(arraySuffix)?;
arrayDimensions: (LBRACK RBRACK)+;

methodDecl
    : (PUBLIC)? functionType name=ID LPAREN paramList? RPAREN block #METHOD_DECL
    | (PUBLIC)? STATIC VOID name=MAIN LPAREN mainParams RPAREN block #METHOD_DECL;

mainParams: STRING LBRACK RBRACK name=ID #MAIN_PARAM;
paramList: param (COMMA param)*;
param: type ellipsis=ELLIPSIS? name=ID #PARAM;

block: LBRACE stmt* RBRACE #STMT;

stmt
    : varDeclaration #VarDeclarationStmt
    | ifStatement #IfStmt
    | whileStatement #WhileStmt
    | assignStatement #ASSIGN_STMT
    | RETURN (expr)? SEMI #RETURN_STMT
    | expr SEMI #ExpressionStmt
    | block #BlockStmt;

ifStatement: IF LPAREN expr RPAREN stmt (ELSE stmt)?;

whileStatement: WHILE LPAREN expr RPAREN stmt;

assignStatement: lvalue ASSIGN expr SEMI;

lvalue: name=ID #IdentifierLValue
      | name=ID LBRACK expr RBRACK #ArrayAccessLValue
      | expr DOT name=ID #FieldAccessLValue;

expr
    : value=INTEGER #IntLiteral
    | TRUE #TrueLiteral
    | FALSE #FalseLiteral
    | THIS #ThisExpression
    | name=ID #VAR_REF_EXPR
    | LPAREN expr RPAREN #ParenExpr
    | NEW INT_TYPE multiDimArrayDeclaration #NewMultiDimArrayExpr
    | NEW INT_TYPE LBRACK expr RBRACK (LBRACK expr? RBRACK)* #NewIntArrayExpr
    | NEW ID LBRACK expr RBRACK (LBRACK expr? RBRACK)* #NewObjectArrayExpr
    | expr LBRACK expr RBRACK #ArrayAccessExpr
    | NEW name=ID LPAREN RPAREN #NewObjectExpr
    | arrayInitializer #ArrayInitializerExpr
    | name=ID LPAREN argumentList? RPAREN #DIRECT_METHOD_CALL
    | expr LBRACK expr RBRACK #ArrayAccessExpr
    | expr DOT name=ID #FieldAccessExpr
    | expr DOT LENGTH #ArrayLengthExpr
    | expr DOT name=ID LPAREN argumentList? RPAREN #MethodCallExpr
    | operator=(PLUS|MINUS) expr #SignExpr
    | NOT expr #NotExpr
    // expressoes binarias
    | expr operator=(STAR|DIV|MOD) expr #MULTIPLICATIVE_EXPR
    | expr operator=(PLUS|MINUS) expr #ADDITIVE_EXPR
    | expr operator=(LT|BT|BOE|LOE|EQ|NOT_EQ) expr #RELATIONAL_EXPR
    | expr operator=AND expr #LogicalAndExpr
    | expr operator=OR expr #LogicalOrExpr;

methodCall: name=ID LPAREN argumentList? RPAREN;
argumentList: expr (COMMA expr)*;
arrayInitializer: LBRACK (arrayElement (COMMA arrayElement)*)? RBRACK;
arrayElement: expr | arrayInitializer;
multiDimArrayDeclaration: LBRACK expr RBRACK (LBRACK expr RBRACK)+;
