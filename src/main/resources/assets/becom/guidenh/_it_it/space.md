---
navigation:
  title: Lo spazio e le altre dimensioni
  parent: index.md
  position: 40
  icon: becom:satellite
item_ids:
  - becom:satellite:*
  - becom:satellite_rocket:*
  - becom:rocket_radio_receiver:*
  - becom:machine:25010
  - becom:machine:25011
  - becom:machine:25012
  - becom:machine:25014
  - becom:machine:25015
quest_ids:
  - "oNm-cIc3Xoy6hejGu6UM7Q"
  - "78j3bHKHWF6_v27ljQ8cYg"
  - "CW6Vyi4yXYClMf9my2HQKQ"
  - "J3UFmY7aUkyD5jAJI7lDBw"
  - "aEl8W97yVGeFRBy0p4O4ZA"
  - "iphLiVwwUKakj8QR4PH2-w"
  - "Ww6rBsobWMmbyc-M1rYILg"
---

# Lo spazio e le altre dimensioni

## Satelliti

Un <ItemLink id="becom:satellite" showIcon="left">Satellite</ItemLink> va in orbita su un
<ItemLink id="becom:satellite_rocket" showIcon="left">Razzo per satelliti</ItemLink>, da una
<ItemLink id="becom:machine:25010" showIcon="left">Rampa di lancio per satelliti</ItemLink>: un complesso di
lancio di Clean Stainless Steel Machine Casing e Stainless Steel Frame Box. Una rampa 7 per 7 intorno a una fossa di
scarico, con il controller sul bordo anteriore e accanto l'energy hatch e il maintenance hatch; una torre di servizio
dietro la rampa, con un bus d'ingresso per i razzi alla base e due bracci mobili che sporgono sulla rampa; un serbatoio
del propellente accanto, con un portello d'ingresso per il carburante; e i parafulmini sugli angoli anteriori. Un lancio
dura 30 s a 480 EU/t con 1.000 L di carburante, e mette un satellite sopra la dimensione della rampa. Il razzo sta nella
fossa durante il conto alla rovescia e decolla quando finisce. I problemi di manutenzione lo allungano, e qualsiasi
blocco che ferma la luce sopra la fossa mette il lancio in pausa finché non si libera; il vetro no. La sua energy hatch la manda
in overclock come le strutture di GT con una ricetta: ogni overclock è metà del tempo per quattro volte l'energia.

I satelliti scendono dopo circa 100 giorni di Minecraft, un quinto in più o in meno: circa 33 ore di server acceso.
Dormire la notte non li fa scendere prima. Continua a lanciarne.

Lo schermo della rampa dice **Lancio in corso**, con i secondi e una barra di avanzamento, **In pausa** o **Pronta**,
poi i razzi in attesa, il carburante in litri e per quanti lanci basta, e i satelliti di ogni dimensione, con le loro
chiamate e quando scende il prossimo. La sua scheda **Orbita**, accanto a **Lanci**, ha una tabella dei cieli:
**Sopra**, **Su**, **Livello**, **Chiamate** e **Chi lo usa**: parabole, collegamenti dai ricevitori di altri pianeti e
telefoni a cielo aperto. Sotto c'è una previsione del cielo della rampa per i prossimi 60 giorni, se non sale altro,
con il numero sotto cui il suo livello scende, e un avviso in rosso quando succederà: "Scende a Basso tra 29 giorni,
quando ne cadono 3 insieme: il cielo porta allora 26 chiamate, non 36."

## Salti ai satelliti

Con 5 satelliti sopra una dimensione, ogni Telefono cellulare e ogni radio sotto il suo cielo aperto li raggiunge, al
livello Basso. I Telefoni ME li usano solo fuori dalla portata della loro rete ME. Più satelliti danno un segnale più
forte:

| Satelliti | Livello |
| --- | --- |
| 5 | Basso |
| 14 | Medio |
| 37 | Alto |
| 100 | Massimo, e non di più oltre |

Un tetto indebolisce un salto ai satelliti della sua copertura, la pioggia di 3 dB e un temporale di 6 dove cade pioggia
o neve, e sotto terra sparisce. Con soli 5 satelliti il salto sta proprio al limite del livello Basso, quindi una
qualsiasi di queste cose lo taglia; 14 lo tengono al Medio. Le radio a cielo aperto si sentono attraverso i satelliti, a
qualsiasi distanza. Le dimensioni senza cielo, come il Nether, non ne hanno.

Ogni satellite porta 2 chiamate, condivise da tutto ciò che sta sotto quel cielo: telefoni, parabole e ricevitori. 18
satelliti portano 36 chiamate insieme, e la chiamata successiva trova occupato finché una non finisce.

## Parabola satellitare

