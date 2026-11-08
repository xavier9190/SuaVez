# Sua Vez — Rodízio justo de tarefas domésticas

**Contexto:** projeto final de Programação para Dispositivos Móveis, Engenharia de Software, IFPE Campus Belo Jardim.

**Equipe:** Maria Letícia, Caio Vinícius e Pedro Xavier.

## 1. Visão geral

Sua Vez é um aplicativo Android que divide as tarefas da casa entre os moradores de forma justa, levando em conta o esforço de cada tarefa, a preferência de cada pessoa, o quanto cada um já contribuiu e a cota semanal de pontos que cabe a cada morador.

Os moradores entram em uma mesma "casa" dentro do app, enxergam os mesmos dados e acompanham um placar de equilíbrio mensal. Cada um vê quanto falta para atingir a sua cota da semana. O que ninguém escolher é distribuído pelo próprio app.

## 2. Problema

Quem divide a casa costuma discutir sobre quem faz mais, e a sensação de injustiça gera conflito e desgaste na convivência. Três causas explicam isso:

- **Esforço ignorado:** checklists comuns contam só a quantidade de tarefas. Lavar o banheiro e tirar o lixo valem o mesmo.
- **Trabalho invisível:** comprar sabão ou pagar a conta de luz não aparece em nenhuma lista, mas dá trabalho.
- **Preferências ignoradas:** cada morador tem tarefas que tolera melhor ou pior, e isso raramente entra na divisão.

Na raiz, falta uma medida de justiça aceita por todos e uma meta clara de quanto cada um deve fazer. O app existe para oferecer essa medida de forma transparente.

| Item | Definição |
|---|---|
| Plataforma | Android |
| Linguagem | Kotlin |
| Público | Moradores de residências compartilhadas |
| Dados | Compartilhados e sincronizados entre os moradores (Firebase) |

O problema foi observado na experiência de moradia compartilhada da própria equipe e ainda não foi validado em pesquisa com outros grupos.

## 3. Público e benefícios

O público são moradores de repúblicas, apartamentos divididos e famílias pequenas. Dois perfis orientam o desenho:

- **Quem organiza:** cria a casa, cansou de cobrar os outros e quer um critério neutro que decida no lugar dela.
- **Quem contribui sem perceber:** não age de má-fé, só não enxerga o trabalho alheio. Precisa de um placar e de uma meta que mostrem sua contribuição sem acusar.

**Benefícios esperados:** divisão mais justa e transparente, menos conflitos e menos cobrança entre moradores.

## 4. Solução

O Sua Vez se apoia em quatro ideias:

- **Esforço:** cada tarefa vale pontos conforme tempo e desgaste.
- **Preferência:** cada morador marca, por tarefa, se odeia, tanto faz ou gosta.
- **Meta:** cada morador tem uma cota semanal de pontos, calculada dividindo o total de pontos das tarefas da semana pelo número de moradores.
- **Equilíbrio:** a distribuição considera quanto falta para cada um chegar na cota e, em caso de empate, os pontos que cada um já acumulou no mês.

Compras e contas entram como tarefas comuns, com peso e pontos, o que torna visível o trabalho que normalmente não aparece.

### Exemplo

Uma casa com 3 moradores e tarefas da semana que somam 30 pontos tem cota de 10 pontos por morador. Cada morador vê na tela de tarefas quanto já escolheu e quanto falta para chegar em 10.

### Fluxo de uso

1. Um morador cria a casa e compartilha o código de convite.
2. Os demais entram pelo código e marcam suas preferências.
3. A casa confirma a lista de tarefas e seus pesos, a partir de valores padrão.
4. No início da semana, o app calcula a cota de cada morador e as tarefas aparecem abertas. Cada morador escolhe as que quiser e acompanha uma barra de progresso, por exemplo "6 de 10 pontos".
5. Ao ultrapassar a cota, o app apenas avisa que o morador já cumpriu a sua parte. Não há bloqueio: ele pode continuar escolhendo.
6. As tarefas que ninguém escolheu são distribuídas pelo botão "Distribuir pendentes".
7. Cada morador marca suas tarefas como feitas e recebe os pontos.
8. O placar mostra a contribuição de cada um no mês e reinicia a cada mês.

## 5. Regras do sistema

**Tarefas.** Toda tarefa tem título, peso em pontos e categoria: limpeza, compras ou contas.

**Preferências.** Três níveis por tarefa: Odeio, Tanto faz e Gosto.

**Cota semanal.** A cota é a soma dos pesos de todas as tarefas da semana dividida pelo número de moradores:

```
cota = soma(pesos das tarefas da semana) / numero_de_moradores
```

