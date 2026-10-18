---
navigation:
  title: Antenne
  parent: index.md
  position: 75
  icon: becom:machine:25004
item_ids:
  - becom:machine:25004
  - becom:machine:25005
  - becom:machine:25006
quest_ids:
  - "UYdKN4lyUKWOiqsyX6luCQ"
  - "BRsUHvwqVhSei0mx4y1eWg"
  - "fia8emGdXIqzunSxVhLBng"
---

# Antenne

Un'antenna serve ogni radio e ogni telefono nel suo raggio, tranne i Telefoni ME nella portata della loro rete ME, ripete tutti i 16 canali radio, si
collega alle altre antenne e smista i bus cablati alle sue Line Hatch.

## Costruirne una

Un'antenna si costruisce come un vero albero radio. La testa sta in cima a un albero di frame box piantato su una platea
di machine casing, con le antenne appese sotto la testa e uno shelter degli apparati davanti alla platea, dove va
l'energy hatch. T1 e T2 sono alberi strallati, tenuti su da tre stralli di frame box che scendono ad ancoraggi di casing
dietro e ai lati; T3 è un traliccio autoportante. Più è alta la testa, più lontano vede: un albero alto su una collina
copre molto più della stessa antenna in una valle. L'anteprima di NEI le mostra blocco per blocco.

| Antenna | Testa | Struttura | Casing | Albero | Corrente | Chiamate | Portata in campo aperto |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T1 (fine LV) | <ItemImage id="becom:machine:25004">Testa d'antenna T1</ItemImage> | Albero strallato, un dipolo | LV Machine Casing | 6-32 Steel Frame Box | 24 EU/t | 4 | circa 64 blocchi |
| T2 (metà MV) | <ItemImage id="becom:machine:25005">Testa d'antenna T2</ItemImage> | Albero strallato più alto, due dipoli incrociati | MV Machine Casing | 8-32 Aluminium Frame Box | 96 EU/t | 8 | circa 128 blocchi |
| T3 (metà HV) | <ItemImage id="becom:machine:25006">Testa d'antenna T3</ItemImage> | Traliccio, antenne a pannello | HV Machine Casing | 10-32 Stainless Steel Frame Box | 384 EU/t | 16 | circa 256 blocchi |

Un'antenna funziona solo se caricata e alimentata. Le sue Energy Hatch la mandano in overclock come le
strutture di GT con una ricetta, da sole, fin dove arriva la loro energia: ogni overclock è il doppio delle chiamate
per quattro volte l'energia, con la stessa portata. Con troppa poca energia o tensione resta spenta e dice perché.

## Cosa fa

- **Serve i dispositivi** nel suo raggio su entrambe le bande: i walkie-talkie sulla banda radio, i telefoni sulla
  banda telefonica.
- **Si collega alle altre antenne** dove il loro segnale arriva almeno al livello Basso, separatamente per ogni banda: i
  canali radio viaggiano tra le antenne sulla banda radio, le chiamate sulla banda telefonica.
- **Smista i bus:** tramite le Line Hatch nel suo shelter, è il router di ogni bus collegato. Un Telefono cablato a
  una di loro passa le sue chiamate per l'antenna, e un Router cablato a una le unisce i suoi bus: le loro chiamate
  passano via etere, a ogni telefono che le antenne raggiungono.

## Il suo schermo

Clic destro sulla testa per il suo schermo: le antenne a cui si collega e i bus collegati alle sue Line Hatch, e una
mappa di copertura del segnale che avrebbe un giocatore in piedi a terra intorno a lei, per le radio o per le chiamate. Il
proprietario, la sua squadra o un operatore del server possono riservarla alla squadra del proprietario: allora serve
solo i dispositivi di quella squadra, ma continua a portare le chiamate lungo i suoi collegamenti. Le squadre sono
quelle di ServerUtilities, che si creano con il suo pulsante My Team accanto all'inventario. Gli alleati di una squadra
non ne fanno parte, e su un server dedicato la squadra "singleplayer" non conta.

Un'antenna mappa il terreno intorno quando si forma, e di nuovo ogni 5 minuti. Un edificio nuovo in mezzo compare al
controllo successivo.
