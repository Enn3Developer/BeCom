---
navigation:
  title: Radio
  parent: index.md
  position: 70
  icon: becom:walkie_talkie
item_ids:
  - becom:walkie_talkie:*
  - gregtech:gt.blockmachines:25007
quest_ids:
  - "VdecT0iBUsCxBEbIo6xNeA"
  - "82uwmy_wU3eMPoGqyurVlw"
  - "gNOxxRElWUebInHOkYOeQw"
---

# Radio

Da MV, le radio condividono 16 canali aperti: chiunque sia sintonizzato sente quello che si dice, e nessuno può essere
bloccato. Tieni premuto il tasto per parlare della radio, <KeyBind id="key.becom.radio">Bloc Maiusc</KeyBind>, per
parlare.

## Walkie-talkie

Il <ItemLink id="becom:walkie_talkie" showIcon="left">Walkie-talkie</ItemLink> va con la sua batteria MV: si carica in
un charger o in un battery buffer GT di livello MV o superiore. Funziona dall'inventario o da uno slot bauble. Clic
destro per scegliere un canale o spegnerlo, oppure accovacciati e usa la rotellina tenendolo in mano per cambiare
canale.

Da solo arriva a circa 80 blocchi in campo aperto, e molto più lontano attraverso le antenne. Chiunque sia entro 6
blocchi da un walkie che riceve lo sente anche lui. Consuma un po' in attesa e di più mentre riceve o trasmette. Uno
scarico si spegne, e resta spento anche dopo la ricarica finché non lo riaccendi.

## Ricetrasmettitore

Il <ItemLink id="gregtech:gt.blockmachines:25007" showIcon="left">Ricetrasmettitore</ItemLink> è una radio su un blocco,
a MV e 32 EU/t. Stagli entro 2 blocchi e tieni premuto il tasto per parlare per trasmettere con lui invece che con il
tuo walkie. Il suo altoparlante riproduce il suo canale a chiunque sia entro 16 blocchi. Da solo arriva a circa 125
blocchi in campo aperto.

## Chi senti

Senti ogni trasmettitore per il migliore di due percorsi: dritto da radio a radio, o attraverso le antenne tra voi, che
ripetono ogni canale ([Antenne](antennas.md)). Le radio arrivano al massimo al livello Medio.

Quando due parlano insieme, senti solo il più forte. Chi stai sentendo tiene il canale fino a un attimo dopo che ha
smesso; un altro prende il suo posto solo se il suo segnale verso di te è più forte di almeno 3 dB.

A cielo aperto, con 5 satelliti in orbita sopra la tua dimensione, le radio si raggiungono anche attraverso i
satelliti, a qualsiasi distanza ([Lo spazio e le altre dimensioni](space.md)). Con soli 5, quel collegamento sta proprio
al limite del livello Basso, e la pioggia o un tetto sopra una delle due radio lo tagliano; 14 satelliti lo tengono al
Medio. La radio non lascia mai la sua dimensione.

Quando sei entro 16 blocchi da qualcuno, senti la sua voce direttamente, come la riproduce GTNH Voice, e BeCom si fa da
parte.