A divisão exata é rara, por isso a meta é chegar perto da cota. A tolerância é de ±10% da cota (arredondada para o inteiro mais próximo). Exemplo: com cota 10, a faixa aceita é de 9 a 11 pontos.

**Aviso de cota.** Quando um morador atinge ou ultrapassa a cota na escolha livre, o app mostra um aviso informando que ele já cumpriu a sua parte. O aviso é apenas informativo e não impede novas escolhas.

**Distribuição.** As tarefas pendentes são ordenadas da mais pesada para a mais leve. Cada uma vai para quem está mais longe da cota e não a odeia:

```
cota = soma(pesos das tarefas da semana) / numero_de_moradores

para cada tarefa pendente (maior peso primeiro):
    candidatos = moradores que não marcaram "Odeio" nessa tarefa
                 E cuja soma atual + peso da tarefa <= cota + tolerância
    se não há candidatos: candidatos = moradores que não marcaram "Odeio"
    se ainda não há candidatos: candidatos = todos os moradores
    escolhido = candidato com maior "falta"
                (cota - pontos atribuídos ou escolhidos na semana)
    desempate: quem marcou "Gosto", depois quem tem menos pontos no mês,
               depois sorteio
    atribuir a tarefa ao escolhido
```

Distribuir as tarefas pesadas primeiro e deixar as leves para o ajuste final é uma heurística gulosa que ajuda a chegar mais perto da cota. A restrição de cota vale apenas para a distribuição automática; na escolha livre o morador não é limitado.

**Placar.** Soma os pontos das tarefas concluídas no mês por morador. Se alguém fica abaixo ou acima da cota em uma semana, o placar mensal ajuda a compensar nas semanas seguintes, pois entra como critério de desempate.

## 6. Funcionalidades

| Funcionalidade | Descrição | Prioridade |
|---|---|---|
| Login e cadastro | Acesso com conta de usuário | Essencial |
| Casa e convite | Criar a casa e entrar com código de convite | Essencial |
| Tarefas e pesos | Cadastrar tarefas com peso e categoria | Essencial |
| Preferências | Marcar Odeio, Tanto faz ou Gosto por tarefa | Essencial |
| Cota semanal e progresso | Calcular a cota de cada morador e mostrar barra de progresso com os pontos escolhidos | Essencial |
| Aviso de cota atingida | Avisar, sem bloquear, quando o morador atingir ou passar a cota | Essencial |
| Escolha e conclusão | Escolher tarefas da semana e marcá-las como feitas | Essencial |
| Distribuição | Atribuir as tarefas pendentes pelas regras acima, considerando a cota | Essencial |
| Placar mensal | Pontos do mês por morador | Essencial |
| Gráfico do placar | Barras com a contribuição de cada morador | Desejável |
| Regras de segurança | Acesso aos dados restrito aos moradores da casa | Desejável |
| Lembretes | Notificação de tarefas pendentes | Futuro |
| Histórico | Consulta a semanas e meses anteriores | Futuro |
| Confirmação de conclusão | Outro morador confirma que a tarefa foi feita | Futuro |
| Troca de tarefas | Moradores trocam tarefas entre si | Futuro |
| Recompensas | Prêmios por pontos | Fora de escopo |
| Versão iOS | Aplicativo para iPhone | Fora de escopo |

## 7. Telas e navegação

O app tem uma Activity principal e um Fragment por tela, ligados pelo Navigation Component. Uma barra inferior dá acesso a Tarefas, Preferências, Placar e Casa.

| Tela | Função |
|---|---|
| Login e cadastro | Entrada do usuário |
| Casa | Criar a casa ou entrar com código |
| Tarefas da semana | Tarefas abertas, minhas e concluídas, com barra de progresso da cota e aviso ao atingi-la |
| Preferências | Lista de tarefas com seletor de três níveis |
| Placar | Pontos do mês por morador |
| Configuração da casa | Tarefas, pesos e código de convite |

## 8. Arquitetura e tecnologias

| Camada | Tecnologia | Papel |
|---|---|---|
| Aplicativo | Kotlin, Activity + Fragments, Navigation Component | Interface e navegação |
| Autenticação | Firebase Authentication | Login e identificação do morador |
| Dados | Cloud Firestore | Casas, moradores, tarefas, preferências e atribuições, sincronizados entre todos |
| Código | Git (GitHub) | Versionamento e entrega |

O Firestore é adequado porque todos os moradores precisam ver os mesmos dados ao mesmo tempo.

A cota semanal é calculada na hora a partir das tarefas cadastradas e do número de moradores, por isso não exige novo campo no banco. Se a equipe quiser guardar a cota de cada semana, pode adicionar `houses/{houseId}/weeks/{semana}` com o campo `cota`.

