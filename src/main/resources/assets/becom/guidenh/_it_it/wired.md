---
navigation:
  title: La rete cablata
  parent: index.md
  position: 80
  icon: becom:telephone
item_ids:
  - becom:telephone:*
  - becom:phone_cable:*
  - gregtech:gt.blockmachines:25000
  - gregtech:gt.blockmachines:25001
  - gregtech:gt.blockmachines:25002
  - gregtech:gt.blockmachines:25003
quest_ids:
  - "IHTr1EMRVx6vQQ7722eirw"
  - "b14IecXxXxuWU8oeGOPcNw"
  - "mXRjCMiFWsyGTG0Id2bbig"
  - "_OpArmItVHW8jB5_z18yig"
  - "_xekW5q3V6-D2yESxW4Bqw"
  - "EwUn7B5YXiqaZtu5vTGc2Q"
  - "ARn2PctkVOe0cqI7997r1Q"
---

# La rete cablata

Il primo livello, a LV: Telefoni uniti da linee su tralicci, senza radio di mezzo.

## Telefono e Cavo telefonico

Il <ItemLink id="becom:telephone" showIcon="left">Telefono</ItemLink> è un blocco. Prende il suo numero di sei cifre
quando lo piazzi, ed è tuo. Clic destro per alzare la cornetta: se a portata c'è un solo altro telefono, squilla subito
quello; altrimenti si apre lo schermo del telefono per comporre. Allontanati di più di un blocco circa, o fai di nuovo
clic destro, e riagganci. Alzare la cornetta di un Telefono che squilla risponde. Accovacciati e fai clic destro per
aprire il suo schermo senza alzare la cornetta, per le impostazioni e le chiamate recenti. Il Telefono suona stretto e
ruvido: le sue chiamate non vanno mai oltre il livello Basso.

Il <ItemLink id="becom:phone_cable" showIcon="left">Cavo telefonico</ItemLink> lo collega a un Terminale di linea e non
perde nulla per strada. Il telefono può stare fino a 32 blocchi di cavo dal terminale, e un terminale prende al massimo
128 blocchi di cavo in tutto, diramazioni comprese.

## Terminale di linea

Il <ItemLink id="gregtech:gt.blockmachines:25003" showIcon="left">Terminale di linea</ItemLink> è dove una linea inizia
e finisce. Va a 4 EU/t di corrente LV e serve l'unico telefono sul suo cavo: con due telefoni sul cavo non ne serve
nessuno, e un telefono sui cavi di due terminali non lo serve nessuno dei due. Ha un solo collegamento: una Testa di
traliccio, oppure un'antenna, una parabola, un ripetitore di portale o un Ponte ME agganciati a lui. Una linea vuole un
terminale a ogni capo.

## Tralicci

Una <ItemLink id="gregtech:gt.blockmachines:25000" showIcon="left">Testa di traliccio</ItemLink> sta su una colonna di
6-16 Steel Frame Box e prende 2 collegamenti. Due tralicci si collegano fino a quattro volte l'altezza del più basso, da
24 a 64 blocchi, se non c'è niente di solido in mezzo. Ogni minuto ogni collegamento viene ricontrollato, e uno ostruito
cade finché non si libera. I tralicci semplici non vogliono corrente e portano la linea anche nei chunk scaricati.

Quando una Testa di traliccio si forma, si collega da sola se a portata c'è esattamente un'altra Testa di traliccio
formata con un posto libero e la linea sgombra. Altrimenti collegale a mano: usa la chiave su una testa, poi sull'altra.
Usa di nuovo la chiave su una coppia collegata per scollegarla. Chiunque può collegarsi a qualsiasi rete; scollegare
possono solo un proprietario di uno dei due capi, la sua squadra GT o un operatore del server.

## Ripetitori e router

Una linea perde 0,08 dB per blocco: si disturba oltre 225 blocchi dal suo ultimo nodo alimentato e cade a 300.

- Una <ItemLink id="gregtech:gt.blockmachines:25001" showIcon="left">Testa ripetitore</ItemLink> è una Testa di
  traliccio che fa ripartire il conto. Prende 4 EU/t di corrente LV da un cavo su qualsiasi lato.
- Una <ItemLink id="gregtech:gt.blockmachines:25002" showIcon="left">Testa router</ItemLink> ha anche 15 collegamenti e
  trasforma le linee in una rete, dove ogni telefono può chiamare qualsiasi altro. Prende 16 EU/t.

A differenza di un traliccio semplice, un ripetitore o un router funziona solo se caricato e alimentato, e uno che non
lo è taglia la linea in quel punto.

## Numeri e contatti

Ogni telefono ha un numero di sei cifre: un Telefono o un Videotelefono da quando viene piazzato, un telefono portatile
da quando qualcuno lo porta con sé la prima volta. Appartiene a chi lo ha piazzato, o a chi lo ha portato per primo. Lo
schermo del telefono ha una tastiera, i contatti elencati per nome del proprietario, e le chiamate recenti e perse. Le
sue impostazioni hanno il non disturbare, il nascondersi dai contatti e una lista dei bloccati, che solo il
proprietario o la sua squadra GT possono cambiare. Chi chiama ha al massimo 5 tentativi al minuto.
