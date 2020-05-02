package util.job;

import com.fasterxml.jackson.databind.json.JsonMapper;
import gui.model.SettingProperty;
import gui.model.analysis.IOLineProperty;
import gui.model.analysis.Ipv4StatBarProperty;
import gui.model.analysis.Ipv6StatBarProperty;
import gui.model.analysis.ProtocolPieProperty;
import gui.model.browser.PacketInfoProperty;
import gui.model.browser.PacketProperty;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapPacket;
import util.PacketHandle;
import util.nif.CNIF;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class AnalysisJob implements Runnable {

    CNIF cnif;

    long time = 0;
    long second = 0;
    int pn = 0;
    int bn = 0;
    int dataLength = 0;
    IOLineProperty ioLineProperty;

    ProtocolPieProperty protocolPieProperty = new ProtocolPieProperty();
    Ipv4StatBarProperty ipv4StatBarProperty = new Ipv4StatBarProperty();
    Ipv6StatBarProperty ipv6StatBarProperty = new Ipv6StatBarProperty();


    public AnalysisJob (String pcapFile) {
        cnif = new CNIF(pcapFile);
        System.out.println(pcapFile);
    }

    private void save() {
        JsonMapper mapper = new JsonMapper();
        try {
            mapper.writeValue(new File(SettingProperty.ioLineChartJson), ioLineProperty);
            mapper.writeValue(new File(SettingProperty.protocolPieChartJson), protocolPieProperty);
            mapper.writeValue(new File(SettingProperty.ipv4StatBarChartJson), ipv4StatBarProperty);
            mapper.writeValue(new File(SettingProperty.ipv6StatBarChartJson), ipv6StatBarProperty);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        int num=0;
        while (true) {
            try {
                PcapPacket packet = cnif.handle.getNextPacketEx();

                PacketInfoProperty packetInfoProperty = PacketHandle.InfoPipeline(packet);
                packetInfoProperty.setNo(num + 1);
                PacketProperty packetProperty = new PacketProperty();
                packetProperty.setInfo(packetInfoProperty);

                num++;
                // analysis
                // 1. io
                if (time == 0) {
                    time = Date.from(packet.getTimestamp()).getTime();
                    ioLineProperty = new IOLineProperty(time);
                }
                if (time > 0 && Date.from(packet.getTimestamp()).getTime() - time > 1000) {
                    second = (Date.from(packet.getTimestamp()).getTime() - time) / 1000;
                    dataLength = ((int) (dataLength + second)) + 1;
                    ioLineProperty.setDataLength(dataLength);
                    time = packet.getTimestamp().toEpochMilli();
                    ioLineProperty.getData().get(0).add(pn);
                    ioLineProperty.getData().get(1).add(bn);
                    pn = 0;
                    bn = 0;
                    for (int i = 0; i < second; i++) {
                        ioLineProperty.getData().get(0).add(0);
                        ioLineProperty.getData().get(1).add(0);
                    }
                }
                pn++;
                bn = bn + packet.getRawData().length;

                // 2. pie
                if (!protocolPieProperty.getData().containsKey(packetInfoProperty.getProtocol()))
                    protocolPieProperty.getData().put(packetInfoProperty.getProtocol(), (double) 1);
                else {
                    double n = protocolPieProperty.getData().get(packetInfoProperty.getProtocol());
                    protocolPieProperty.getData().put(packetInfoProperty.getProtocol(), n + 1);
                }

                //3. bar
                if (packetInfoProperty.getSrc().contains(".")) {
                    String ip = packetInfoProperty.getSrc().split(":")[0];
                    String port = packetInfoProperty.getSrc().split(":")[1];
                    if (!ipv4StatBarProperty.getData().containsKey(ip))
                        ipv4StatBarProperty.getData().put(ip, ((double) packet.getOriginalLength()) / 1024);
                    else {
                        double n = ipv4StatBarProperty.getData().get(ip);
                        ipv4StatBarProperty.getData().put(ip, n + ((double) packet.getOriginalLength()) / 1024);
                    }
                } else {
                    if (!ipv6StatBarProperty.getData().containsKey(packetInfoProperty.getSrc()))
                        ipv6StatBarProperty.getData().put(packetInfoProperty.getSrc(), ((double) packet.getOriginalLength()) / 1024);
                    else {
                        double n = ipv6StatBarProperty.getData().get(packetInfoProperty.getSrc());
                        ipv6StatBarProperty.getData().put(packetInfoProperty.getSrc(), n + ((double) packet.getOriginalLength()) / 1024);
                    }
                }
            } catch (EOFException e) {
                ioLineProperty.getData().get(0).add(dataLength, pn);
                ioLineProperty.getData().get(1).add(dataLength, bn);
                dataLength++;

                int finalNum = num;
                protocolPieProperty.getData().keySet().forEach(key -> {
                    double n = protocolPieProperty.getData().get(key);
                    protocolPieProperty.getData().put(key, n / finalNum);
                });

                ipv4StatBarProperty.setData(SortByValueDescending(ipv4StatBarProperty.getData()));
                ipv6StatBarProperty.setData(SortByValueDescending(ipv6StatBarProperty.getData()));

                this.save();
                break;
            } catch (NotOpenException | TimeoutException | PcapNativeException ignored) {
            }

        }
    }

    //降序排序
    public static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> SortByValueDescending(Map<K, V> map)
    {
        List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
        list.sort(new Comparator<Map.Entry<K, V>>() {
            @Override
            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                int compare = (o1.getValue()).compareTo(o2.getValue());
                return -compare;
            }
        });

        LinkedHashMap<K, V> result = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