Una <ItemLink id="becom:machine:25011" showIcon="left">Parabola satellitare</ItemLink> collega la sua rete a
ogni altra parabola della sua dimensione, attraverso i satelliti, e nell'Overworld a ogni pianeta con un Ricevitore
radio per razzi. È una stazione di terra a 384 EU/t: una parabola di Clean Stainless Steel Machine Casing rivolta dritta in
alto, larga 7 al bordo, con un illuminatore tenuto sopra di lei da quattro sostegni di Stainless Steel Frame Box, su un
piedistallo. Il controller sta in un basamento ai suoi piedi, con accanto l'energy hatch, il maintenance hatch e una Line
Hatch. Vuole cielo aperto sopra tutta la parabola. Collega con un cavo la sua Line Hatch a un Router o a una Centrale:
quel bus è la sua dorsale, e il livello del suo cavo ne decide la velocità. La pioggia sulla parabola toglie 3 dB al suo salto, un temporale 6, e
ogni problema di manutenzione 2 dB.

Una parabola porta al massimo 32 chiamate, il doppio per ogni overclock che le danno le sue energy hatch, come nelle
strutture di GT, a quattro volte l'energia; e mai più di quante ne stiano nel livello del suo salto: un salto al Basso
porta 4 KB/s, posto per 2 chiamate, al Medio 16 KB/s, all'Alto 64 e al Massimo 256, a 2 KB/s per chiamata.

Il suo schermo mostra il suo salto in dB, con le tacche di segnale e il suo livello, e quante delle sue chiamate sono in
uso. Poi vengono i satelliti sopra di lei e quante chiamate del cielo sono in uso, **Cielo** libero in verde o
coperto in rosso, **Manutenzione** con i problemi e i dB che costano, e la sua dorsale (**Backhaul**).

## Ricevitore radio per razzi

Metti un <ItemLink id="becom:rocket_radio_receiver" showIcon="left">Ricevitore radio per razzi</ItemLink> nella stiva di
un razzo di Galacticraft fermo su una piattaforma di lancio di Galacticraft, su una luna, un pianeta o una stazione
spaziale. Il razzo diventa lì un'antenna T1, con una portata di circa 64 blocchi, e il collegamento di quel mondo con i
satelliti dell'Overworld, al livello Alto. Porta 4 chiamate insieme. Il razzo ha una stiva solo se è costruito con dei bauli, e conta solo finché
il suo chunk è caricato. Un razzo su cui voli tu arriva come oggetto nella sua Cassa del razzo: piazzalo su una
piattaforma e mettici dentro il ricevitore.

Il collegamento vuole almeno 5 satelliti sopra l'Overworld, e una chiamata che ci passa non va meglio del salto ai
satelliti dal lato dell'Overworld: i telefoni sotto il cielo aperto dell'Overworld, e le reti con una parabola,
raggiungono il pianeta. I pianeti si raggiungono tra loro allo stesso modo, attraverso l'Overworld, con un ricevitore su
ciascuno. Il razzo non prende cavi: per portare dentro la rete cablata di un pianeta, collegala con il cavo alla Line Hatch di
un'antenna a portata del razzo.

## Ripetitore di portale

Un <ItemLink id="becom:machine:25012" showIcon="left">Ripetitore di portale</ItemLink> funziona entro 4
blocchi da un portale del Nether, un portale dell'End, un portale del Twilight Forest o un portale di Personal Space,
Collegalo con il cavo, su qualsiasi faccia, a un Router o a una Centrale della tua rete. Poi attraversa il portale e
metti un ripetitore dall'altra parte, collegato a uno di là: i due si accoppiano da soli, e la coppia unisce le due reti al livello Alto. Il suo
schermo dice **Non accoppiato** finché non succede.

Ne esistono tre livelli, e una coppia lavora al livello del ripetitore più basso:

| Ripetitore | Corrente | Chiamate | Velocità |
| --- | --- | --- | --- |
| <ItemLink id="becom:machine:25012" showIcon="left">Ripetitore di portale</ItemLink> | 32 EU/t, MV | 16 | 64 KB/s |
| <ItemLink id="becom:machine:25014" showIcon="left">Ripetitore di portale (HV)</ItemLink> | 128 EU/t, HV | 32 | 128 KB/s |
| <ItemLink id="becom:machine:25015" showIcon="left">Ripetitore di portale (EV)</ItemLink> | 512 EU/t, EV | 64 | 256 KB/s |

I due ripetitori devono stare ai due lati dello stesso portale. Attraverso il Nether, quello lontano sta dove porta il
portale, a un ottavo della distanza dal centro del mondo; attraverso il Twilight Forest, vicino allo stesso punto. Un
ripetitore di Personal Space nell'Overworld deve stare vicino a un portale che porta nella dimensione del ripetitore
lontano.

## Chiamate tra dimensioni

- L'Overworld e un pianeta: attraverso i satelliti, verso un Ricevitore radio per razzi lì.
- Due pianeti: attraverso i satelliti dell'Overworld, con un ricevitore su ciascuno.
- L'Overworld e il Nether, l'End, il Twilight Forest o una dimensione personale: attraverso una coppia di Ripetitori di
  portale.
- Catene più lunghe combinano queste, dal Nether attraverso l'Overworld fino a un pianeta.

Ogni altra dimensione per ora non si raggiunge. I canali radio restano nella loro dimensione.
