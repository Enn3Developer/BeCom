---
navigation:
  title: La rete cablata
  parent: index.md
  position: 80
  icon: becom:telephone
item_ids:
  - becom:telephone:*
  - becom:phone_cable:*
  - becom:switching_rack:*
  - becom:machine:25000
  - becom:machine:25001
  - becom:machine:25002
  - becom:machine:25003
  - becom:machine:25013
quest_ids:
  - "IHTr1EMRVx6vQQ7722eirw"
  - "b14IecXxXxuWU8oeGOPcNw"
  - "mXRjCMiFWsyGTG0Id2bbig"
  - "_OpArmItVHW8jB5_z18yig"
  - "_xekW5q3V6-D2yESxW4Bqw"
  - "EXDKXjgxXWGVm2xGizbPmw"
  - "Dd10sdc7U-eNKPKXD7E0Ig"
---

# La rete cablata

Il primo livello, da LV: Telefoni sul cavo di un edificio, e linee su tralicci tra un edificio e l'altro. Il filo è
digitale: non perde nulla, con la pioggia o col sole, e quello che ti dà è spazio per le chiamate.

## Telefono e cavo

Il <ItemLink id="becom:telephone" showIcon="left">Telefono</ItemLink> è un blocco. Prende il suo numero di sei cifre
quando lo piazzi, ed è tuo. Clic destro per alzare la cornetta: se a portata c'è un solo altro telefono, squilla subito
quello; altrimenti si apre lo schermo del telefono per comporre. Allontanati di più di un blocco circa, o fai di nuovo
clic destro, e riagganci. Alzare la cornetta di un Telefono che squilla risponde. Accovacciati e fai clic destro per
aprire il suo schermo senza alzare la cornetta, per le impostazioni e le chiamate recenti. Il Telefono suona stretto e
ruvido: le sue chiamate non vanno mai oltre il livello Basso.

Il suo schermo ha un display, una tastiera e quattro tasti di chiamata rapida, da M1 a M4. Clicca un tasto di chiamata
rapida per chiamare il numero salvato lì; clic destro per salvarci il numero scritto sulla tastiera, o per svuotarlo se
non hai scritto niente. Solo il proprietario, la sua squadra o un operatore del server possono salvarne uno. Sotto di
loro, **Richiama** chiama l'ultimo numero che hai chiamato, **Chiamate** scorre le chiamate recenti, con una spia
arancione per quelle perse, e **Menu** scorre **Non disturbare**, **Non in elenco** e la lista dei bloccati. Su una
chiamata recente, **Chiama** ritelefona a quel numero e **C** due volte blocca chi era dall'altra parte; nel menu,
**Chiama** cambia l'impostazione mostrata e **C** sblocca il giocatore mostrato.

Ci sono tre cavi, e ognuno porta la sua velocità su una tratta fino alla sua portata, e niente oltre:

| Cavo | Livello | Velocità | Tratta più lunga |
| --- | --- | --- | --- |
| <ItemLink id="becom:phone_cable:0" showIcon="left">Cavo telefonico</ItemLink> | LV | 16 KB/s | 128 blocchi |
| <ItemLink id="becom:phone_cable:1" showIcon="left">Cavo coassiale</ItemLink> | MV | 64 KB/s | 512 blocchi |
| <ItemLink id="becom:phone_cable:2" showIcon="left">Cavo in fibra</ItemLink> | HV | 256 KB/s | 4096 blocchi |

Una chiamata vocale prende 2 KB/s, una videochiamata 8 KB/s. I cavi di ogni livello si uniscono tra loro, e la tratta
di un dispositivo vale quanto il cavo più scarso sulla sua strada.

## Router

Un <ItemLink id="becom:machine:25003" showIcon="left">Router</ItemLink> è dove parte la linea
di un edificio. Va a 4 EU/t e serve ogni dispositivo sul suo cavo, dal più vicino, quanti ne ha di porte: 4 quello LV,
8 il <ItemLink id="becom:machine:25002" showIcon="left">Router (MV)</ItemLink>, 16 il
<ItemLink id="becom:machine:25013" showIcon="left">Router (HV)</ItemLink>. I dispositivi più
lontani restano senza linea, e uno sul cavo di due router prende il più vicino. Quando i dispositivi che vogliono una
linea sono più delle porte, WAILA lo dice in rosso, "9 dispositivi su 8 porte", con "Il più lontano resta senza linea."
Tutti dividono il suo unico collegamento: una campata verso un traliccio, o un'antenna collegata a lui.

Cliccalo col tasto destro per la sua schermata: il collegamento e quanto ne usano le chiamate, e una riga per ogni
porta, col dispositivo, la tratta del suo cavo e cosa sta facendo. Un dispositivo che non può servire dice perché:
nessuna porta libera, una tratta più lunga di quanto arriva il suo cavo, o un router più vicino. La Parabola
satellitare, la Rampa di lancio per satelliti, il Ponte ME e il Ripetitore di portale mostrano in cima una riga di
stato, verde se la macchina funziona e rossa col motivo se non funziona, poi righe con un'etichetta, come chiamate e
dorsale. Quel che non va è elencato sotto le righe, dopo una linea di separazione: in rosso, o in arancione se è un
avviso.

