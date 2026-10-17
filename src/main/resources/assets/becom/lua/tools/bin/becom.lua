-- becom: talk to BeCom from a computer with a BeCom Card.
--   becom number                     this computer's number and signal
--   becom install <file> <number>    offer an app to a phone; its holder accepts or declines
--   becom publish <file>             publish an app to the App Store; you confirm on your phone
--   becom send <number> <app> <text> send data to an app on a phone, or to a computer
--   becom listen                     print data as it arrives, until Ctrl+C
local component = require("component")
local event = require("event")
local shell = require("shell")

if not component.isAvailable("becom") then
  io.stderr:write("No BeCom Card in this computer.\n")
  return 1
end
local becom = component.becom
local args = shell.parse(...)
local command = args[1]

local function read(path)
  local file, reason = io.open(shell.resolve(path), "r")
  if not file then
    io.stderr:write(tostring(reason) .. "\n")
    os.exit(1)
  end
  local source = file:read("*a")
  file:close()
  return source
end

if command == "number" then
  print("Number " .. tostring(becom.number()) .. ", signal " .. becom.signal())
elseif command == "install" and args[2] and tonumber(args[3]) then
  local source = read(args[2])
  local ok, reason = becom.install(tonumber(args[3]), source)
  if not ok then
    io.stderr:write("Can't offer it: " .. tostring(reason) .. "\n")
    return 1
  end
  print(#source .. " bytes. Asking phone " .. args[3] .. "...")
  local _, number, result = event.pull(70, "becom_install")
  print(result and ("Phone " .. tostring(number) .. ": " .. result) or "No answer.")
elseif command == "publish" and args[2] then
  local ok, reason = becom.publish(read(args[2]))
  if not ok then
    io.stderr:write("Can't publish it: " .. tostring(reason) .. "\n")
    return 1
  end
  print("Asking you on your phone...")
  local _, _, result = event.pull(70, "becom_publish")
  print(result and ("App Store: " .. tostring(result)) or "No answer.")
elseif command == "send" and tonumber(args[2]) and args[3] and args[4] then
  local ok, reason = becom.send(tonumber(args[2]), args[3], table.concat(args, " ", 4))
  print(ok and "Sent." or ("Not sent: " .. tostring(reason)))
elseif command == "listen" then
  print("Listening. Ctrl+C stops.")
  while true do
    local name, from, app, data = event.pullMultiple("becom_message", "interrupted")
    if name == "interrupted" then break end
    print(string.format("%s [%s]: %s", tostring(from), tostring(app), tostring(data)))
  end
else
  print("Usage:")
  print("  becom number")
  print("  becom install <file> <phone number>")
  print("  becom publish <file>")
  print("  becom send <number> <app> <text>")
  print("  becom listen")
end
