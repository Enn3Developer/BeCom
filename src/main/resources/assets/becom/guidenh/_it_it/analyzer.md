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

Tieni in mano un <ItemLink id="becom:signal_analyzer" showIcon="left" /> e una sovrimpressione mostra il tuo segnale,
riletto ogni secondo. Non vuole corrente. Gli operatori del server possono stampare la stessa lettura in chat con
`/becom signal`.

Legge due bande:

- **Radio (VHF):** quello che avrebbe un walkie-talkie dalle antenne e dai satelliti dove ti trovi.
- **Telefoni (UHF):** quello che avrebbe un Telefono cellulare.

## Leggere una banda

Ogni banda mostra il suo SNR e il livello, poi chi ti serve meglio: un'antenna, con il suo livello e la sua posizione, o
i satelliti sopra la tua dimensione. "nessuna antenna o satellite a portata", dopo il nome di una banda, vuol dire che
qui un telefono non ha servizio. Un walkie raggiunge comunque le altre radio dritto, per circa 80 blocchi, e
l'analizzatore non lo mostra.

La riga successiva nomina la **perdita maggiore**, la prima da sistemare, e l'ultima elenca ogni termine del
collegamento ([Come funziona il segnale](signal.md)):

- **Bilancio:** quello da cui parte l'antenna: 42 dB per una T1, 48 per una T2, 54 per una T3.
- **Distanza:** cresce di 6 dB ogni volta che la distanza raddoppia. Avvicinati, o usa un'antenna più forte.
- **Terreno:** una collina o un edificio tra te e l'antenna. Alza l'antenna su un albero più alto, o mettila sulla
  collina.
- **Copertura:** quello che hai sopra la testa. Esci all'aperto, o porta un'antenna giù da te.
- **Meteo:** pioggia o temporale dove sei tu o l'antenna, sulla banda telefonica, e su entrambe le bande dai satelliti.
  Passa.
- **Rumore:** 10 dB in una dimensione senza cielo.

Dai satelliti, la riga mostra quanto danno, secondo quanti sono in orbita, e quanto ne tolgono copertura e meteo.

## Leggerlo in pratica

- **Basso e disturbato:** guarda la perdita maggiore. Terreno vuol dire che l'antenna deve stare più in alto; distanza
  vuol dire che serve un'antenna di livello più alto o una seconda a metà strada.
- **Nessuno sotto terra:** la copertura è oltre quello che qualsiasi antenna può recuperare. Porta giù una linea, con
  un'antenna che la aggancia in superficie.
- **Bene fuori, niente dentro:** è la copertura. Conta ogni blocco dai tuoi occhi fino alla cima del tetto: sotto un
  tetto di un blocco sopra una stanza alta due, circa 3,5 dB sulla banda telefonica, e di più con un soffitto più alto.