## Tralicci e campate

Una <ItemLink id="becom:machine:25000" showIcon="left">Testa di traliccio</ItemLink> sta in cima a un palo
telefonico: da 6 a 16 Wood Frame Box, con una traversa di altre 4 subito sotto la testa. Ha 2 collegamenti. Due
tralicci si collegano fino a 4 volte l'altezza del più basso, da 24 a 64 blocchi, se in mezzo non c'è niente di
solido. Ogni minuto ogni collegamento viene ricontrollato, e uno bloccato cade finché non torna libero. I tralicci non
vogliono corrente e portano la linea anche nei chunk scaricati.

Per tendere una campata metti del cavo nella barra rapida, poi usa la chiave su un nodo e sull'altro: sale il primo
cavo della barra, uno ogni 4 blocchi di campata. Di nuovo con del cavo ne aggiungi un altro: una campata ne regge fino
a 4, e le loro velocità si sommano. Quando è piena, un cavo migliore prende il posto del più scarso, che ti torna
indietro. Senza cavo nella barra rapida la scolleghi, e ti tornano tutti i cavi. Rompere un traliccio fa cadere i cavi
delle sue campate.

Una tratta è il filo tra due nodi alimentati: router, Centrali, antenne e gateway. Porta quanto la sua campata più
sottile, contando solo i cavi la cui portata copre tutta la tratta. WAILA su un traliccio mostra l'altezza del suo palo,
ogni campata per punto cardinale con i suoi cavi e la sua lunghezza, e la lunghezza della tratta rispetto alla portata
del cavo migliore teso su ogni campata: "Tratta 600 di 4096". Chiunque può collegarsi a qualsiasi rete; scollegare
possono solo un proprietario di uno dei due capi, la sua squadra o un operatore del server. Le squadre sono quelle di
ServerUtilities, che si creano con il suo pulsante My Team accanto all'inventario: contano il proprietario della
squadra, i moderatori e i membri, non gli alleati. Su un server dedicato non conta la squadra "singleplayer" che un
mondo giocato prima da soli si porta dietro, perché lì chiunque può entrarci. Senza ServerUtilities, possono solo il
proprietario e gli operatori.

## Centrale

Una linea di tralicci unisce due router. Per unirne di più, collegali a una
<ItemLink id="becom:machine:25001" showIcon="left">Centrale</ItemLink>: un edificio di LV Machine Casing,
tre blocchi per lato, con il controller al centro della facciata, un Energy Hatch e un Input Bus. Consuma 64 EU/t e
smista le chiamate tra fino a 15 linee.

Senza <ItemLink id="becom:switching_rack:0" showIcon="left">Rack di commutazione (LV)</ItemLink> non smista niente.
Mettine fino a 8 nel suo Input Bus: ogni rack LV smista 8 chiamate insieme, MV 32, HV 128. Quando ogni strada tra due
telefoni è piena, chi chiama dopo sente occupato. Le chiamate già in corso tengono la loro strada.

Clic destro sul suo controller per il suo schermo, al posto del terminale di GT: se funziona, poi le sue chiamate,
"9 di 72", con una barra, i suoi dati in KB/s su quanti percorsi, e quanti numeri raggiunge tramite quante centrali.
Una fila di 8 slot mostra i suoi rack: uno pieno con la tensione e le chiamate del rack, uno libero come un "+"
tratteggiato. **Collegato a** elenca ogni tratta di filo sui tralicci e tutto ciò che sta sul suo cavo: le antenne come
"Antenna T3, backhaul", le altre Centrali per coordinate, i Router per punto cardinale, i gateway per
nome, e i dispositivi con un numero per tipo e numero. Ogni riga ha i cavi del fascio e una barra di quanto portano, o
in rosso perché la tratta non porta niente: "310 blocchi: una tratta di Cavo telefonico arriva al massimo a 128".

A differenza di un traliccio, una Centrale funziona solo se è caricata e alimentata.

## Numeri e contatti

Ogni telefono ha un numero di sei cifre: un Telefono o un Videotelefono da quando viene piazzato, un telefono portatile
da quando qualcuno lo porta con sé la prima volta. Appartiene a chi lo ha piazzato, o a chi lo ha portato per primo. Lo
schermo di un Telefono ha una tastiera, la chiamata rapida e le chiamate recenti e perse; quello di un telefono
portatile ha anche i contatti, elencati per nome del proprietario. Le loro impostazioni hanno il non disturbare, il
nascondersi dai contatti e una lista dei bloccati, che solo il proprietario, la sua squadra o un operatore del server
possono cambiare. Chi chiama ha al massimo 5 tentativi al minuto.
