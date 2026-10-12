---
navigation:
  title: Telefoni
  parent: index.md
  position: 50
  icon: becom:mobile_phone
item_ids:
  - becom:mobile_phone:*
  - becom:me_phone:*
  - gregtech:gt.blockmachines:25008
  - gregtech:gt.blockmachines:25009
quest_ids:
  - "Cs4eUVpnUBeevZzOvcInrw"
  - "1e_b8ZF0VRq1STROZstNIg"
  - "tmuzvF6QVE-ZigvcfAhM5Q"
  - "QTf-qLjuX5uncRhFsloOcg"
  - "SSBYl-CuVHeaS00Zgy5NWA"
  - "6JNs0PJ2W_2dWPcb8B5q0A"
  - "1FYiW9xFV5eNdWDcFL6cbw"
---

# Telefoni

Ogni telefono può chiamare ogni altro telefono verso cui la rete ha un percorso. Il livello di una chiamata è quello del
suo salto più debole, limitato da entrambi i telefoni. Le chiamate prendono fino a 8 persone: chiunque sia in una può
aggiungere un telefono dalla tastiera o dai contatti.

## Telefono cellulare

Il <ItemLink id="becom:mobile_phone" showIcon="left">Telefono cellulare</ItemLink> prende il servizio dalle antenne
sulla banda telefonica, circa 64 blocchi da una T1 in campo aperto, e dai satelliti a cielo aperto ([Lo spazio e le
altre dimensioni](space.md)). Le sue chiamate arrivano fino al livello Alto. Prende il suo numero, e il suo
proprietario, appena qualcuno lo porta con sé, e funziona dall'inventario o da uno slot bauble. La sua batteria tiene
1.200.000 EU e consuma 4 EU/t in attesa, 16 in chiamata: si carica in un charger o in un battery buffer GT di livello HV
o superiore. Un telefono scarico si spegne, e resta spento anche dopo la ricarica finché non lo riaccendi.

- **Tasto Telefono,** <KeyBind id="key.becom.phone">N</KeyBind>: risponde a un telefono che squilla, riaggancia durante
  una chiamata, e altrimenti apre lo schermo del telefono. Accovacciati e premilo per rifiutare una chiamata. Risponde
  anche a un Telefono che squilla entro la sua cornetta, o a un Videotelefono vicino a cui ti trovi.
- **Squillo:** un telefono portatile squilla da chi lo porta, e lo sentono anche i giocatori vicini. Un avviso in un
  angolo dice chi chiama. In modalità silenziosa lo senti solo tu, come una vibrazione.
- **Tacche** accanto alla barra degli strumenti mostrano il tuo servizio mentre tieni in mano un telefono, o il livello
  della chiamata durante una chiamata.
- **Impostazioni:** accensione, modalità silenziosa, non disturbare, nascondersi dai contatti e la lista dei bloccati.

## Videotelefono

Il <ItemLink id="gregtech:gt.blockmachines:25009" showIcon="left">Videotelefono</ItemLink> è un vivavoce con uno schermo
per le reti cablate, a MV e 32 EU/t. Collegalo con Cavo telefonico a un Terminale di linea, come un Telefono. Chiunque
sia entro 4 blocchi è nella sua chiamata e sul suo schermo, e il suo altoparlante porta la chiamata a 16 blocchi.
Riaggancia da solo 30 secondi dopo che l'ultima persona se ne va.

## Videochiamate

I Telefoni cellulari, i Telefoni ME e i Videotelefoni mostrano il video. La fotocamera frontale mostra il tuo
personaggio, che si muove quando ti muovi e annuisce quando parli. La fotocamera posteriore mostra quello che vedi, da
un Telefono cellulare o da un Telefono ME, nelle chiamate tra due persone al livello Alto o meglio, e parte spenta. Il
Videotelefono non ha fotocamera posteriore, ma mostra quella dell'altra parte.

| Livello | Fotocamera frontale | Fotocamera posteriore |
| --- | --- | --- |
| Basso | Bloccata, "Segnale debole"; la voce continua | Spenta |
| Medio | Alta 48 pixel, 5 immagini al secondo, disturbata | Spenta |
| Alto | Alta 96 pixel, 12 al secondo, un po' disturbata | 2 immagini al secondo |
| Massimo | Alta 192 pixel, ogni fotogramma, pulita | 5 immagini al secondo |

Solo una chiamata tra due Telefoni ME arriva al Massimo: ogni altro telefono si ferma all'Alto.

Durante una chiamata, una piccola finestra in un angolo mostra l'altra parte. La scheda Video dello schermo del
telefono la mostra a tutto schermo, accende o spegne ogni fotocamera, e nasconde o mostra la finestra.

## Telefono ME

Il <ItemLink id="becom:me_phone" showIcon="left">Telefono ME</ItemLink> è un Telefono cellulare collegato a una rete ME.
Collegalo a un Terminale di sicurezza ME, come un Terminale wireless: ha servizio al livello Massimo ovunque arrivino i
Punti d'accesso wireless ME di quella rete, e da nessun'altra parte, perché antenne e satelliti non lo servono. I
Telefoni ME della stessa rete ME si chiamano senza antenne. Si carica in un Caricatore.

Per chiamare qualsiasi altro telefono, la rete ME vuole un
<ItemLink id="gregtech:gt.blockmachines:25008" showIcon="left">Ponte ME</ItemLink>: mettilo su un cavo ME, dove prende
un canale, e collegalo con la chiave a una Testa di traliccio, un Terminale di linea o un'antenna entro 16 blocchi. Usa
512 EU/t, e unisce le due reti al livello Massimo. Una rete ME porta le chiamate solo all'interno di ciascuna
dimensione.
