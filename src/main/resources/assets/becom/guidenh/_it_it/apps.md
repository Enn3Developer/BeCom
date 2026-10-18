---
navigation:
  title: App del telefono
  parent: index.md
  position: 45
  icon: becom:becom_card
item_ids:
  - becom:becom_card:*
  - becom:machine:25022
quest_ids:
  - "n3P0tSabXDS4eCuzXQVCYQ"
---

# App del telefono

Telefono cellulare e Telefono ME eseguono app scritte in Lua. Ogni telefono contiene un computer di OpenComputers che si
accende quando si apre un'app, o quando un'app in background ha da fare, e si spegne quando non serve. Finché è acceso,
le app consumano 8 EU/t dalla batteria del telefono: **Impostazioni › Batteria** elenca il consumo di oggi per uso,
Standby, Chiamate, Video e Schermo, e ogni app per nome, con la sua quota.

## Avere le app

- **App Store:** il riquadro App Store nella schermata home, finché il telefono raggiunge un Mirror dell'App Store (più
  sotto). Le sue schede elencano le app **Popolari**, le **Nuove** e quelle **Installate** sul tuo telefono. La pagina di un'app dice cosa può fare, quando è stata aggiornata e chi la
  vede, e ha **Segnala** per indicarla agli operatori. Chiunque può pubblicare un'app da un computer: prima glielo
  chiede il suo telefono, per tutti o solo per la sua squadra, e va a suo nome, al massimo 32 app a testa. Gli operatori
  possono elencare le segnalazioni, nascondere un'app, leggerne la sorgente o toglierla per sempre.
- **Da un computer:** un computer con una Scheda BeCom offre un'app a un telefono tramite il suo numero. Chi lo porta vede
  l'offerta nella home, con chi la manda, da dove e cosa può fare, e la installa o la annulla. Installa solo app di
  persone di cui ti fidi.

Un telefono tiene 16 app. **Impostazioni › App** le elenca. La pagina di ogni app ha un interruttore **In background**,
quante volte si è svegliata e quanta batteria ha usato oggi, sotto **Può** un interruttore per ogni cosa che l'app
dichiara (Rete, Notifiche, Chiamate, Camera), quanta memoria occupa e **Disinstalla**. Spegnerne uno nega quella cosa
all'app, anche se l'ha chiesta. Un'app che non raggiunge il suo computer, senza segnale o col computer spento, ha
**Riprova**; un'app andata in errore mostra l'errore su cui si è fermata.

## Il Mirror dell'App Store

L'App Store è di tutto il server, ma i telefoni ci arrivano solo attraverso un
<ItemLink id="becom:machine:25022" showIcon="left">Mirror dell'App Store</ItemLink>: una macchina MV, 64 EU/t,
collegata con il cavo su qualsiasi faccia a un bus. Un telefono con un percorso verso un mirror in funzione, attraverso
antenne, router e tutto ciò che porta una chiamata, apre lo store; senza, lo store lo dice. Ogni mirror porta allo
stesso store.

Un'installazione è un download: l'app scende dal mirror che il telefono raggiunge più in fretta, alla velocità di quel
percorso, e il pulsante si riempie man mano. Prende ciò che le chiamate lasciano di ogni collegamento, come ogni dato,
quindi un'app da 64 KB ci mette 16 secondi a Basso e un attimo sulla fibra. Se a metà il mirror esce di portata, si
ferma. Un telefono scarica un'app alla volta. Le app installate funzionano senza mirror; aggiornarne una è di nuovo un
download.

Anche un computer pubblica attraverso un mirror: `becom publish` ne vuole uno alla portata del computer. Un cacciavite
riserva un mirror alla squadra del suo proprietario, e di nuovo lo apre a tutti. Il suo schermo e WAILA mostrano i
download che serve ora e oggi.

## La Scheda BeCom

<ItemLink id="becom:becom_card:0" showIcon="left">Scheda BeCom (radio)</ItemLink> e
<ItemLink id="becom:becom_card:1" showIcon="left">Scheda BeCom (cablata)</ItemLink> sono schede di OpenComputers. Danno
al computer un numero e il componente `becom`. La scheda radio ha il segnale di un Telefono cellulare tenuto dove sta il
computer. Quella cablata si unisce al bus il cui cavo è collegato al computer, a Massimo e alla velocità del suo cavo.

