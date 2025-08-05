# Projeto de Compiladores
Membros do grupo:
  - Álvaro Luís Dias Amaral Alvim Torres (up202208954@up.pt)
  - Diogo Miguel Fernandes Ferreira (up202205295@up.pt)
  - Tomás Ferreira de Oliveira (up202208415@up.pt)

Todos os membros contribuíram igualmente para a totalidade do desenvolvimento do projeto.

# CP2
## Otimizações a Nível da AST

* **Propagação de Constantes**: Identifica variáveis com valores conhecidos estaticamente e substitui as suas ocorrências pelas constantes efetivas
* **Folding de Constantes**: Avalia expressões com valores constantes em tempo de compilação e substitui-as pelos seus resultados
* **Otimização Sensível a Ciclos**: Tratamento especial para variáveis modificadas dentro de ciclos para preservar a semântica correta
* **Rastreio de Variáveis com Múltiplas Atribuições**: Deteta variáveis atribuídas em diferentes caminhos de execução (p. ex., ramos if-else)
* **Tratamento de Varargs**: Transforma a sintaxe de parâmetros varargs em operações de array adequadas em toda a AST
* **Identidades Algébricas**: Reconhece e otimiza padrões como multiplicação por 0/1, adição de 0, subtração de 0, entre outros
* **Avaliação de Expressões Relacionais**: Resolve comparações constantes (como 5 < 10) durante a compilação
* **Simplificação de Expressões Booleanas**: Otimiza expressões como AND/OR quando um dos operandos é constante
* **Eliminação de Código Inatingível**: Remove código que nunca será executado (p. ex., após condições sempre falsas)

## Otimizações OLLIR

* **Correção de Formato de Importação**: Corrige problemas comuns de sintaxe em declarações de importação
* **Aplicação da Ordem de Elementos**: Garante que campos, construtores e métodos apareçam na ordem correta
* **Correção de Invocação de Métodos**: Adiciona instruções de invocação de métodos ausentes quando necessário
* **Indentação de Corpos de Métodos**: Melhora a legibilidade do código OLLIR gerado
* **Gestão de Etiquetas Sequenciais**: Utiliza contador sequencial global para garantir etiquetas únicas
* **Armazenamento em Cache de Resultados de Expressões**: Evita recálculos durante a geração de código
* **Formatação Consistente de Estruturas de Controlo**: Aplicação de regras específicas para if-then-else e ciclos

## Análise de Tempo de Vida e Grafo de Interferência

* **Cálculo de Conjuntos DEF/USE**: Identifica onde as variáveis são definidas e utilizadas
* **Iteração In/Out até Ponto Fixo**: Algoritmo de análise de fluxo de dados para determinar tempo de vida
* **Construção de Intervalos de Vida**: Mapeia pontos de instrução onde cada variável está viva
* **Construção do Grafo de Interferência**: Identifica quais variáveis não podem partilhar registos
* **Tratamento de Interferências Implícitas**: Identificação de interferências que não são óbvias no código-fonte
* **Validação e Correção do Grafo**: Garante simetria e consistência no grafo de interferência

## Alocação de Registos

* **Coloração de Grafo DSatur**: Implementa o algoritmo DSatur para alocação eficiente de registos
* **Coalescência de Cópias**: Funde variáveis em instruções de cópia quando os seus intervalos de vida não se sobrepõem
* **Modos de Alocação de Registos**:
  * Modo `-r=-1`: Utiliza o número predefinido de registos (sem otimização)
  * Modo `-r=0`: Otimiza para utilizar o número mínimo de registos
  * Modo `-r=n` (n > 0): Utiliza no máximo n registos com derrame de variáveis quando necessário
* **Gestão de Spilling de Variáveis**: Quando não há registos suficientes, algumas variáveis partilham registos
* **Priorização de Variáveis**: Atribui registos primeiro a variáveis com maior número de interferências
* **Tratamento de Variáveis Não Utilizadas**: Permite que variáveis não utilizadas partilham registos

## Otimizações de Casos Especiais

* **Tratamento de Parâmetros Especiais**: Garante que `this` esteja no registo 0 e outros parâmetros tenham registos dedicados
* **Otimização de Variáveis Temporárias**: Prioriza a alocação de registos para variáveis regulares em detrimento das temporárias
* **Deteção de Padrões Tipo Switch**: Tratamento otimizado de estruturas if-else em cascata
* **Otimização do Método Main**: Alocação de registos especial para o método main
* **Alocação de Emergência**: Sistema de segurança para garantir alocação quando métodos normais falham
* **Normalização de Etiquetas**: Uniformização da nomenclatura de etiquetas para estruturas de controlo
* **Tratamento Short-Circuit para Operadores Lógicos**: Implementação eficiente de avaliação para AND/OR
* **Gestão Especial de Retorno com Variáveis Multi-Atribuídas**: Preserva variáveis originais em instruções de retorno

