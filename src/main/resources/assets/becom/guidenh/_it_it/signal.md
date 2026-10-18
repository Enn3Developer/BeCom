---
navigation:
  title: Come funziona il segnale
  parent: index.md
  position: 90
  icon: becom:signal_analyzer
---

# Come funziona il segnale

Una chiamata o un canale radio viaggia salto per salto: dal Telefono al Router al router successivo, da un walkie a
un'antenna all'antenna successiva, da un telefono su fino ai satelliti. Ogni salto ha un rapporto segnale/rumore, l'SNR,
in decibel (dB). Il collegamento vale quanto il suo salto più debole, e non più di quanto permettono i dispositivi ai due
capi.

## Livelli

| Livello | SNR | Cosa senti |
| --- | --- | --- |
| Nessuno | sotto 6 dB | Niente: un nuovo collegamento non parte |
| Basso | 6 dB | Stretto, ruvido e rumoroso, ma si capisce |
| Medio | 12 dB | Banda limitata, con un po' di rumore |
| Alto | 18 dB | Ampio, con un rumore leggero |
| Massimo | 24 dB | Pulito |

Un collegamento raggiunge un livello alla sua soglia e lo lascia solo 2 dB sotto, così non salta avanti e indietro tra
due: un collegamento al livello Basso si sente fino a 4 dB. I collegamenti vengono rivalutati una volta al secondo.

Ogni dispositivo limita quello che sente e che manda: il Telefono al livello Basso, le radio al Medio, il Telefono
cellulare e il Videotelefono all'Alto, il Telefono ME al Massimo. Una chiamata tra due telefoni prende il limite più
basso dei due.

## Disturbi e cadute

Tra 6 e 12 dB il segnale si disturba: brevi tratti di voce si perdono, il 30% a 6 dB, meno man mano che il segnale
sale, e nulla da 12. Sotto 4 dB non senti niente.

Quando un collegamento scende sotto 6 dB parte un conto di 3 secondi. I collegamenti vengono rivalutati una volta al
secondo, quindi il conto finisce dopo 3 o 4 secondi. Allora:

- una chiamata tra due telefoni finisce; in una chiamata di gruppo, un telefono tagliato fuori da tutti gli altri esce,
  e gli altri continuano;
- un telefono che sta ancora squillando smette;
- su un canale radio non finisce niente: quel collegamento resta muto finché non torna a 6 dB.

## Cavi

Il cavo è digitale e non perde niente: una chiamata sulla rete cablata è buona quanto i suoi telefoni, e solo i suoi
salti radio, se ne ha, le tolgono segnale. Quello che un bus limita è lo spazio per le chiamate, e la sua lunghezza: vedi
[La rete cablata](wired.md).

## Radio

Un salto radio parte dal suo bilancio di collegamento e perde:

- **Distanza:** 20 · log10 della distanza in blocchi. Ogni raddoppio della distanza costa 6 dB, quindi 6 dB di bilancio
  in più raddoppiano la portata in campo aperto.
- **Terreno:** la collina o l'edificio peggiore tra i due capi, secondo quanto sale sopra la linea retta che li unisce.
  Fino a 40 dB.
- **Copertura:** un capo con qualcosa sopra perde 1,5 dB per blocco sulla banda radio e 2,5 sulla banda telefonica,
  fino a 40 dB. I blocchi si contano dagli occhi del capo, o dalla cima della testa di un'antenna, fino alla cima del
  blocco più alto sopra di lui che ferma la luce: tetti, foglie e acqua contano, il vetro no. Un tetto costa qualche
  dB; in fondo sotto la roccia, il collegamento sparisce.
- **Meteo:** 3 dB con la pioggia e 6 con un temporale, dove cade pioggia o neve su uno dei due capi. I deserti restano
  sereni. Lo sentono solo la banda telefonica e i salti ai satelliti.
- **Rumore:** 10 dB in una dimensione senza cielo, come il Nether, dove terreno e copertura non si contano.

La banda radio (VHF), che usano le radio, aggira meglio le colline; la banda telefonica (UHF), che usano le chiamate,
vuole una linea di vista più libera.

Le antenne mappano il terreno intorno quando si formano, e poi ogni 5 minuti: per questo una collina nuova può metterci
un po' a comparire in un collegamento.
