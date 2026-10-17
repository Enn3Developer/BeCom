-- BeCom phone EEPROM: boots BeCom OS from the phone's read-only system disk.
local disk
for address in component.list("filesystem") do
  if component.invoke(address, "exists", "/becom_os.lua") then
    disk = address
    break
  end
end
if not disk then error("BeCom OS is missing", 0) end
local handle = assert(component.invoke(disk, "open", "/becom_os.lua"))
local chunks = {}
while true do
  local data = component.invoke(disk, "read", handle, math.huge)
  if not data then break end
  chunks[#chunks + 1] = data
end
component.invoke(disk, "close", handle)
local boot, reason = load(table.concat(chunks), "=becom_os", "t", _G)
if not boot then error(reason, 0) end
boot(disk)
