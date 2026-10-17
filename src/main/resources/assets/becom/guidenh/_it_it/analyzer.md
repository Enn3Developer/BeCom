---
navigation:
  title: L'Analizzatore di segnale
  parent: index.md
  position: 65
  icon: becom:signal_analyzer
item_ids:
  - becom:signal_analyzer:*
quest_ids:
  - "8sQY72VCXU2ZURqIKp1zVw"
---

# L'Analizzatore di segnale

Tieni in mano un <ItemLink id="becom:signal_analyzer" showIcon="left">Analizzatore di segnale</ItemLink> e una
sovrimpressione mostra il tuo segnale, riletto ogni secondo. Non vuole corrente. Gli operatori del server possono
stampare la stessa lettura in chat con `/becom signal`.

Legge due bande:

- **Radio (VHF):** quello che avrebbe un walkie-talkie dalle antenne e dai satelliti dove ti trovi.
- **Telefoni (UHF):** quello che avrebbe un Telefono cellulare.

## Leggere una banda

A colpo d'occhio è una riga per banda: quattro tacche di segnale e l'SNR, colorati secondo il livello, poi la **perdita
maggiore**, la prima da sistemare, con la sua icona. Se non c'è niente in mezzo dice invece chi ti serve: un'antenna T1,
T2 o T3, o i satelliti. "Nessun servizio" vuol dire che qui un telefono non ha servizio. Un walkie raggiunge comunque le
altre radio dritto, per circa 80 blocchi, e l'analizzatore non lo mostra.

**Accovacciati** per vedere ogni termine. Ogni banda dice chi la serve (un'antenna, con quanto è lontana e una freccia
che la indica, o i satelliti in cielo), poi disegna una barra per quello con cui parte il collegamento, una per ogni
perdita che ne toglie qualcosa, dalla maggiore, e quello che resta al livello che ne esce. L'ultima riga dice quanti dB
mancano al livello successivo ([Come funziona il segnale](signal.md)):

- **Bilancio:** quello da cui parte l'antenna: 42 dB per una T1, 48 per una T2, 54 per una T3.
- **Distanza:** cresce di 6 dB ogni volta che la distanza raddoppia. Avvicinati, o usa un'antenna più forte.
- **Terreno:** una collina o un edificio tra te e l'antenna. Alza l'antenna su un albero più alto, o mettila sulla
  collina.
- **Copertura:** quello che hai sopra la testa. Esci all'aperto, o porta un'antenna giù da te.
- **Meteo:** pioggia o temporale dove sei tu o l'antenna, sulla banda telefonica, e su entrambe le bande dai satelliti.
  Passa.
- **Rumore:** 10 dB in una dimensione senza cielo.

Dai satelliti, la prima barra è quanto danno, secondo quanti sono in orbita, e quanto ne tolgono copertura e meteo.

## Leggerlo in pratica

- **Basso e disturbato:** guarda la perdita maggiore. Terreno vuol dire che l'antenna deve stare più in alto; distanza
  vuol dire che serve un'antenna di livello più alto o una seconda a metà strada.
- **Nessuno sotto terra:** la copertura è oltre quello che qualsiasi antenna può recuperare. Porta giù una linea, con
  un'antenna che la aggancia in superficie.
- **Bene fuori, niente dentro:** è la copertura. Conta ogni blocco dai tuoi occhi fino alla cima del tetto: sotto un
  tetto di un blocco sopra una stanza alta due, circa 3,5 dB sulla banda telefonica, e di più con un soffitto più alto.
