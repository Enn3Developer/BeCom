package com.enn3developer.becom.apps;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.enn3developer.becom.BeCom;

import li.cil.oc.api.Driver;
import li.cil.oc.api.FileSystem;
import li.cil.oc.api.Items;
import li.cil.oc.api.Machine;
import li.cil.oc.api.Network;
import li.cil.oc.api.detail.ItemInfo;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;

/**
 * The OpenComputers machine inside a carried phone (how phone apps work), like OC's Tablet: a tier 1 CPU, memory, an
 * EEPROM that boots BeCom OS from a read-only system disk, the phone's own storage, and the {@code becom} component.
 * It stands where its holder stands. Its power comes from the phone's battery, which BeCom draws as "Apps"; the
 * machine's own buffer is kept full.
 */
final class PhoneMachine implements MachineHost {

    private static final double BUFFER = 1000;

    final int number;
    private EntityPlayerMP holder;
    private final li.cil.oc.api.machine.Machine machine;
    private final List<ItemStack> parts = new ArrayList<>();
    private final List<ManagedEnvironment> environments = new ArrayList<>();
    private final PhoneComponent becom;
    private final PowerSource power;
    private boolean ready;

    PhoneMachine(int number, EntityPlayerMP holder, AppService apps, long storageBytes) {
        this.number = number;
        this.holder = holder;
        for (String name : new String[] { "cpu1", "ram2" }) {
            ItemInfo info = Items.get(name);
            if (info != null) parts.add(info.createItemStack(1));
        }
        ItemStack eeprom = PhoneOs.eeprom();
        if (eeprom != null) parts.add(eeprom);
        machine = Machine.create(this);
        // Reads the parts: the CPU's architecture and call budget, the component limit. Without it every direct call
        // is over budget and the kernel never gets to the BIOS.
        machine.onHostChanged();
        Network.joinNewNetwork(machine.node());
        for (ItemStack part : parts) {
            li.cil.oc.api.driver.Item driver = Driver.driverFor(part, getClass());
            if (driver == null) continue;
            ManagedEnvironment environment = driver.createEnvironment(part, this);
            if (environment == null) continue;
            environment.load(driver.dataTag(part));
            attach(environment);
        }
        attach(
            FileSystem.asManagedEnvironment(
                FileSystem.fromClass(BeCom.class, BeCom.MODID, "lua/os"),
                "BeCom OS",
                this,
                null));
        attach(
            FileSystem.asManagedEnvironment(
                FileSystem.fromSaveDirectory(storageName(number), storageBytes, false),
                "Phone storage",
                this,
                null));
        becom = new PhoneComponent(this, apps);
        attach(becom);
        power = new PowerSource();
        attach(power);
    }

    private void attach(ManagedEnvironment environment) {
        if (environment == null || environment.node() == null) return;
        environments.add(environment);
        machine.node()
            .connect(environment.node());
    }

    /** What it's built from, for the log when it won't start. */
    String describe() {
        StringBuilder out = new StringBuilder();
        for (ItemStack part : parts) out.append(part.getDisplayName())
            .append(' ');
        out.append("components ")
            .append(machine.componentCount());
        return out.toString();
    }

    /**
     * Deletes an uninstalled app's files from a phone's storage, so installing it again starts clean. OC keeps the
     * storage in the world's save, under the name the machine gives it.
     */
    static void forgetFiles(int number, String id) {
        if (!id.matches("[a-z0-9_]+")) return;
        java.io.File root = net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory();
        if (root == null) return;
        java.io.File dir = new java.io.File(root, "opencomputers/" + storageName(number) + "/data/" + id);
        try {
            if (dir.isDirectory()) org.apache.commons.io.FileUtils.deleteDirectory(dir);
        } catch (java.io.IOException e) {
            BeCom.LOG.warn("Couldn't delete the files of phone {}'s app {}", number, id, e);
        }
    }

    /** The bytes phone {@code number}'s app {@code id} keeps in its own folder. */
    static long filesSize(int number, String id) {
        if (!id.matches("[a-z0-9_]+")) return 0;
        java.io.File root = net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory();
        if (root == null) return 0;
        java.io.File dir = new java.io.File(root, "opencomputers/" + storageName(number) + "/data/" + id);
        try {
            return dir.isDirectory() ? org.apache.commons.io.FileUtils.sizeOfDirectory(dir) : 0;
        } catch (IllegalArgumentException e) {
            // It went while being read.
            return 0;
        }
    }

    /** Deletes all of a phone's app files: its number is gone. */
    static void forgetStorage(int number) {
        java.io.File root = net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory();
        if (root == null) return;
        java.io.File dir = new java.io.File(root, "opencomputers/" + storageName(number));
        try {
            if (dir.isDirectory()) org.apache.commons.io.FileUtils.deleteDirectory(dir);
        } catch (java.io.IOException e) {
            BeCom.LOG.warn("Couldn't delete phone {}'s app files", number, e);
        }
    }

    private static String storageName(int number) {
        return "becom-phone-" + number;
    }

    boolean start() {
        power.refill();
        return machine.start();
    }

    /** One tick: the machine runs, and anything that ticks with it. */
    void update(EntityPlayerMP holder) {
        this.holder = holder;
        power.refill();
        machine.update();
        for (ManagedEnvironment environment : environments) {
            if (environment.canUpdate()) environment.update();
        }
    }

    boolean running() {
        return machine.isRunning();
    }

    String lastError() {
        return machine.lastError();
    }

    /** Sends the OS a signal; false while the machine can't take one. */
    boolean signal(String name, Object... args) {
        return machine.signal(name, args);
    }

    boolean ready() {
        return ready;
    }

    void setReady() {
        ready = true;
    }

    EntityPlayerMP holder() {
        return holder;
    }

    void stop() {
        machine.stop();
        // Closing its network lets OC free the machine's state and its disks' handles.
        for (ManagedEnvironment environment : environments) {
            Node node = environment.node();
            if (node != null) node.remove();
        }
        machine.node()
            .remove();
    }

    // region MachineHost

    @Override
    public li.cil.oc.api.machine.Machine machine() {
        return machine;
    }

    @Override
    public Iterable<ItemStack> internalComponents() {
        return parts;
    }

    @Override
    public int componentSlot(String address) {
        return -1;
    }

    @Override
    public void onMachineConnect(Node node) {}

    @Override
    public void onMachineDisconnect(Node node) {}

    @Override
    public World world() {
        return holder.worldObj;
    }

    @Override
    public double xPosition() {
        return holder.posX;
    }

    @Override
    public double yPosition() {
        return holder.posY + holder.getEyeHeight();
    }

    @Override
    public double zPosition() {
        return holder.posZ;
    }

    @Override
    public void markChanged() {}

    // endregion

    /** Keeps the machine's energy buffer full; BeCom bills the phone's battery for apps itself. */
    private static final class PowerSource extends li.cil.oc.api.prefab.ManagedEnvironment {

        PowerSource() {
            setNode(
                Network.newNode(this, Visibility.None)
                    .withConnector(BUFFER)
                    .create());
        }

        void refill() {
            if (node() instanceof Connector connector) {
                connector.changeBuffer(connector.localBufferSize() - connector.localBuffer());
            }
        }
    }
}
