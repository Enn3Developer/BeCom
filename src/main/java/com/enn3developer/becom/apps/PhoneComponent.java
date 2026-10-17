package com.enn3developer.becom.apps;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppTree;

import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.ManagedEnvironment;

/**
 * The {@code becom} component inside a phone: what BeCom OS calls to show an app, send its data, notify, read the
 * signal and dial. Not direct, so every call runs on the server thread, where BeCom's state lives. Apps don't call it
 * themselves: the OS hands them the {@code phone} table, which checks what's allowed in the background.
 * <p>
 * Public because OC calls callbacks by reflection: on a package-private class every call quietly returns nothing.
 */
public final class PhoneComponent extends ManagedEnvironment {

    private final PhoneMachine host;
    private final AppService apps;

    PhoneComponent(PhoneMachine host, AppService apps) {
        this.host = host;
        this.apps = apps;
        setNode(
            Network.newNode(this, Visibility.Neighbors)
                .withComponent("becom")
                .create());
    }

    @Callback(doc = "function() -- BeCom OS is up and takes signals.")
    public Object[] ready(Context context, Arguments args) {
        apps.ready(host);
        return new Object[0];
    }

    @Callback(doc = "function():number -- This phone's number.")
    public Object[] number(Context context, Arguments args) {
        return new Object[] { host.number };
    }

    @Callback(doc = "function():string -- The signal: none, low, medium, high or max.")
    public Object[] signal(Context context, Arguments args) {
        return new Object[] { apps.signal(host.number) };
    }

    @Callback(doc = "function(id:string):string -- An installed app's source.")
    public Object[] source(Context context, Arguments args) {
        String source = apps.source(host.number, args.checkString(0));
        return source == null ? new Object[] { null } : new Object[] { source };
    }

    @Callback(doc = "function(id:string, widgets:table) -- What the app in the foreground shows.")
    public Object[] show(Context context, Arguments args) {
        apps.show(host.number, args.checkString(0), AppTree.parse(normalize(args.checkTable(1), 0)));
        return new Object[0];
    }

    @Callback(doc = "function(id:string, reason:string) -- The app crashed.")
    public Object[] crashed(Context context, Arguments args) {
        apps.crashed(host.number, args.checkString(0), args.optString(1, "?"));
        return new Object[0];
    }

    @Callback(doc = "function(id:string, number:number, data:string):boolean[, string] -- Sends data by number.")
    public Object[] send(Context context, Arguments args) {
        String reason = apps.permit(host.number, args.checkString(0), AppManifest.Use.NET);
        if (reason == null)
            reason = apps.send(host.number, args.checkString(0), args.checkInteger(1), args.checkByteArray(2));
        return reason == null ? new Object[] { true } : new Object[] { null, reason };
    }

    @Callback(doc = "function(id:string, title:string, text:string) -- A notification.")
    public Object[] notify(Context context, Arguments args) {
        apps.notify(host.number, args.checkString(0), args.checkString(1), args.optString(2, ""));
        return new Object[] { true };
    }

    @Callback(doc = "function(id:string, number:number):boolean -- The app on screen calls a number from this phone.")
    public Object[] dial(Context context, Arguments args) {
        return new Object[] { apps.dial(host, args.checkString(0), args.checkInteger(1)) };
    }

    @Callback(doc = "function(id:string):table|nil -- What the app on screen sees: the block the holder looks at.")
    public Object[] camera(Context context, Arguments args) {
        return new Object[] { apps.camera(host, args.checkString(0)) };
    }

    @Callback(doc = "function(id:string):table -- What the app says it uses, as {net = true, ...}.")
    public Object[] uses(Context context, Arguments args) {
        String id = args.checkString(0);
        Map<String, Boolean> uses = new HashMap<>();
        for (AppManifest.Use use : AppManifest.Use.VALUES)
            if (apps.permit(host.number, id, use) == null) uses.put(use.word(), true);
        return new Object[] { uses };
    }

    @Callback(doc = "function():number -- The bytes each app may keep in its own storage.")
    public Object[] storageLimit(Context context, Arguments args) {
        return new Object[] { apps.storageLimit() };
    }

    /** A Lua table as plain Java: strings as strings, wherever OpenComputers handed them over as bytes. */
    static Object normalize(Object o, int depth) {
        if (o instanceof byte[]bytes) return new String(bytes, StandardCharsets.UTF_8);
        if (!(o instanceof Map<?, ?>map)) return o;
        // Past this, the rest is dropped, not handed on as OC's live table: a table may hold itself.
        if (depth > 8) return Map.of();
        Map<Object, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet())
            out.put(normalize(e.getKey(), depth + 1), normalize(e.getValue(), depth + 1));
        return out;
    }
}
