---
navigation:
  title: Componenti e ricette
  parent: index.md
  position: 85
  icon: becom:part:3
item_ids:
  - becom:part:*
---

# Componenti e ricette

BeCom fabbrica come fa GregTech:

- **I componenti** vengono dall'Assembler, o dal Circuit Assembler un livello di tensione sotto quello del componente.
- **Quel che piazzi** si fabbrica al banco attorno a uno scafo di macchina del suo livello. Lo fa anche l'Assembler, alla
  tensione del suo livello, e un po' più a buon mercato: metà dei circuiti, dei cavi GT, delle piastre e dei cavi BeCom.
- **I dispositivi portatili, le Line Hatch, i Rack di commutazione e tutto ciò che è ME** vengono solo dall'Assembler.

I fluidi accettano i sostituti di GTNH, e NEI mostra ogni alternativa:

| Fluido | Uno qualsiasi tra |
| --- | --- |
| Plastica | Polietilene; Politetrafluoroetilene a metà; Polibenzimidazolo a un sedicesimo |
| Gomma | Gomma; Gomma stirene-butadiene a tre quarti; Gomma siliconica a metà |
| Lega per saldatura | Lega per saldatura; Stagno al doppio; Piombo al quadruplo |

## Cavo

Il cavo si posa un blocco alla volta, su bus fino a 128, 512 e 4096 blocchi. Così ogni fabbricazione ne dà quattro, con
un quarto dell'isolante che prende un cavo GT.

| Cavo | Assembler | Quattro prendono |
| --- | --- | --- |
| <ItemLink id="becom:phone_cable:0" showIcon="left">Cavo telefonico</ItemLink> | LV | 8 fili sottili di rame, 144 L di gomma |
| <ItemLink id="becom:phone_cable:1" showIcon="left">Cavo coassiale</ItemLink> | MV | 4 fili sottili di rame ricotto, una lamina d'alluminio, 144 L di plastica |
| <ItemLink id="becom:phone_cable:2" showIcon="left">Cavo in fibra</ItemLink> | HV | 4 fibre di vetro, 144 L di plastica |

## Audio

Un <ItemLink id="becom:part:0" showIcon="left">Altoparlante</ItemLink> e un
<ItemLink id="becom:part:1" showIcon="left">Microfono</ItemLink> sono ciascuno un magnete, una bobina e una membrana.
Insieme su un filo, in un guscio di plastica, fanno una <ItemLink id="becom:part:2" showIcon="left">Cornetta</ItemLink>.
La Cornetta va nel Telefono e nel Videotelefono. L'Altoparlante e il Microfono vanno anche nel Walkie-talkie, nel
Ricetrasmettitore e nel Telefono cellulare.

<RecipeFor id="becom:part:0" />
<RecipeFor id="becom:part:1" />
<RecipeFor id="becom:part:2" />

## Moduli RF

Un Modulo RF è l'emitter e il sensor di un livello sulla sua scheda: il cuore di tutto ciò che trasmette.

| Modulo | Va in |
| --- | --- |
| <ItemLink id="becom:part:3" showIcon="left">Modulo RF (LV)</ItemLink> | Testa d'antenna T1 |
| <ItemLink id="becom:part:4" showIcon="left">Modulo RF (MV)</ItemLink> | Testa d'antenna T2, Ricetrasmettitore, Walkie-talkie, Ripetitore di portale, Scheda BeCom (radio) |
| <ItemLink id="becom:part:5" showIcon="left">Modulo RF (HV)</ItemLink> | Testa d'antenna T3, Telefono cellulare, Ripetitore di portale (HV), Satellite, Ricevitore radio per razzi, Rampa di lancio per satelliti, Parabola satellitare |
| <ItemLink id="becom:part:6" showIcon="left">Modulo RF (EV)</ItemLink> | Telefono ME, Ponte ME, Ripetitore di portale (EV) |

<RecipeFor id="becom:part:3" />
<RecipeFor id="becom:part:4" />
<RecipeFor id="becom:part:5" />
<RecipeFor id="becom:part:6" />

## Commutazione

Ogni livello commuta a modo suo.

| Componente | Livello | Va in |
| --- | --- | --- |
| <ItemLink id="becom:part:7" showIcon="left">Commutatore crossbar</ItemLink>: relè, barre incrociate | LV | Router, Rack di commutazione (LV), Centrale |
| <ItemLink id="becom:part:8" showIcon="left">Scheda di commutazione</ItemLink>: chip logici | MV | Router (MV), Rack di commutazione (MV), Ripetitore di portale |
| <ItemLink id="becom:part:9" showIcon="left">Commutatore di pacchetto</ItemLink>: un system on a chip e la sua memoria | HV | Router (HV), Rack di commutazione (HV), Ripetitore di portale (HV ed EV), Mirror dell'App Store |

<RecipeFor id="becom:part:7" />
<RecipeFor id="becom:part:8" />
<RecipeFor id="becom:part:9" />
