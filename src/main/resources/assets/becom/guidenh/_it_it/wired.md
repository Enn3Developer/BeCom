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
  - gregtech:gt.blockmachines:25000
  - gregtech:gt.blockmachines:25001
  - gregtech:gt.blockmachines:25002
  - gregtech:gt.blockmachines:25003
  - gregtech:gt.blockmachines:25013
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

Ci sono tre cavi, e ognuno porta la sua velocità su una tratta fino alla sua portata, e niente oltre:

| Cavo | Livello | Velocità | Tratta più lunga |
| --- | --- | --- | --- |
| <ItemLink id="becom:phone_cable:0" showIcon="left">Cavo telefonico</ItemLink> | LV | 16 KB/s | 128 blocchi |
| <ItemLink id="becom:phone_cable:1" showIcon="left">Cavo coassiale</ItemLink> | MV | 64 KB/s | 512 blocchi |
| <ItemLink id="becom:phone_cable:2" showIcon="left">Cavo in fibra</ItemLink> | HV | 256 KB/s | 4096 blocchi |

Una chiamata vocale prende 2 KB/s, una videochiamata 8 KB/s. I cavi di ogni livello si uniscono tra loro, e la tratta
di un dispositivo vale quanto il cavo più scarso sulla sua strada.

## Terminale di linea

Un <ItemLink id="gregtech:gt.blockmachines:25003" showIcon="left">Terminale di linea</ItemLink> è dove parte la linea
di un edificio. Va a 4 EU/t e serve ogni dispositivo sul suo cavo, dal più vicino, quanti ne ha di porte: 4 quello LV,
8 il <ItemLink id="gregtech:gt.blockmachines:25002" showIcon="left">Terminale di linea (MV)</ItemLink>, 16 il
<ItemLink id="gregtech:gt.blockmachines:25013" showIcon="left">Terminale di linea (HV)</ItemLink>. I dispositivi più
lontani restano senza linea, e uno sul cavo di due terminali prende il più vicino. Tutti dividono il suo unico
collegamento: un traliccio, o un'antenna, una parabola, un ripetitore di portale o un ME Bridge collegati a lui.

## Tralicci e campate

Una <ItemLink id="gregtech:gt.blockmachines:25000" showIcon="left">Testa di traliccio</ItemLink> sta in cima a un palo
telefonico: da 6 a 16 Wood Frame Box, con una traversa di altre 4 subito sotto la testa. Ha 2 collegamenti. Due
tralicci si collegano fino a 4 volte l'altezza del più basso, da 24 a 64 blocchi, se in mezzo non c'è niente di
solido. Ogni minuto ogni collegamento viene ricontrollato, e uno bloccato cade finché non torna libero. I tralicci non
vogliono corrente e portano la linea anche nei chunk scaricati.

Per tendere una campata metti del cavo nella barra rapida, poi usa la chiave su un nodo e sull'altro: sale il primo
cavo della barra, uno ogni 4 blocchi di campata. Di nuovo con del cavo ne aggiungi un altro: una campata ne regge fino
a 4, e le loro velocità si sommano. Quando è piena, un cavo migliore prende il posto del più scarso, che ti torna
indietro. Senza cavo nella barra rapida la scolleghi, e ti tornano tutti i cavi. Rompere un traliccio fa cadere i cavi
delle sue campate.

Una tratta è il filo tra due nodi alimentati: terminali, Centrali, antenne e gateway. Porta quanto la sua campata più
sottile, contando solo i cavi la cui portata copre tutta la tratta. Chiunque può collegarsi a qualsiasi rete; scollegare
possono solo un proprietario di uno dei due capi, la sua squadra o un operatore del server. Le squadre sono quelle di
ServerUtilities, che si creano con il suo pulsante My Team accanto all'inventario: contano il proprietario della
squadra, i moderatori e i membri, non gli alleati. Su un server dedicato non conta la squadra "singleplayer" che un
mondo giocato prima da soli si porta dietro, perché lì chiunque può entrarci. Senza ServerUtilities, possono solo il
proprietario e gli operatori.

## Centrale

Una linea di tralicci unisce due terminali. Per unirne di più, collegali a una
<ItemLink id="gregtech:gt.blockmachines:25001" showIcon="left">Centrale</ItemLink>: un edificio di LV Machine Casing,
tre blocchi per lato, con il controller al centro della facciata, un Energy Hatch e un Input Bus. Consuma 64 EU/t e
smista le chiamate tra fino a 15 linee.

Senza <ItemLink id="becom:switching_rack:0" showIcon="left">Rack di commutazione (LV)</ItemLink> non smista niente.
Mettine fino a 8 nel suo Input Bus: ogni rack LV smista 8 chiamate insieme, MV 32, HV 128. Quando ogni strada tra due
telefoni è piena, chi chiama dopo sente occupato. Le chiamate già in corso tengono la loro strada.

A differenza di un traliccio, una Centrale funziona solo se è caricata e alimentata.

## Numeri e contatti

Ogni telefono ha un numero di sei cifre: un Telefono o un Videotelefono da quando viene piazzato, un telefono portatile
da quando qualcuno lo porta con sé la prima volta. Appartiene a chi lo ha piazzato, o a chi lo ha portato per primo. Lo
schermo del telefono ha una tastiera, i contatti elencati per nome del proprietario, e le chiamate recenti e perse. Le
sue impostazioni hanno il non disturbare, il nascondersi dai contatti e una lista dei bloccati, che solo il
proprietario, la sua squadra o un operatore del server possono cambiare. Chi chiama ha al massimo 5 tentativi al
minuto.
