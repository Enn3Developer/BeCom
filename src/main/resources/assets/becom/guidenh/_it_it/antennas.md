---
navigation:
  title: Antenne
  parent: index.md
  position: 75
  icon: gregtech:gt.blockmachines:25004
item_ids:
  - gregtech:gt.blockmachines:25004
  - gregtech:gt.blockmachines:25005
  - gregtech:gt.blockmachines:25006
quest_ids:
  - "UYdKN4lyUKWOiqsyX6luCQ"
  - "BRsUHvwqVhSei0mx4y1eWg"
  - "fia8emGdXIqzunSxVhLBng"
---

# Antenne

Un'antenna serve ogni radio e ogni telefono nel suo raggio, tranne i Telefoni ME, ripete tutti i 16 canali radio, si
collega alle altre antenne e porta le linee agganciate a lei.

## Costruirne una

La testa d'antenna sta su un albero di 4-32 frame box, sopra una base 3 per 3 di machine casing con dentro un energy
hatch. Più è alta la testa, più lontano vede: un albero alto su una collina copre molto più della stessa antenna in una
valle.

| Antenna | Testa | Base | Albero | Corrente | Portata in campo aperto |
| --- | --- | --- | --- | --- | --- |
| T1 (fine LV) | <ItemImage id="gregtech:gt.blockmachines:25004">Testa d'antenna T1</ItemImage> | LV Machine Casing | Steel Frame Box | 32 EU/t | circa 64 blocchi |
| T2 (metà MV) | <ItemImage id="gregtech:gt.blockmachines:25005">Testa d'antenna T2</ItemImage> | MV Machine Casing | Aluminium Frame Box | 128 EU/t | circa 128 blocchi |
| T3 (metà HV) | <ItemImage id="gregtech:gt.blockmachines:25006">Testa d'antenna T3</ItemImage> | HV Machine Casing | Stainless Steel Frame Box | 512 EU/t | circa 256 blocchi |

Un'antenna funziona solo se caricata e alimentata.

## Cosa fa

- **Serve i dispositivi** nel suo raggio su entrambe le bande: i walkie-talkie sulla banda radio, i telefoni sulla
  banda telefonica.
- **Si collega alle altre antenne** dove il loro segnale arriva almeno al livello Basso, separatamente per ogni banda: i
  canali radio viaggiano tra le antenne sulla banda radio, le chiamate sulla banda telefonica.
- **Aggancia le linee:** usa la chiave su di lei, poi su una Testa di traliccio o un Terminale di linea entro 16
  blocchi, e le chiamate di quella linea passano via etere, a ogni telefono che le antenne raggiungono. Un'antenna ha 15
  collegamenti, condivisi tra i suoi agganci e ogni Parabola satellitare, Ripetitore di portale o Ponte ME collegati a
  lei.

## Il suo schermo

Clic destro sulla testa per il suo schermo: le antenne a cui si collega e le linee che aggancia, e una mappa di
copertura del segnale che avrebbe un giocatore in piedi a terra intorno a lei, per le radio o per le chiamate. Il
proprietario può riservarla alla sua squadra: allora serve solo i dispositivi della squadra, ma continua a portare le
chiamate lungo i suoi collegamenti. Le squadre sono quelle di ServerUtilities, che si creano con il suo pulsante My Team
accanto all'inventario, e gli alleati di una squadra non ne fanno parte.

Un'antenna mappa il terreno intorno quando si forma, e di nuovo ogni 5 minuti. Un edificio nuovo in mezzo compare al
controllo successivo.
