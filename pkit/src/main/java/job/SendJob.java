package job;

import gui.ctrl.SendView;
import gui.model.config.SendProperty;
import nif.SNIF;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.packet.Packet;
import util.PacketHandle;

import java.net.UnknownHostException;
import java.util.ArrayList;

public class SendJob implements Runnable {

    private final SNIF snif;
    private final SendProperty sendProperty;
    private SendView sendView;
    private String opt;
    Packet packet;
    ArrayList<Packet> packetArrayList;

    int retry;

    public SendJob(SendView sendView, String opt) {
        this.sendView = sendView;
        this.opt = opt;
        this.snif = sendView.getSnif();
        this.sendProperty = sendView.getSendProperty();
        try {
            this.snif.load();
            if (opt.equals("one"))
                this.packet = PacketHandle.Restore(sendView.getPacketProperty());
            else {
                packetArrayList = new ArrayList<>(sendView.packetPropertyArrayList.size());
                sendView.packetPropertyArrayList.forEach(pp -> {
                    try {
                        packetArrayList.add(PacketHandle.Restore(pp));
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (UnknownHostException | PcapNativeException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        retry = 0;
        if (opt.equals("one")) {
            SendOne(packet);
        } else SendMulti();

    }

    private void SendMulti() {
        packetArrayList.forEach(this::SendOne);
    }

    private void SendOne(Packet packet) {
        try {
            for (int i=0; i<sendProperty.getCount(); ++i) {
                Thread.sleep(sendProperty.getTimeout());
                snif.handle.sendPacket(packet);
            }
        } catch (NotOpenException | PcapNativeException | InterruptedException ignored) {
            while (retry<sendProperty.getRetry())
                SendOne(packet);
        }
        retry++;
    }
}
