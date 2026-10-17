"""Italian quest text for make_quests.py, by quest key: (name, description). Every quest needs an entry here."""

LINE_IT = ("Better Communications",
           "Telefoni su linee di tralicci, radio, telefoni cellulari e satelliti, portati dalla chat vocale.")
QUESTS_IT = {
    "phone_cable": ("Cablato per il suono",
        "Ogni rete telefonica comincia da un filo. Il Cavo telefonico è filo di red alloy avvolto nella gomma, e si "
        "fa nell'Assembler.%n%nVa da un Telefono al suo Terminale di linea, fino a 32 blocchi, e il segnale non "
        "perde nulla per strada. Fanne un paio per cominciare."),
    "telephone": ("Driin, driin",
        "Un circuito, un note block per la suoneria e un corpo d'acciaio, messi insieme nell'Assembler con un filo "
        "di Cavo telefonico: ecco un Telefono.%n%nClic destro per alzare la cornetta. È tuo, e lo è anche il suo "
        "numero di sei cifre."),
    "line_terminal": ("Capolinea",
        "Ogni linea inizia e finisce in un Terminale di linea. Dagli corrente LV, bastano 4 EU/t, e collegalo con "
        "il Cavo telefonico a un Telefono. Un terminale serve un solo telefono.%n%nPer metterlo su una linea, usa "
        "la chiave sul terminale, poi su una Testa di traliccio."),
    "pylon": ("Sul palo",
        "Il Cavo telefonico arriva solo a 32 blocchi. Per andare più lontano servono i tralicci: una Testa di "
        "traliccio in cima a un palo di 6-16 Wood Frame Box, con una traversa di altre 4 subito sotto la "
        "testa.%n%nPiù è alto, meglio è. Due tralicci si collegano fino a "
        "quattro volte l'altezza del più basso, se in mezzo non c'è niente di solido. Usa la chiave su una testa, "
        "poi sull'altra, per collegarli.%n%nI tralicci non vogliono corrente e funzionano anche nei chunk "
        "scaricati. Il segnale però cala con la distanza, quindi ti serve un ripetitore almeno ogni 225 blocchi."),
    "first_call": ("Pronto? Mi senti?",
        "È ora della prima chiamata! Ti servono due Telefoni, ognuno sul suo Terminale di linea, con i tralicci tra "
        "i terminali.%n%nAlza la cornetta. Se sulla linea c'è un solo altro telefono, squilla subito. Altrimenti "
        "componi il suo numero o scegli un contatto.%n%nNon aspettarti l'alta fedeltà: al Telefono la voce è "
        "stretta e ruvida. Però arriva fin dove arriva la linea."),
    "repeater": ("Tenere vivo il segnale",
        "Le linee lunghe si fanno silenziose. Una Testa ripetitore è una Testa di traliccio che ripulisce il "
        "segnale, così la perdita di linea riparte da zero dopo di lei.%n%nA differenza di un traliccio semplice "
        "vuole corrente: 4 EU/t di LV, da un Energy Hatch in un armadio alla base del palo. Funziona solo se è caricata e alimentata, "
        "quindi tieni caricati i chunk dei ripetitori."),
    "router": ("Centralino",
        "Una linea semplice unisce esattamente due telefoni. Ne vuoi di più? Una Testa router ha 15 collegamenti e "
        "trasforma le tue linee in una rete, dove ogni telefono può chiamare qualsiasi altro.%n%nRipulisce il "
        "segnale come un ripetitore e prende 16 EU/t di corrente LV da un Energy Hatch nell'armadio di centrale alla base del palo: "
        "tre blocchi di larghezza, due di altezza, con una seconda traversa in cima per tutte quelle linee."),
    "antenna_t1": ("In onda",
        "È ora di tagliare il filo. Una Testa d'antenna T1 sta in cima a un albero strallato di 6-32 Steel Frame "
        "Box, con un dipolo sotto la testa e tre stralli fino ad ancoraggi di LV Machine Casing; l'Energy Hatch va "
        "nello shelter degli apparati davanti. Usa 32 EU/t.%n%nArriva a circa 64 blocchi in campo aperto. "
        "L'altezza aiuta; colline e tetti ti costano segnale. Le antenne si trovano da sole, e collegandone una con "
        "la chiave a una Testa di traliccio o a un Terminale di linea entro 16 blocchi porti quella linea via radio."),
    "transceiver": ("Stazione base",
        "Un Ricetrasmettitore è una radio in un blocco. Va a MV, 32 EU/t, e da solo arriva a circa 125 blocchi in "
        "campo aperto.%n%nStagli entro 2 blocchi e tieni premuto il tasto per parlare della radio (Bloc Maiusc, se "
        "non l'hai cambiato) per parlare sul suo canale. Il suo altoparlante riproduce il canale a chiunque sia "
        "entro 16 blocchi."),
    "signal_analyzer": ("Leggere l'aria",
        "La radio è capricciosa, e l'Analizzatore di segnale ti dice perché. Tienilo in mano per vedere il tuo "
        "segnale: la forza in dB, il livello, l'antenna che ti serve e cosa ti costa di più, tra distanza, terreno, "
        "copertura, meteo e rumore.%n%nUn collegamento vuole 6 dB per il livello Basso e 12 per il Medio. Sotto i 4 "
        "dB cade subito; tra 4 e 6 regge 3 secondi, poi cade."),
    "walkie_talkie": ("Radio tascabile",
        "Una radio da portare in giro. Il Walkie-talkie va con la sua batteria MV: si carica come qualsiasi "
        "attrezzo GT.%n%nClic destro per scegliere uno dei 16 canali o spegnerlo, oppure accovacciati e usa la "
        "rotellina per cambiare canale. Tieni premuto il tasto per parlare per trasmettere. Da solo arriva a circa "
        "80 blocchi, e molto più lontano con le antenne.%n%nChiunque entro 6 blocchi sente quello che riceve, "
        "quindi non è adatto ai segreti."),
    "radio_contact": ("Passo e chiudo",
        "Ora mettila alla prova! Parla con qualcuno via radio, o sentilo: con un walkie, con un Ricetrasmettitore o "
        "attraverso le antenne tra voi. Deve passare per la radio: sentirlo di persona non conta.%n%nGalateo radio: "
        "i canali sono aperti a chiunque sia sintonizzato, e se due parlano insieme ognuno sente solo il più forte. "
        "Di' \"passo\" quando hai finito."),
    "antenna_t2": ("Un albero più alto",
        "Stessa idea, più portata. Una Testa d'antenna T2 sta in cima a un albero strallato più alto di "
        "Aluminium Frame Box, da 8 a 32, con due dipoli incrociati e MV Machine Casing, e usa 128 EU/t.%n%nArriva a circa 128 blocchi, il doppio di una T1: ogni 6 dB di bilancio di "
        "collegamento raddoppiano la portata in campo aperto. Le antenne ripetono tra loro tutti i 16 canali radio "
        "e portano le chiamate tra le linee agganciate a loro."),
    "antenna_t3": ("Oltre la valle",
        "Una Testa d'antenna T3 sta in cima a un traliccio autoportante di Stainless Steel "
        "Frame Box su HV Machine Casing, con antenne a pannello in cima, usa 512 EU/t e "
        "arriva a circa 256 blocchi.%n%nCostruiscila in alto. Rileva il terreno intorno ogni cinque minuti, e una "
        "collina in mezzo può costarti più della distanza."),
    "videophone": ("Faccia a faccia",
        "Un vivavoce con uno schermo, per tutta la squadra. Il Videotelefono va a MV, 32 EU/t, e si collega a un "
        "Terminale di linea con il Cavo telefonico, proprio come un Telefono.%n%nClic destro per comporre, "
        "rispondere o riagganciare. Chiunque sia entro 4 blocchi è nella chiamata e sullo schermo, e il suo "
        "altoparlante porta la chiamata a 16 blocchi. Riaggancia da solo 30 secondi dopo che l'ultima persona se ne "
        "va."),
    "video_call": ("Vedere per credere",
        "Vedi qualcuno in una videochiamata, con un Videotelefono, un Telefono cellulare o un Telefono ME, con la "
        "sua fotocamera frontale accesa.%n%nLa frontale mostra il tuo personaggio; la posteriore mostra quello che "
        "vedi, nelle chiamate a due al livello Alto o meglio. Il video vuole almeno il livello Medio: al Basso "
        "l'immagine si blocca, ma la voce continua.%n%nLa scheda Video dello schermo del telefono mostra la "
        "chiamata a tutto schermo e accende o spegne ogni fotocamera."),
    "mobile_phone": ("Un telefono in tasca",
        "Un telefono che va dove vai tu. Il Telefono cellulare prende il servizio dalle antenne: circa 64 blocchi "
        "da una T1 in campo aperto, il doppio da una T2.%n%nPrende il suo numero al primo uso e funziona "
        "dall'inventario o da uno slot bauble. Il tasto Telefono (N, se non l'hai cambiato) risponde, riaggancia o "
        "apre il suo schermo.%n%nLa batteria tiene 1.200.000 EU e consuma 4 EU/t in attesa, 16 in chiamata."),
    "mobile_call": ("Mi senti adesso?",
        "Fai o ricevi una chiamata con un Telefono cellulare. Tienilo in mano per vedere le tacche.%n%nIl maltempo "
        "si fa sentire: la pioggia toglie 3 dB al servizio di un telefono, un temporale 6. Non sai dove c'è "
        "copertura? Lo schermo di un'antenna mostra fin dove arriva il suo segnale.%n%nIn chiamata, aggiungi altri "
        "telefoni dalla tastiera: si parla fino in 8."),
    "me_phone": ("Senza fili dappertutto",
        "Hai una rete ME con i punti d'accesso wireless? Allora la copertura ce l'hai già. Un Telefono ME è un "
        "Telefono cellulare collegato a una rete ME.%n%nCollegalo a un Terminale di sicurezza ME, come un Terminale "
        "wireless. Ovunque arrivino i Punti d'accesso wireless ME di quella rete, amplificatori compresi, ha "
        "servizio al livello Massimo. Si carica in un Caricatore di AE2.%n%nI Telefoni ME della stessa rete ME si "
        "chiamano senza nessuna antenna."),
    "me_bridge": ("Unire le reti",
        "Un Ponte ME unisce una rete ME alla tua rete telefonica, così i Telefoni ME possono chiamare qualsiasi "
        "telefono.%n%nMettilo su un cavo ME, dove prende un canale, e collegalo con la chiave a una Testa di "
        "traliccio, un Terminale di linea o un'antenna entro 16 blocchi. Usa 512 EU/t."),
    "me_bridge_call": ("Interurbana",
        "Il tuo Telefono ME può arrivare oltre la tua rete ME. Da un Telefono ME, chiama un Telefono su una linea, "
        "un Telefono cellulare o un Videotelefono, attraverso un Ponte ME."),
    "portal_relay": ("Attraverso il portale",
        "La radio da sola non passa da una dimensione all'altra. Un Ripetitore di portale funziona entro 4 blocchi "
        "da un portale del Nether, dell'End, del Twilight Forest o di Personal Space, con 32 EU/t di corrente "
        "MV.%n%nCollegalo con la chiave a un'antenna, una Testa di traliccio o un Terminale di linea della tua "
        "rete. Poi usa la chiave sul ripetitore, attraversa il portale e usala su un ripetitore dall'altra parte "
        "entro 5 minuti. La coppia unisce le due reti al livello Alto."),
    "satellite_rocket": ("Carico utile",
        "Un Satellite è un emitter e un sensor HV con due pannelli solari. Mettilo sui pezzi del razzo di livello 1 "
        "di Galacticraft nell'Assembler, e hai un Razzo per satelliti.%n%nLascia stare le piattaforme di "
        "Galacticraft: questo razzo parte solo da una Rampa di lancio per satelliti."),
    "first_satellite": ("Decollo",
        "3, 2, 1... La Rampa di lancio per satelliti è un intero complesso di lancio: una rampa "
        "di Clean Stainless Steel Machine Casing intorno a una fossa di scarico, una torre di servizio dietro con un "
        "Input Bus per i razzi alla base, un serbatoio del propellente con un Input Hatch per il carburante, i "
        "parafulmini, e un Energy Hatch e un Maintenance Hatch sul bordo anteriore. Tieni il cielo aperto sopra la "
        "fossa.%n%nUn lancio dura 30 secondi a 480 EU/t con 1.000 L di carburante, e mette un satellite "
        "in orbita sopra la dimensione della rampa. Costruisci la rampa e guarda partire il tuo primo satellite."),
    "five_satellites": ("Una costellazione",
        "Un satellite da solo si annoia. Mettine 5 sopra una dimensione e ogni telefono e radio sotto il suo cielo "
        "aperto ne riceve il segnale, al livello Basso. 14 danno il Medio, 37 l'Alto e 100 il Massimo.%n%nUn tetto "
        "indebolisce il segnale, e la pioggia costa qualche dB. I satelliti scendono dopo circa 100 giorni: "
        "continua a lanciarne."),
    "satellite_dish": ("Rete parabolica",
        "Perché tirare linee quando puoi rimbalzare su un satellite? Una Parabola satellitare collega la sua rete a "
        "ogni altra parabola della stessa dimensione, attraverso i satelliti in orbita.%n%nÈ una parabola di Clean "
        "Stainless Steel Machine Casing rivolta al cielo, con un illuminatore su quattro sostegni sopra di lei, su un "
        "piedistallo di Stainless Steel Frame Box; l'Energy Hatch e il Maintenance Hatch vanno nel basamento ai suoi "
        "piedi, accanto al controller. Usa 512 EU/t.%n%nCollegala con la chiave a un'antenna, una Testa "
        "di traliccio o un Terminale di linea. Tienila in ordine: ogni problema di manutenzione le costa 2 dB."),
    "rocket_receiver": ("Chiamare casa",
        "Il tuo razzo è parcheggiato su un altro pianeta: perché non renderlo utile? Metti un Ricevitore radio per "
        "razzi nella sua stiva mentre è fermo su una piattaforma di lancio, e il razzo diventa un'antenna lì, "
        "collegata ai satelliti dell'Overworld al livello Alto.%n%nIl razzo ha una stiva solo se è costruito con "
        "dei bauli. Nell'Overworld lo raggiungono i telefoni a cielo aperto e le reti con una parabola."),
    "call_between_dimensions": ("Fuori da questo mondo",
        "Chiama qualcuno in un'altra dimensione: con una coppia di Ripetitori di portale verso il Nether, l'End, il "
        "Twilight Forest o una dimensione personale, oppure attraverso i satelliti dell'Overworld verso un pianeta "
        "con un Ricevitore radio per razzi."),
}
