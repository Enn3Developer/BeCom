"""Italian quest text for make_quests.py, by quest key: (name, description). Every quest needs an entry here."""

LINE_IT = ("Better Communications",
           "Telefoni su linee di tralicci, radio, telefoni cellulari e satelliti, portati dalla chat vocale.")
QUESTS_IT = {
    "phone_cable": ("Cablato per il suono",
        "Tutto comincia da un filo. Il Cavo telefonico è filo di red alloy nella gomma, fatto nell'assembler. Collega "
        "un Telefono a un Terminale di linea, fino a 32 blocchi, e non perde nulla per strada."),
    "telephone": ("Driin, driin",
        "Un Telefono: un circuito, un note block per la suoneria e un corpo d'acciaio, assemblati con un filo di Cavo "
        "telefonico. Clic destro per alzare la cornetta. È tuo, e lo è anche il suo numero di sei cifre."),
    "line_terminal": ("Capolinea",
        "Ogni linea inizia e finisce in un Terminale di linea. Va a 4 EU/t di corrente LV, serve un solo telefono sul "
        "suo cavo e si collega a una Testa di traliccio: usa la chiave sul terminale, poi sulla testa."),
    "pylon": ("Sul palo",
        "Una Testa di traliccio in cima a una colonna di 6-16 Steel Frame Box porta la linea. Due tralicci si collegano "
        "fino a quattro volte l'altezza del più basso, se non c'è niente di solido in mezzo. I tralicci non vogliono "
        "corrente e funzionano anche nei chunk scaricati. La linea perde segnale a ogni blocco: prevedi un ripetitore "
        "almeno ogni 225 blocchi."),
    "first_call": ("Pronto? Mi senti?",
        "Due Telefoni, ognuno sul suo Terminale di linea, e i tralicci in mezzo. Alza la cornetta: se sulla linea c'è un "
        "solo altro telefono squilla subito, altrimenti componi un numero o scegli un contatto. Fai una chiamata che si "
        "connette. La voce al Telefono è stretta e ruvida, ma arriva fin dove arriva la linea."),
    "repeater": ("Tenere vivo il segnale",
        "Una Testa ripetitore è una Testa di traliccio che rigenera il segnale, così la perdita di linea riparte da zero "
        "dopo di lei. Prende 4 EU/t di corrente LV da un cavo su qualsiasi lato e funziona solo se caricata e "
        "alimentata."),
    "router": ("Centralino",
        "Una linea semplice unisce esattamente due telefoni. Una Testa router ha 15 collegamenti e trasforma le linee "
        "in una rete, dove ogni telefono può chiamare qualsiasi altro. Rigenera il segnale come un ripetitore e prende "
        "16 EU/t di corrente LV da un cavo."),
    "antenna_t1": ("In onda",
        "Una Testa d'antenna T1 sta su un albero di 4-32 Steel Frame Box, sopra una base 3x3 di LV Machine Casing con un "
        "Energy Hatch, e usa 32 EU/t. Arriva a circa 64 blocchi in campo aperto; l'altezza aiuta, colline e tetti costano "
        "segnale. Le antenne si collegano tra loro da sole. Collegala con la chiave a una Testa di traliccio o a un "
        "Terminale di linea entro 16 blocchi per portare quella linea via radio."),
    "transceiver": ("Stazione base",
        "Un Ricetrasmettitore è una radio su un blocco, a MV e 32 EU/t. Stagli entro 2 blocchi e tieni premuto il tasto "
        "per parlare della radio, Bloc Maiusc se non l'hai cambiato, per parlare sul suo canale. Il suo altoparlante "
        "riproduce il canale a chiunque sia entro 16 blocchi. Da solo arriva a circa 125 blocchi in campo aperto."),
    "signal_analyzer": ("Leggere l'aria",
        "Tieni in mano un Analizzatore di segnale per vedere il tuo segnale radio: la forza in dB, il livello, l'antenna "
        "che ti serve e cosa costa di più, tra distanza, terreno, copertura, meteo e rumore. Un collegamento vuole 6 dB "
        "per il livello Basso e 12 per il Medio. Sotto 4 cade subito; tra 4 e 6 regge 3 secondi, poi cade."),
    "walkie_talkie": ("Radio tascabile",
        "Un Walkie-talkie va con la sua batteria MV: si carica come qualsiasi attrezzo GT. Clic destro per scegliere uno "
        "dei 16 canali o spegnerlo, oppure accovacciati e usa la rotellina per cambiare canale. Tieni premuto il tasto "
        "per parlare per trasmettere. Da solo arriva a circa 80 blocchi e molto più lontano con le antenne, e chiunque "
        "entro 6 blocchi sente quello che riceve."),
    "radio_contact": ("Passo e chiudo",
        "Parla con qualcuno via radio, o sentilo: con un walkie, con un Ricetrasmettitore o attraverso le antenne tra "
        "voi. I canali sono aperti a chiunque sia sintonizzato. Se due parlano insieme, ognuno sente il più forte."),
    "antenna_t2": ("Un albero più alto",
        "Un'Antenna T2 sta su MV Machine Casing con un albero di Aluminium Frame Box e usa 128 EU/t. Arriva a circa 128 "
        "blocchi, il doppio di una T1: ogni 6 dB di bilancio di collegamento raddoppiano la portata in campo aperto. Le "
        "antenne ripetono tra loro tutti i 16 canali radio e portano le chiamate tra le linee agganciate a loro."),
    "antenna_t3": ("Oltre la valle",
        "Un'Antenna T3 sta su HV Machine Casing con un albero di Stainless Steel Frame Box e usa 512 EU/t. Arriva a "
        "circa 256 blocchi. Costruiscila in alto: rileva il terreno intorno ogni cinque minuti, e una collina in mezzo "
        "può costare più della distanza."),
    "videophone": ("Faccia a faccia",
        "Un Videotelefono è un vivavoce con uno schermo, a MV e 32 EU/t. Collegalo con Cavo telefonico a un Terminale di "
        "linea, come un Telefono. Clic destro per comporre, rispondere o riagganciare. Chiunque sia entro 4 blocchi è "
        "nella sua chiamata e sul suo schermo, e il suo altoparlante porta la chiamata a 16 blocchi. Riaggancia da solo "
        "30 secondi dopo che l'ultima persona se ne va."),
    "video_call": ("Vedere per credere",
        "Vedi qualcuno in una videochiamata: con un Videotelefono, un Telefono cellulare o un Telefono ME, con la sua "
        "fotocamera frontale accesa. La frontale mostra il tuo personaggio, la posteriore quello che vedi, nelle "
        "chiamate a due al livello Alto o meglio. Il video vuole il livello Medio: al Basso l'immagine si blocca e la "
        "voce continua. La scheda Video dello schermo del telefono mostra la chiamata a tutto schermo e accende o spegne "
        "ogni fotocamera."),
    "mobile_phone": ("Un telefono in tasca",
        "Un Telefono cellulare prende il servizio dalle antenne, sulla banda delle chiamate: circa 64 blocchi da una T1 "
        "in campo aperto, il doppio da una T2. Prende il suo numero al primo uso e funziona dall'inventario o da uno "
        "slot bauble. La sua batteria tiene 1.200.000 EU e consuma 4 EU/t in attesa, 16 in chiamata. Il tasto "
        "Telefono, N se non l'hai cambiato, risponde, riaggancia o apre il suo schermo."),
    "mobile_call": ("Mi senti adesso?",
        "Fai o ricevi una chiamata con un Telefono cellulare. Le sue tacche mostrano il servizio mentre lo tieni in "
        "mano. La pioggia toglie 3 dB al servizio di un telefono, un temporale 6, e lo schermo di un'antenna mostra fin "
        "dove arriva il suo segnale. In chiamata, aggiungi altri telefoni dalla tastiera: si parla fino in 8."),
    "me_phone": ("Senza fili dappertutto",
        "Un Telefono ME è un Telefono cellulare collegato a una rete ME. Collegalo a un Terminale di sicurezza ME, come "
        "un Terminale wireless: ha servizio al livello Massimo ovunque arrivino i Punti d'accesso wireless ME di quella "
        "rete, amplificatori compresi, e si carica in un Caricatore. I Telefoni ME della stessa rete ME si chiamano senza "
        "antenne."),
    "me_bridge": ("Unire le reti",
        "Un Ponte ME unisce una rete ME a una rete BeCom, così i Telefoni ME possono chiamare qualsiasi telefono. "
        "Mettilo su un cavo ME, dove prende un canale, e collegalo con la chiave a una Testa di traliccio, un Terminale "
        "di linea o un'antenna entro 16 blocchi. Usa 512 EU/t."),
    "me_bridge_call": ("Interurbana",
        "Chiama da un Telefono ME, attraverso un Ponte ME, un telefono fuori dalla tua rete ME: un Telefono su una linea, "
        "un Telefono cellulare o un Videotelefono."),
    "portal_relay": ("Attraverso il portale",
        "Un Ripetitore di portale funziona entro 4 blocchi da un portale del Nether, un portale dell'End, un portale del "
        "Twilight Forest o un portale di Personal Space, con 32 EU/t di corrente MV. Collegalo con la chiave a "
        "un'antenna, una Testa di traliccio o un Terminale di linea della tua rete. Poi usa la chiave su di lui, "
        "attraversa il portale e usala su un ripetitore dall'altra parte entro 5 minuti: la coppia unisce le due reti "
        "al livello Alto."),
    "satellite_rocket": ("Carico utile",
        "Un Satellite è un emitter e un sensor HV con due pannelli solari. Mettilo sui pezzi del razzo di livello 1 di "
        "Galacticraft nell'assembler per fare un Razzo per satelliti. Non parte dalle piattaforme di Galacticraft: serve "
        "una Rampa di lancio per satelliti."),
    "first_satellite": ("Decollo",
        "La Rampa di lancio per satelliti è un pavimento 5 per 5 di Clean Stainless Steel Machine Casing a cielo "
        "aperto, con un bus d'ingresso per i razzi, un portello d'ingresso per il carburante, un energy hatch e un "
        "maintenance hatch. Un lancio dura 30 s a 480 EU/t con 1.000 L di carburante, e mette un satellite in orbita "
        "sopra la dimensione della rampa."),
    "five_satellites": ("Una costellazione",
        "Con 5 satelliti sopra una dimensione, ogni telefono e radio sotto il suo cielo aperto ne riceve il segnale, al "
        "livello Basso; 14 danno il Medio, 37 l'Alto e 100 il Massimo. Un tetto lo indebolisce, e la pioggia costa "
        "qualche dB. I satelliti scendono dopo circa 100 giorni: continua a lanciarne."),
    "satellite_dish": ("Rete parabolica",
        "Una Parabola satellitare collega la sua rete a ogni altra parabola della stessa dimensione, attraverso i "
        "satelliti. È una parabola 5 per 5 di Clean Stainless Steel Machine Casing su un supporto di tre Stainless Steel "
        "Frame Box, con un energy hatch e un maintenance hatch, a cielo aperto, a 512 EU/t. Collegala con la chiave a "
        "un'antenna, una Testa di traliccio o un Terminale di linea. Ogni problema di manutenzione le costa 2 dB."),
    "rocket_receiver": ("Chiamare casa",
        "Un Ricevitore radio per razzi nella stiva di un razzo fermo su una piattaforma di lancio di un pianeta fa del "
        "razzo un'antenna lì, e il collegamento del pianeta con i satelliti dell'Overworld, al livello Alto. Il razzo "
        "ha una stiva solo se è costruito con dei bauli. Nell'Overworld lo raggiungono i telefoni a cielo aperto e le reti con una "
        "parabola."),
    "call_between_dimensions": ("Fuori da questo mondo",
        "Chiama qualcuno in un'altra dimensione: con una coppia di Ripetitori di portale verso il Nether, l'End, il "
        "Twilight Forest o una dimensione personale, oppure attraverso i satelliti dell'Overworld verso un pianeta con "
        "un Ricevitore radio per razzi."),
}