# CP3

## Geração de Código Jasmin

* **Estrutura de Classes**: Gera declarações de classe, superclasse e constructores por defeito
* **Campos e Métodos**: Cria declarações completas de campos e métodos com modificadores apropriados
* **Assinaturas de Métodos**: Gera assinaturas corretas incluindo parâmetros e tipos de retorno
* **Suporte para Varargs**: Deteta e trata métodos com parâmetros variáveis de forma automática
* **Invocações de Métodos**: Implementa chamadas estáticas, virtuais e especiais com gestão adequada da pilha
* **Operações com Arrays**: Suporte completo para criação, acesso, atribuição e obtenção do comprimento de arrays
* **Gestão de Objectos**: Criação de objectos e acesso/atribuição de campos com validação de tipos

## Otimizações de Instruções JVM

* **Instruções de Carregamento Otimizadas**: Utiliza formas especializadas como `iload_0`, `aload_1`, `istore_2` quando possível
* **Carregamento de Constantes Eficiente**: Seleciona automaticamente entre `iconst_0`, `bipush`, `sipush` e `ldc` conforme o valor
* **Otimização de Incrementos**: Deteta padrões `i = i + 1` e substitui pela instrução `iinc` mais eficiente
* **Comparações com Zero**: Utiliza instruções de comparação directa (`iflt`, `ifne`) em vez de comparações de dois operandos
* **Optimização Cross-Instruction**: Identifica e optimiza padrões que atravessam múltiplas instruções OLLIR
* **Gestão Inteligente de Constantes**: Tratamento especial para valores booleanos e constantes numéricas

## Análise de Pilha e Variáveis Locais

* **Cálculo de `.limit stack`**: Determina o tamanho máximo da pilha necessário para cada método
* **Cálculo de `.limit locals`**: Calcula o número exacto de variáveis locais requeridas
* **Análise de Chamadas de Métodos**: Considera argumentos e tipos de retorno no cálculo da pilha
* **Tratamento de Operações com Arrays**: Análise específica para operações que requerem múltiplos valores na pilha
* **Optimização para Método Main**: Tratamento especial para métodos main com apenas uma instrução de retorno
* **Validação de Limites**: Garante que os valores calculados estão dentro dos limites válidos da JVM

## Gestão de Tipos e Descritores

* **Descritores de Tipo JVM**: Conversão automática de tipos OLLIR para descritores bytecode Java
* **Suporte para Arrays de String**: Tratamento especializado para arrays de strings (`[Ljava/lang/String;`)
* **Identificação de Tipos Primitivos**: Reconhecimento e conversão de tipos void, integer e boolean
* **Formatação de Nomes de Classes**: Conversão de nomes com pontos para formato de barra (`/`)
* **Validação de Tipos**: Verificação de consistência entre tipos OLLIR e descritores gerados

## Controlo de Fluxo e Etiquetas

* **Gestão Automática de Etiquetas**: Geração sequencial de etiquetas únicas para estruturas de controlo
* **Comparações Optimizadas**: Implementação eficiente de operações relacionais com geração de etiquetas
* **Saltos Condicionais e Incondicionais**: Suporte completo para instruções `if-else`, ciclos e `goto`
* **Posicionamento de Etiquetas**: Colocação correcta de etiquetas no código gerado para manter a semântica
* **Operações Unárias**: Implementação de negação lógica e aritmética

## Tratamento de Casos Especiais

* **Array Length**: Detecção automática e geração de código para operações `array.length`
* **Instrospção por Reflexão**: Utiliza reflexão Java para analisar propriedades de métodos quando necessário
* **Tratamento de Varargs**: Identificação de métodos varargs através de múltiplas estratégias
* **Validação de Código Gerado**: Verifica se o código Jasmin contém elementos essenciais (classe, métodos, etc.)
* **Recuperação de Erros**: Mecanismos de fallback para garantir geração de código mesmo com problemas parciais
* **Otimização de Operações Aritméticas**: Tratamento especializado para operações como AND, OR, ADD, SUB, MUL, DIV

