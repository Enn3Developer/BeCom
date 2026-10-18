---
navigation:
  title: Phone apps
  parent: index.md
  position: 45
  icon: becom:becom_card
item_ids:
  - becom:becom_card:*
  - becom:machine:25022
quest_ids:
  - "n3P0tSabXDS4eCuzXQVCYQ"
---

# Phone apps

Mobile Phones and ME Phones run apps written in Lua. Each phone holds an OpenComputers computer that starts when an app
opens, or when a background app has work, and stops when nothing needs it. While it runs, apps draw 8 EU/t from the
phone's battery: **Settings › Battery** lists today's drain by use, Standby, Calls, Video and Screen, and each app by
name, with its share.

## Getting apps

- **App Store:** the App Store tile on the home screen, while the phone reaches an App Store Mirror (below). Its tabs
  list the **Popular** apps, the **New** ones and the ones **Installed** on your phone. An app's page says what it may do, when it was last updated and who sees it, and has
  **Report** to flag it to the operators. Anyone can publish an app from a computer: their phone asks them first, for
  everyone or for their team only, and it goes in under their name, 32 apps each at most. Operators can list the
  reports, hide an app, read its source, or take it out for good.
- **From a computer:** a computer with a BeCom Card offers an app to a phone by its number. The holder sees the offer on
  the home screen, with who sent it, from where and what it may do, and installs or cancels it. Only install apps from
  people you trust.

A phone holds 16 apps. **Settings › Apps** lists them. Each app's page there has a **Run in background** switch, how
often it woke and how much battery it used today, a switch under **It can** for each thing the app declares (Network,
Notifications, Calls, Camera), how much of its storage it uses, and **Uninstall**. Switching one off denies it to the
app even though it asked for it. An app that can't reach its computer, out of signal or with the computer off, has
**Retry**; one that crashed shows the error it stopped at.

## The App Store Mirror

The App Store is the whole server's, but phones get to it only through an
<ItemLink id="becom:machine:25022" showIcon="left">App Store Mirror</ItemLink>: an MV machine, 64 EU/t, cabled on any
face to a bus. A phone with a path to a working mirror, through antennas, routers and everything else that carries a
call, opens the store; with none, the store says so. Every mirror leads to the same store.

An install is a download: the app comes down from the mirror the phone reaches fastest, at that path's rate, and the
button fills as it does. It takes what calls leave of each link, like any data, so a 64 KB app takes 16 seconds at
Low and moments on fibre. Out of reach halfway, it stops. A phone downloads one app at a time. Installed apps run
without a mirror; updating one is a download again.

A computer publishes through a mirror too: `becom publish` needs one in the computer's reach. A screwdriver keeps a
mirror for its owner's team, and again opens it to everyone. Its screen and WAILA show the downloads it serves now and
today.

## The BeCom Card

<ItemLink id="becom:becom_card:0" showIcon="left">BeCom Card (Radio)</ItemLink> and
<ItemLink id="becom:becom_card:1" showIcon="left">BeCom Card (Wired)</ItemLink> are OpenComputers cards. They give the
computer a number and the `becom` component. The radio card has signal like a Mobile Phone held where the computer
stands. The wired card joins the bus whose cable is joined to the computer, at Max and at its cable's rate.

The tools disk, **becom**, is an OpenComputers loot disk: craft any loot disk, such as OpenOS, with a wrench, and
again, until it turns into this one. Its `becom` program does the common jobs:

| Command | What it does |
| --- | --- |
| `becom number` | This computer's number and signal |
| `becom install <file> <number>` | Offers an app to a phone, and waits for the answer |
| `becom publish <file>` | Asks the nearest player, on their phone, to publish an app under their name |
| `becom send <number> <app> <text>` | Sends data to an app on a phone, or to a computer |
| `becom listen` | Prints data as it arrives |

## Data

Apps and computers send each other data by number, up to 8 KB at a time. Data travels like a call, so both ends need a
path between them, and its speed is the slower end's rate: 4, 16, 64 or 256 KB/s by radio from Low to Max, or a
bus's rate on cable. A sender's messages go one after another at that rate. Data takes what calls leave of each link: calls
always come first, and when they fill a link, data waits. The Router, the Exchange and every backhaul show what
data takes. An app in the background wakes for data at most once a minute, and data waits for it until then; data for
an app that isn't installed, doesn't use `net`, or isn't allowed in the background while the phone's apps are stopped,
is dropped. Under an app that shows data, the screen says how old the last data is once it's a few seconds old.

## Writing an app

An app is one Lua file, up to 64 KB. Comment lines at the top name it:

```lua
--@name Notes
--@version 1.0
--@accent 2a6aaa
--@about A note kept on the phone.
--@uses storage, notify
--@icon 0000000000aaaa000a0000a00a0ff0a00a0000a00a0ff0a000aaaa0000000000
```

`--@uses` says what the app may do beyond drawing its screen, and an app that doesn't say gets none of it: `net` to
send and receive data, `notify` for notifications, `storage` for its own files, `call` to place calls while it's on
screen, and `camera` to see what the holder looks at while it's on screen. `--@icon` is 8 by 8 cells, a row at a time
from the top, each a text colour code from 1 to f, 0 for none; it shows on the app's tile and in the store.

The file returns a table of functions, all optional: `init()` once when it loads, `draw()` returning the widgets to
show, `on(id, value)` when a button, toggle or input is used, `message(from, data)` when data arrives, and `tick()` about
once a second. `draw()` runs again after each of these.

| Widget | Fields |
| --- | --- |
| `heading`, `text` | `text` |
| `value` | `label`, `value`, `level` |
| `progress` | `label`, `value` from 0 to 1, `level` |
| `toggle` | `id`, `label`, `on` |
| `button` | `id`, `label`, `style`: `go` or `danger` |
| `item` | `label`, `item`, `value` |
| `input` | `id`, `hint` |

A `level` is `low`, `medium`, `high` or `max`, and colours the widget like a signal. The global `phone` table holds the
rest: `number()`, `signal()`, `foreground()`, `redraw()`, `send(number, data)`, `notify(title, text)`, `call(number)`
and `camera()` (only on screen), `uptime()`, and `read`, `write`, `remove`, `list` and `used()` for the app's own files.
`camera()` returns a table for the block or creature the holder looks at, up to 16 blocks away: its `kind`, `name`,
`x`, `y`, `z` and `distance`. A phone's storage is 256 KB, and an app keeps 64 KB of it at most. What an app may not do
returns nil and the reason.
The tools disk holds `apps/notes.lua`, a short example of all of it.
