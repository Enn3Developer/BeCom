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
  - gregtech:gt.blockmachines:25010
  - gregtech:gt.blockmachines:25011
  - gregtech:gt.blockmachines:25012
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
<ItemLink id="gregtech:gt.blockmachines:25010" showIcon="left">Rampa di lancio per satelliti</ItemLink>: un pavimento 5
per 5 di Clean Stainless Steel Machine Casing a cielo aperto, con un bus d'ingresso per i razzi, un portello d'ingresso
per il carburante, un energy hatch e un maintenance hatch. Un lancio dura 30 s a 480 EU/t con 1.000 L di carburante, e
mette un satellite sopra la dimensione della rampa. I problemi di manutenzione lo allungano, e qualsiasi blocco che
ferma la luce sopra la rampa mette il lancio in pausa finché non si libera; il vetro no.

I satelliti scendono dopo circa 100 giorni di Minecraft, un quinto in più o in meno: circa 33 ore di server acceso.
Dormire la notte non li fa scendere prima. Continua a lanciarne. Lo schermo della rampa mostra i satelliti di ogni
dimensione e quando scende il prossimo.

## Salti ai satelliti

Con 5 satelliti sopra una dimensione, ogni Telefono cellulare e ogni radio sotto il suo cielo aperto li raggiunge, al
livello Basso. I Telefoni ME non li usano. Più satelliti danno un segnale più forte:

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

## Parabola satellitare

Una <ItemLink id="gregtech:gt.blockmachines:25011" showIcon="left">Parabola satellitare</ItemLink> collega la sua rete a
ogni altra parabola della sua dimensione, attraverso i satelliti, e nell'Overworld a ogni pianeta con un Ricevitore
radio per razzi. È una parabola 5 per 5 di Clean Stainless Steel Machine Casing su un supporto di tre Stainless Steel
Frame Box, con un energy hatch e un maintenance hatch, a cielo aperto, a 512 EU/t. Collegala con la chiave a un'antenna,
una Testa di traliccio o un Terminale di linea. La pioggia sulla parabola toglie 3 dB al suo salto, un temporale 6, e
ogni problema di manutenzione 2 dB.

## Ricevitore radio per razzi

Metti un <ItemLink id="becom:rocket_radio_receiver" showIcon="left">Ricevitore radio per razzi</ItemLink> nella stiva di
un razzo di Galacticraft fermo su una piattaforma di lancio di Galacticraft, su una luna, un pianeta o una stazione
spaziale. Il razzo diventa lì un'antenna T1, con una portata di circa 64 blocchi, e il collegamento di quel mondo con i
satelliti dell'Overworld, al livello Alto. Il razzo ha una stiva solo se è costruito con dei bauli, e conta solo finché
il suo chunk è caricato. Un razzo su cui voli tu arriva come oggetto nel suo Parachest: piazzalo su una piattaforma e
mettici dentro il ricevitore.

Il collegamento vuole almeno 5 satelliti sopra l'Overworld, e una chiamata che ci passa non va meglio del salto ai
satelliti dal lato dell'Overworld: i telefoni sotto il cielo aperto dell'Overworld, e le reti con una parabola,
raggiungono il pianeta. I pianeti si raggiungono tra loro allo stesso modo, attraverso l'Overworld, con un ricevitore su
ciascuno. Il ricevitore non si collega con la chiave: per portare dentro le linee cablate di un pianeta, agganciale a
un'antenna a portata del razzo.

## Ripetitore di portale

Un <ItemLink id="gregtech:gt.blockmachines:25012" showIcon="left">Ripetitore di portale</ItemLink> funziona entro 4
blocchi da un portale del Nether, un portale dell'End, un portale del Twilight Forest o un portale di Personal Space,
con 32 EU/t di corrente MV. Collegalo con la chiave a un'antenna, una Testa di traliccio o un Terminale di linea della
tua rete. Poi usa la chiave su di lui, attraversa il portale e usala su un ripetitore dall'altra parte entro 5 minuti:
la coppia unisce le due reti al livello Alto.

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
