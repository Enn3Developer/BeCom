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
  - becom:machine:25001
  - becom:machine:25002
  - becom:machine:25003
  - becom:machine:25013
  - becom:machine:25016
  - becom:machine:25017
  - becom:machine:25018
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

Il primo livello, da LV: Telefoni sul cavo di un edificio, e cavo tra un edificio e l'altro. Il filo è digitale: non
perde nulla, con la pioggia o col sole, e quello che ti dà è spazio per le chiamate.

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

Ci sono tre cavi. Ognuno si unisce solo al cavo del suo stesso livello, e un suo bus porta la sua velocità fino alla
sua portata, e niente oltre:

| Cavo | Livello | Velocità | Bus più lungo |
| --- | --- | --- | --- |
| <ItemLink id="becom:phone_cable:0" showIcon="left">Cavo telefonico</ItemLink> | LV | 16 KB/s | 128 blocchi |
| <ItemLink id="becom:phone_cable:1" showIcon="left">Cavo coassiale</ItemLink> | MV | 64 KB/s | 512 blocchi |
| <ItemLink id="becom:phone_cable:2" showIcon="left">Cavo in fibra</ItemLink> | HV | 256 KB/s | 4096 blocchi |

Una chiamata vocale prende 2 KB/s, una videochiamata 8 KB/s. Il cavo si unisce come quelli di GT: piazzato contro un
blocco, si unisce a quel blocco, e le tronchesi GT ne aprono e chiudono i lati. Apre un lato solo verso qualcosa che lo
prende, e l'acqua non lo porta via.

## Bus

Un bus è una tratta di cavo di un solo livello, unito lato a lato, con tutto ciò che raggiunge: telefoni, router e
gateway. Funziona solo con un router in funzione sopra, e solo fino al bus più lungo del suo cavo; oltre non porta
niente. Tutto quel che sta su un bus ne divide la velocità. Una chiamata tra due telefoni sullo stesso bus lo
attraversa due volte, verso il router e di ritorno.

Un router chiude un bus: il cavo su un'altra sua faccia è un altro bus, che si conta di nuovo da zero. Un bus tra due
router li collega, così le chiamate passano dall'uno all'altro; è così che una linea arriva all'edificio dopo. Oltre
quanto arriva un bus di fibra, le chiamate vanno via radio, tramite le antenne, o tramite una rete ME.

## Router

Un <ItemLink id="becom:machine:25003" showIcon="left">Router</ItemLink> va a 4 EU/t e smista le chiamate di ogni bus
che lo raggiunge. Prende cavo fino al suo livello, su ogni faccia: quello LV il Cavo telefonico e porta 8 chiamate
insieme, il <ItemLink id="becom:machine:25002" showIcon="left">Router (MV)</ItemLink> fino al coassiale e 32 chiamate,
il <ItemLink id="becom:machine:25013" showIcon="left">Router (HV)</ItemLink> fino alla fibra e 128 chiamate.

Cliccalo col tasto destro per la sua schermata: le chiamate che porta, una riga per ogni bus che lo raggiunge, col suo
cavo, la sua lunghezza rispetto al bus più lungo e quanto porta, poi quel che sta su quei bus: ogni telefono per numero
con quel che sta facendo, poi i router e i gateway. WAILA mostra le stesse righe. La Parabola satellitare, la Rampa di
lancio per satelliti, il Ponte ME e il Ripetitore di portale mostrano in cima una riga di stato, verde se la macchina
funziona e rossa col motivo se non funziona, poi righe con un'etichetta, come chiamate e dorsale. Quel che non va è
elencato sotto le righe, dopo una linea di separazione: in rosso, o in arancione se è un avviso.

## Line Hatch

Una struttura si collega ai bus tramite le sue Line Hatch:
<ItemLink id="becom:machine:25016" showIcon="left">Line Hatch (LV)</ItemLink>,
<ItemLink id="becom:machine:25017" showIcon="left">Line Hatch (MV)</ItemLink> e
<ItemLink id="becom:machine:25018" showIcon="left">Line Hatch (HV)</ItemLink>. Ognuna prende un cavo, sulla sua faccia
frontale, fino al suo livello, e collega quel bus alla struttura di cui fa parte: una Centrale o un'antenna come suo
router, una Parabola satellitare come sua dorsale. La sua spia è accesa mentre quella struttura funziona. Fuori da una
struttura formata, non collega niente.

## Centrale

Una <ItemLink id="becom:machine:25001" showIcon="left">Centrale</ItemLink> è un grande router: un edificio di LV
Machine Casing, tre blocchi per lato, con il controller al centro della facciata, un Energy Hatch, un Input Bus e
almeno una Line Hatch. Consuma 64 EU/t e smista le chiamate tra tutti i bus collegati alle sue Line Hatch.

Senza <ItemLink id="becom:switching_rack:0" showIcon="left">Rack di commutazione (LV)</ItemLink> non smista niente.
Mettine fino a 8 nel suo Input Bus: ogni rack LV smista 8 chiamate insieme, MV 32, HV 128. Quando ogni strada tra due
telefoni è piena, chi chiama dopo sente occupato. Le chiamate già in corso tengono la loro strada.

Clic destro sul suo controller per il suo schermo, al posto del terminale di GT: se funziona, poi le sue chiamate,
"9 di 72", con una barra, i suoi dati in KB/s su quanti percorsi, e quanti numeri raggiunge tramite quante centrali.
Una fila di 8 slot mostra i suoi rack: uno pieno con la tensione e le chiamate del rack, uno libero come un "+"
tratteggiato. **Collegato a** elenca tutto ciò che sta sui bus collegati alle sue Line Hatch: le antenne come
"Antenna T3, backhaul", le altre Centrali per coordinate, i Router per punto cardinale, i gateway per nome, e i
dispositivi con un numero per tipo e numero. Ogni riga ha il cavo del suo bus e una barra di quanto porta, o in rosso
perché non porta niente: "310 blocchi: una tratta di Cavo telefonico arriva al massimo a 128".

## Numeri e contatti

Ogni telefono ha un numero di sei cifre: un Telefono o un Videotelefono da quando viene piazzato, un telefono portatile
da quando qualcuno lo porta con sé la prima volta. Appartiene a chi lo ha piazzato, o a chi lo ha portato per primo. Lo
schermo di un Telefono ha una tastiera, la chiamata rapida e le chiamate recenti e perse; quello di un telefono
portatile ha anche i contatti, elencati per nome del proprietario. Le loro impostazioni hanno il non disturbare, il
nascondersi dai contatti e una lista dei bloccati, che solo il proprietario, la sua squadra o un operatore del server
possono cambiare. Chi chiama ha al massimo 5 tentativi al minuto.

Le squadre sono quelle di ServerUtilities, che si creano con il suo pulsante My Team accanto all'inventario: contano il
proprietario della squadra, i moderatori e i membri, non gli alleati. Su un server dedicato non conta la squadra
"singleplayer" che un mondo giocato prima da soli si porta dietro, perché lì chiunque può entrarci. Senza
ServerUtilities, possono cambiarle solo il proprietario e gli operatori.