Il disco degli strumenti, **becom**, è un disco loot di OpenComputers: combina un qualsiasi disco loot, come OpenOS,
con una chiave inglese, più volte, finché non diventa questo. Il suo programma `becom` fa i lavori comuni:

| Comando | Cosa fa |
| --- | --- |
| `becom number` | Il numero e il segnale di questo computer |
| `becom install <file> <numero>` | Offre un'app a un telefono e aspetta la risposta |
| `becom publish <file>` | Chiede al giocatore più vicino, sul suo telefono, di pubblicare un'app a suo nome |
| `becom send <numero> <app> <testo>` | Manda dati a un'app su un telefono, o a un computer |
| `becom listen` | Stampa i dati man mano che arrivano |

## Dati

App e computer si mandano dati tramite numero, fino a 8 KB per volta. I dati viaggiano come una chiamata: serve un
percorso tra i due capi, e la velocità è quella del capo più lento: 4, 16, 64 o 256 KB/s via radio da Basso a Massimo, o la
velocità di un bus sul cavo. I messaggi di chi manda partono uno dopo l'altro, a quella velocità. I dati
prendono ciò che le chiamate lasciano di ogni collegamento: le chiamate vengono sempre prima, e quando riempiono un
collegamento i dati aspettano. Il Router, la Centrale e ogni dorsale mostrano quanto prendono i dati.
Un'app in background si sveglia per i dati al massimo una volta al minuto, e fino ad allora i dati la aspettano; i dati
per un'app non installata, che non usa `net`, o non ammessa in background mentre le app del telefono sono ferme, vanno
persi. Sotto un'app che mostra dati, lo schermo dice quanto sono vecchi gli ultimi, quando lo sono di qualche secondo.

## Scrivere un'app

Un'app è un solo file Lua, fino a 64 KB. Le righe di commento in cima le danno un nome:

```lua
--@name Notes
--@version 1.0
--@accent 2a6aaa
--@about A note kept on the phone.
--@uses storage, notify
--@icon 0000000000aaaa000a0000a00a0ff0a00a0000a00a0ff0a000aaaa0000000000
```

`--@uses` dice cosa può fare l'app oltre a disegnare il suo schermo, e un'app che non lo dice non ha nulla di questo:
`net` per mandare e ricevere dati, `notify` per le notifiche, `storage` per i suoi file, `call` per fare chiamate
mentre è sullo schermo, e `camera` per vedere ciò che guarda chi lo porta mentre è sullo schermo. `--@icon` è di 8 per
8 celle, una riga alla volta dall'alto, ognuna un codice colore del testo da 1 a f, 0 per nessuno; si vede sul
riquadro dell'app e nello store.

Il file restituisce una tabella di funzioni, tutte facoltative: `init()` una volta al caricamento, `draw()` che
restituisce i widget da mostrare, `on(id, value)` quando si usa un pulsante, un interruttore o un campo, `message(from,
data)` quando arrivano dati, e `tick()` circa una volta al secondo. `draw()` viene richiamata dopo ognuna di queste.

| Widget | Campi |
| --- | --- |
| `heading`, `text` | `text` |
| `value` | `label`, `value`, `level` |
| `progress` | `label`, `value` da 0 a 1, `level` |
| `toggle` | `id`, `label`, `on` |
| `button` | `id`, `label`, `style`: `go` o `danger` |
| `item` | `label`, `item`, `value` |
| `input` | `id`, `hint` |

Un `level` è `low`, `medium`, `high` o `max`, e colora il widget come un segnale. La tabella globale `phone` contiene il
resto: `number()`, `signal()`, `foreground()`, `redraw()`, `send(number, data)`, `notify(title, text)`, `call(number)`
e `camera()` (solo sullo schermo), `uptime()`, e `read`, `write`, `remove`, `list` e `used()` per i file dell'app.
`camera()` restituisce una tabella per il blocco o la creatura che guarda chi lo porta, fino a 16 blocchi: il suo
`kind`, `name`, `x`, `y`, `z` e `distance`. La memoria di un telefono è di 256 KB, e un'app ne tiene al massimo 64 KB.
Ciò che un'app non può fare restituisce nil e il motivo. Il
disco degli strumenti contiene `apps/notes.lua`, un breve esempio di tutto questo.
