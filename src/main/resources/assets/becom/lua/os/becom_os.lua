-- BeCom OS: runs a phone's Lua apps. One app is in the foreground, on screen; the rest wake for messages.
-- An app is one file returning a table of callbacks:
--   init()            once, when it loads
--   draw()            returns the widgets to show (a table of widget tables)
--   on(id, value)     a toggle, a button or an input was used
--   message(from, d)  data from another phone or a computer, by number
--   tick()            about once a second while it's loaded
-- and it talks to the phone through the global `phone` table.

local system = ...
-- The OS's own references, taken before any app runs: apps get copies of the libraries, never these tables, so
-- nothing an app does to its globals reaches the OS or another app.
local pack, unpack, concat = table.pack, table.unpack, table.concat
local floor, huge = math.floor, math.huge
local pcall, type, tostring, tonumber, pairs, load = pcall, type, tostring, tonumber, pairs, load
local becom = component.proxy(component.list("becom")())
local storage
for address in component.list("filesystem") do
  if address ~= system and address ~= computer.tmpAddress() then
    storage = component.proxy(address)
    break
  end
end

local apps = {}
local foreground = nil
local LIMIT = becom.storageLimit()
local TICK = 1

local function crashed(id, reason)
  apps[id] = nil
  becom.crashed(id, tostring(reason))
end

local function call(id, name, ...)
  local app = apps[id]
  if not app then return nil end
  local f = app.impl[name]
  if type(f) ~= "function" then return nil end
  local result = pack(pcall(f, ...))
  if not result[1] then
    crashed(id, result[2])
    return nil
  end
  return unpack(result, 2, result.n)
end

local function redraw(id)
  if foreground ~= id or not apps[id] then return end
  local tree = call(id, "draw")
  if apps[id] then becom.show(id, type(tree) == "table" and tree or {}) end
end

-- An app's files live in /data/<id>/ on the phone's storage; names are plain.
local function path(id, name)
  name = tostring(name)
  if not name:match("^[%w_%-%.]+$") or name:match("^%.+$") then error("bad file name: " .. name, 3) end
  return "/data/" .. id .. "/" .. name
end

-- The bytes an app keeps in its folder, less the file it's about to replace.
local function stored(id, except)
  local total = 0
  for _, name in pairs(storage.list("/data/" .. id) or {}) do
    local file = "/data/" .. id .. "/" .. name
    if file ~= except then total = total + (storage.size(file) or 0) end
  end
  return total
end

-- A fresh copy of a library for one app.
local function copy(lib)
  local out = {}
  for k, v in pairs(lib) do out[k] = v end
  return out
end

local function sandbox(id)
  local env = {
    assert = assert, error = error, ipairs = ipairs, next = next, pairs = pairs, pcall = pcall,
    rawequal = rawequal, rawget = rawget, rawlen = rawlen, rawset = rawset, select = select,
    setmetatable = setmetatable, getmetatable = getmetatable, tonumber = tonumber, tostring = tostring,
    type = type, xpcall = xpcall, _VERSION = _VERSION,
    string = copy(string),
    table = copy(table),
    math = copy(math),
    os = { time = os.time, clock = os.clock, date = os.date },
    unicode = copy(unicode),
  }
  env._G = env
  -- What the app may do beyond its screen is what its --@uses line says; the server checks it again.
  local can = becom.uses(id) or {}
  local function refused(use)
    return nil, "not allowed: the app doesn't say --@uses " .. use
  end
  local net, notify, files, dial, camera = can.net, can.notify, can.storage and storage, can.call, can.camera
  env.phone = {
    number = function() return becom.number() end,
    signal = function() return becom.signal() end,
    foreground = function() return foreground == id end,
    redraw = function() redraw(id) end,
    send = function(number, data)
      if not net then return refused("net") end
      return becom.send(id, floor(tonumber(number) or -1), tostring(data))
    end,
    notify = function(title, text)
      if not notify then return refused("notify") end
      return becom.notify(id, tostring(title), tostring(text or ""))
    end,
    call = function(number)
      if not dial then return refused("call") end
      if foreground ~= id then return nil, "not in the foreground" end
      return becom.dial(id, floor(tonumber(number) or -1))
    end,
    camera = function()
      if not camera then return refused("camera") end
      if foreground ~= id then return nil, "not in the foreground" end
      return becom.camera(id)
    end,
    read = function(name)
      if not files then return refused("storage") end
      local file = path(id, name)
      if not storage.exists(file) then return nil end
      local handle = storage.open(file, "r")
      local chunks = {}
      while true do
        local data = storage.read(handle, huge)
        if not data then break end
        chunks[#chunks + 1] = data
      end
      storage.close(handle)
      return concat(chunks)
    end,
    write = function(name, data)
      if not files then return refused("storage") end
      local file = path(id, name)
      data = tostring(data)
      if stored(id, file) + #data > LIMIT then return nil, "storage full" end
      storage.makeDirectory("/data/" .. id)
      local handle, reason = storage.open(file, "w")
      if not handle then return nil, reason end
      local ok, why = storage.write(handle, data)
      storage.close(handle)
      return ok, why
    end,
    remove = function(name)
      if not files then return false end
      return storage.remove(path(id, name))
    end,
    list = function()
      if not files then return {} end
      return storage.list("/data/" .. id) or {}
    end,
    used = function()
      if not files then return 0, LIMIT end
      return stored(id), LIMIT
    end,
    uptime = computer.uptime,
  }
  return env
end

local function load_app(id)
  if apps[id] then return apps[id] end
  local source = becom.source(id)
  if not source then return nil end
  local chunk, reason = load(source, "=" .. id .. ".lua", "t", sandbox(id))
  if not chunk then
    becom.crashed(id, reason)
    return nil
  end
  local ok, impl = pcall(chunk)
  if not ok then
    becom.crashed(id, impl)
    return nil
  end
  apps[id] = { impl = type(impl) == "table" and impl or {} }
  call(id, "init")
  return apps[id]
end

becom.ready()
local last = computer.uptime()
while true do
  local signal = pack(computer.pullSignal(TICK))
  local name = signal[1]
  if name == "becom_open" then
    foreground = signal[2]
    if load_app(foreground) then redraw(foreground) end
  elseif name == "becom_close" then
    foreground = nil
  elseif name == "becom_event" then
    local id = signal[2]
    if apps[id] then
      call(id, "on", signal[3], signal[4])
      redraw(id)
    end
  elseif name == "becom_message" then
    local id = signal[2]
    if load_app(id) then
      call(id, "message", signal[3], signal[4])
      redraw(id)
    end
  elseif name == "becom_unload" then
    apps[signal[2]] = nil
    if foreground == signal[2] then foreground = nil end
  end
  if computer.uptime() - last >= TICK then
    last = computer.uptime()
    for id in pairs(apps) do call(id, "tick") end
    if foreground then redraw(foreground) end
  end
end
