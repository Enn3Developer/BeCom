--@name Notes
--@version 1.0
--@accent 2a6aaa
--@about A note kept on the phone, and a counter: an example BeCom app.
--@uses storage, notify
--@icon 0000000000aaaa000a0000a00a0ff0a00a0000a00a0ff0a000aaaa0000000000
-- Install it with: becom install /mnt/xxx/apps/notes.lua <phone number>
local note = ""
local count = 0

return {
  init = function()
    note = phone.read("note.txt") or ""
    count = tonumber(phone.read("count.txt") or "0") or 0
  end,
  draw = function()
    return {
      { type = "heading", text = "Notes" },
      { type = "text", text = note ~= "" and note or "No note yet." },
      { type = "input", id = "note", hint = "Write a note" },
      { type = "value", label = "Counter", value = count },
      { type = "button", id = "add", label = "Add one", style = "go" },
      { type = "button", id = "clear", label = "Clear", style = "danger" },
      { type = "value", label = "Signal", value = phone.signal() },
    }
  end,
  on = function(id, value)
    if id == "note" then
      note = value
      phone.write("note.txt", note)
    elseif id == "add" then
      count = count + 1
      phone.write("count.txt", tostring(count))
    elseif id == "clear" then
      note, count = "", 0
      phone.write("note.txt", "")
      phone.write("count.txt", "0")
    end
  end,
  message = function(from, data)
    note = tostring(data)
    phone.write("note.txt", note)
    phone.notify("Note from " .. tostring(from), note)
  end,
}