**Estrutura dos dados:**

```
houses/{houseId}
    nome, codigoConvite, criadaEm

houses/{houseId}/members/{uid}
    nome, pontosMes

houses/{houseId}/tasks/{taskId}
    titulo, peso, categoria

houses/{houseId}/preferences/{uid_taskId}
    uid, taskId, nivel        # odeio | tanto_faz | gosto

houses/{houseId}/assignments/{id}
    taskId, uid, semana, status, concluidaEm, pontos
```

## 9. Etapas de desenvolvimento

1. **Estrutura:** Activity, Fragments e navegação, com dados fictícios.
2. **Persistência:** casas, tarefas, preferências e conclusões gravadas no Firestore.
3. **Autenticação e convivência:** login, vínculo dos dados ao usuário e vários moradores na mesma casa.
4. **Regras e acabamento:** cota semanal, distribuição, placar, regras de segurança e documentação do repositório.

## 10. Soluções existentes e diferencial

A análise usa descrições públicas dos aplicativos, sem teste prático.

| Aplicativo | Foco | Destaque |
|---|---|---|
| Tody | Rotina de limpeza por cômodo | Mostra tarefas atrasadas por ambiente |
| Sweepy | Planejamento da faxina | Pontos por dificuldade e distribuição conforme o tempo disponível |
| OurHome | Famílias com crianças | Pontos, recompensas, listas compartilhadas e lembretes |
| Flatastic | Repúblicas e famílias | Lembra tarefas e mostra o que falta comprar |
| Cozi e FamilyWall | Organização familiar geral | Calendário, tarefas e listas de compras |

Pontos por esforço e placar já existem nesses aplicativos. Nas soluções analisadas não identificamos a combinação que o Sua Vez propõe: preferência individual somada a uma cota semanal por morador e ao equilíbrio pelo placar acumulado em uma distribuição automática. O posicionamento é o de um árbitro neutro para adultos que dividem a casa, e não o de um app de recompensas para crianças.

## 11. Decisões de projeto

| Decisão | Alternativas consideradas | Motivo |
|---|---|---|
| Escolha livre com distribuição automática do restante | Tudo automático; rodízio fixo; troca entre moradores | Une autonomia e justiça. O rodízio fixo ignora esforço e preferência |
| Cota semanal por morador | Equilibrar só pelo placar mensal | Uma meta explícita e visível reduz a discussão sobre quem faz mais e deixa claro o que falta para cada um |
| Aviso ao atingir a cota, sem bloqueio | Bloquear a escolha após a cota | Mantém a autonomia do morador e evita travar quem quer ajudar mais. A cota orienta, não impede |
| Tolerância de ±10% da cota | Divisão exata | A divisão exata raramente é possível com pesos inteiros |
| Distribuição acionada por botão | Execução agendada no servidor | Não depende de função agendada em nuvem e pode ser executada na hora |
| Convite por código | Link de convite | Evita depender de links diretos entre aplicativos |
| Preferência em três níveis | Dois níveis | "Gosto" dá ao algoritmo um sinal positivo útil |
| Pontos do mês, reiniciados mensalmente | Pontos acumulados sem fim | Uma semana ruim não persegue ninguém para sempre |
| Distribuir da tarefa mais pesada para a mais leve | Ordem de cadastro; ordem aleatória | As tarefas pesadas primeiro e as leves no final ajustam a conta e aproximam cada morador da cota. É uma heurística gulosa para o problema de partição |

## 12. Riscos e limites

- **Pesos contestados:** se os moradores discordarem dos pontos, o placar vira nova fonte de conflito. Os pesos são definidos em conjunto pela casa.
- **Cota irreal:** se houver poucas tarefas cadastradas ou tarefas muito pesadas, a cota fica fora da realidade. A casa precisa ajustar a lista e os pesos.
- **Aviso ignorado:** como o app só avisa e não bloqueia, um morador pode ultrapassar a cota e outro ficar abaixo. O placar mensal e a distribuição das pendentes compensam parte disso.
- **Divisão não exata:** com pesos como 5, 3 e 2, nem sempre é possível fechar a cota de todos. Por isso existe a tolerância.
- **Uso parcial:** o app só funciona quando todos os moradores entram. É uma limitação assumida.
- **Preferências exageradas:** marcar "Odeio" em tudo enfraquece a distribuição. Pode-se limitar quantas tarefas cada morador marca assim.
- **Conclusão sem verificação:** a tarefa vale pontos assim que o morador a marca como feita.
- **Problema não validado externamente:** a necessidade vem da experiência da equipe e pode ser confirmada com pesquisa a outros grupos de moradores.
