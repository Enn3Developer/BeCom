package com.enn3developer.becom.apps;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.server.BeComServer;

import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.ManagedEnvironment;

/**
 * A BeCom Card in a computer: its number, kept on the card, and the {@code becom} component. Data for it arrives as a
 * {@code becom_message} signal (from, app, data); an install it asked for ends with {@code becom_install} (number,
 * result).
 */
public final class BecomCard extends ManagedEnvironment {

    public static final String NUMBER = "becomNumber";
    private static final int TICKS_PER_SECOND = 20;

    private final EnvironmentHost host;
    private final boolean wired;
    private int number = -1;
    private int ticks;

    BecomCard(EnvironmentHost host, boolean wired) {
        this.host = host;
        this.wired = wired;
        setNode(
            Network.newNode(this, Visibility.Neighbors)
                .withComponent("becom")
                .create());
    }

    public boolean wired() {
        return wired;
    }

    public int number() {
        return number;
    }

    void setNumber(int number) {
        this.number = number;
    }

    public World world() {
        return host.world();
    }

    /** The block the computer stands in. */
    public long pos() {
        return BlockPos.pack(
            MathHelper.floor_double(host.xPosition()),
            MathHelper.floor_double(host.yPosition()),
            MathHelper.floor_double(host.zPosition()));
    }

    public double x() {
        return host.xPosition();
    }

    public double y() {
        return host.yPosition();
    }

    public double z() {
        return host.zPosition();
    }

    /** Hands the computer a signal. */
    void signal(String name, Object... args) {
        Node node = node();
        if (node == null) return;
        Object[] all = new Object[args.length + 1];
        all[0] = name;
        System.arraycopy(args, 0, all, 1, args.length);
        node.sendToReachable("computer.signal", all);
    }

    private static AppService apps() {
        BeComServer becom = BeComServer.get();
        return becom == null ? null : becom.apps;
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void update() {
        if (ticks++ % TICKS_PER_SECOND != 0) return;
        AppService apps = apps();
        if (apps != null) apps.computerHere(this);
    }

    @Override
    public void onDisconnect(Node node) {
        super.onDisconnect(node);
        if (node == node()) {
            AppService apps = apps();
            if (apps != null) apps.computerGone(this);
        }
    }

    @Override
    public void load(NBTTagCompound nbt) {
        super.load(nbt);
        if (nbt.hasKey(NUMBER)) number = nbt.getInteger(NUMBER);
    }

    @Override
    public void save(NBTTagCompound nbt) {
        super.save(nbt);
        if (number > 0) nbt.setInteger(NUMBER, number);
    }

    // region Callbacks

    @Callback(doc = "function():number -- This computer's number, or -1 until BeCom gives it one.")
    public Object[] number(Context context, Arguments args) {
        return new Object[] { number };
    }

    @Callback(doc = "function():string -- The signal: none, low, medium, high or max.")
    public Object[] signal(Context context, Arguments args) {
        AppService apps = apps();
        return new Object[] { apps == null || number < 0 ? "none" : apps.signal(number) };
    }

    @Callback(doc = "function():boolean -- Whether this is the wired card.")
    public Object[] isWired(Context context, Arguments args) {
        return new Object[] { wired };
    }

    @Callback(
        doc = "function(number:number, app:string, data:string):boolean[, string] -- Sends data to an app on "
            + "a phone, or to a computer, by number.")
    public Object[] send(Context context, Arguments args) {
        AppService apps = apps();
        if (apps == null || number < 0) return new Object[] { null, "no number yet" };
        String reason = apps.send(number, args.checkString(1), args.checkInteger(0), args.checkByteArray(2));
        return reason == null ? new Object[] { true } : new Object[] { null, reason };
    }

    @Callback(
        doc = "function(number:number, source:string):boolean[, string] -- Offers an app to a phone; its "
            + "holder accepts or declines, and the answer comes as becom_install.")
    public Object[] install(Context context, Arguments args) {
        AppService apps = apps();
        if (apps == null || number < 0) return new Object[] { null, "no number yet" };
        String reason = apps.offer(this, args.checkInteger(0), args.checkString(1));
        return reason == null ? new Object[] { true } : new Object[] { null, reason };
    }

    @Callback(
        doc = "function(source:string):boolean[, string] -- Asks the nearest player, on their phone, to publish an "
            + "app to the App Store under their name; the answer comes as becom_publish.")
    public Object[] publish(Context context, Arguments args) {
        AppService apps = apps();
        if (apps == null || number < 0) return new Object[] { null, "no number yet" };
        String reason = apps.publish(this, args.checkString(0));
        return reason == null ? new Object[] { true } : new Object[] { null, reason };
    }

    // endregion
}
