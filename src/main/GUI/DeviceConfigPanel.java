package main.GUI;

import com.fazecast.jSerialComm.*;

import main.GUI.controller.ComponentConfig;
import main.GlobalProperties;
import main.udpServer.udp2SSCWorker;
import main.udpServer.udpServer4SSC;
import main.usb2ppm.ServoParameter;
import main.usb2ppm.Usb2PPMWorker;
import main.usb2ppm.event.DataSentEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *     This file is part of joystick-to-ppm, a port of Flytron's Compufly
 *     to Java for cross platform use.
 *
 *     The source was obtained from code.google.com/p/joystick-to-ppm
 *     Copyright (C) 2011  Alexandr Vorobiev
 *
 *     Implemented new interface jserialComm
 *     Added wLAN connect
 *     Copyright (C) 2019  Gregor Schlechtriem
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public class DeviceConfigPanel extends JPanel implements ActionListener, DataSentEvent, AdjustmentListener{
    public final static int SERVO_COUNT = 8;
    private static final String CONNECT = "Connect";
    private static final String DISCONNECT = "Disconnect";
    private final JComboBox comBox;
    // private final JPanel deviceConfigPanel = new JPanel(new GridBagLayout());
    private final JPanel portSettingPanel = new JPanel(new GridBagLayout());
    private final JPanel wLANconnectingPanel = new JPanel(new GridBagLayout());
    private final JPanel servoOutputPanel = new JPanel(new GridBagLayout());
    private final JComboBox channelsBox = new JComboBox(new Integer[]{1,2,3,4,5,6,7,8});
    private final JProgressBar[] servoBars = new JProgressBar[SERVO_COUNT];
    private final JRadioButton[] servoInv = new JRadioButton[SERVO_COUNT];
    private final JScrollBar[] servoTrim = new JScrollBar[SERVO_COUNT];
    private final JScrollBar[] servoEPA = new JScrollBar[SERVO_COUNT];
    private final JRadioButton v2 = new JRadioButton("HP PiKoder");
    private final JComboBox modeBox = new JComboBox(new String[]{"Negative PPM (Futaba,Hitec,Esky,JR)","Positive PPM"});
    private final JButton connect = new JButton(CONNECT);
    private final JButton wLANconnect = new JButton(CONNECT);
    //private Map<Integer, net.java.games.input.Component> =


    private final JButton setModeBtn = new JButton("Set Chanel and Mode");

    private Thread workingThread;
    private Usb2PPMWorker worker;
    private udp2SSCWorker udpWorker;
    private static HashMap<ComponentConfig, Integer> assignMap = new HashMap<ComponentConfig, Integer>();
    private static DeviceConfigPanel INSTANCE  = new DeviceConfigPanel();

    protected DeviceConfigPanel() {
        final Pattern lastIntPattern;
        lastIntPattern = Pattern.compile("[^0-9]+([0-9]+)$");
        int ComRefTab[] = new int[256];
        for (int i = 0; i < ComRefTab.length; i++)
            ComRefTab[i] = 256;
        setModeBtn.addActionListener(this);
        v2.setSelected(Boolean.parseBoolean(GlobalProperties.getProperties().getProperty("V2",Boolean.toString(false))));
        //v2.setVisible(false);
        v2.addActionListener(this);
        comBox = new JComboBox();
        SerialPort[] comPort = SerialPort.getCommPorts();
        for (int i = 0; i < comPort.length; i++) {
            Matcher matcher = lastIntPattern.matcher(comPort[i].getSystemPortName());
            if (matcher.find()) {
                String someNumberStr = matcher.group(1);
                ComRefTab[Integer.parseInt(someNumberStr)] = i;
            }
        }
        for (int i = 0; i < ComRefTab.length; i++) {
            if (ComRefTab[i] != 256) {
                comBox.addItem(comPort[ComRefTab[i]].getSystemPortName());
            }
        }
        if (comBox.getItemCount() > 0) {
            String selCom = (String)comBox.getSelectedItem();
            selCom = GlobalProperties.getProperties().getProperty("COM",selCom);
            if (!selCom.equals(comBox.getSelectedItem()))
                for (int i = 0; i < comBox.getItemCount(); i++ ) {
                    if (comBox.getItemAt(i).equals(selCom)) {
                        comBox.setSelectedIndex(i);
                        break;
                    }
                }
            GlobalProperties.getProperties().setProperty("COM",(String)comBox.getSelectedItem());
        }
        Integer value = Integer.valueOf(GlobalProperties.getProperties().getProperty("CHANNELS", "1"));
        channelsBox.setSelectedItem(value);
        GlobalProperties.getProperties().setProperty("CHANNELS", value.toString());

        value = Integer.valueOf(GlobalProperties.getProperties().getProperty("PPMTYPE", "0"));
        modeBox.setSelectedIndex(value);
        GlobalProperties.getProperties().setProperty("PPMTYPE", value.toString());


        createLayout();
    }

    public static DeviceConfigPanel getINSTANCE() {
        return INSTANCE;
    }
    public static Map<ComponentConfig, Integer> getAssignMap() {
        return Collections.unmodifiableMap(assignMap);
    }
    public static void addMapping(ComponentConfig component, int channel, boolean updateParams) {
        assignMap.put(component, channel);
        if (updateParams) {
            updateParams();
        }
        INSTANCE.updateMapping();
    }

    private static void updateParams() {
        GlobalProperties.getProperties().setProperty("DC", Integer.toString(assignMap.size()));
        int i = 1;
        for (Map.Entry<ComponentConfig, Integer> entry: assignMap.entrySet()) {
            GlobalProperties.getProperties().setProperty("DEVICE" + i, entry.getKey().toString());
            GlobalProperties.getProperties().setProperty("DCHANNEL" + i, entry.getValue().toString());
            GlobalProperties.getProperties().setProperty("CHANNEL_SV" + i, entry.getKey().getSentValue() + "");
            GlobalProperties.getProperties().setProperty("CHARACTERISTIC" + i, entry.getKey().getCharacteristics());
            i++;
        }
    }

    public static void addMapping(ComponentConfig component, int channel){
        addMapping(component,channel, true);
    }
    public static void removeMapping(ComponentConfig component) {
        if (assignMap.containsKey(component))
            assignMap.remove(component);
        updateParams();
        INSTANCE.updateMapping();
    }

    public void updateMapping() {
        if (worker!= null)
            worker.setMapping(getAssignMap());
    }
    public void updateParametersMap() {
        if (worker!= null)
            worker.setParameterMap(servoParameterMap());
    }

    public void wLANupdateMapping() {
        if (udpWorker!= null)
            udpWorker.setMapping(getAssignMap());
    }

    public void wLANupdateParametersMap() {
        if (udpWorker!= null)
            udpWorker.setParameterMap(servoParameterMap());
    }

    private void createLayout() {
    	portSettingPanel.setBorder(new TitledBorder("USB | Bluetooth"));
        GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets.set(10, 10, 10, 10);
        gbc.gridwidth = 2;
        portSettingPanel.add(comBox, gbc);
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        portSettingPanel.add(v2, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        portSettingPanel.add(connect, gbc);
        connect.addActionListener(this);

        wLANconnectingPanel.setBorder(new TitledBorder("WLAN"));
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets.set(10, 10, 10, 10);
        gbc.gridwidth = 2;
        wLANconnectingPanel.add(wLANconnect, gbc);
        wLANconnect.addActionListener(this);

        servoOutputPanel.setBorder(new TitledBorder("Servo Outputs"));
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        servoOutputPanel.add(new JPanel(), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.insets.set(10, 10, 10, 10);
        servoOutputPanel.add(new JLabel("Trim"), gbc);
        gbc.gridx = 4;
        servoOutputPanel.add(new JLabel("EPA"), gbc);

        for (int j = 0; j < SERVO_COUNT; j++) {
            servoBars[j] = new JProgressBar(0,1000);
            servoInv[j] = new JRadioButton("inv");
            servoInv[j].addActionListener(this);
            servoInv[j].setSelected(false);
            boolean value = Boolean.parseBoolean(GlobalProperties.getProperties().getProperty("INV" + j, Boolean.toString(servoInv[j].isSelected())));
            servoInv[j].setSelected(value);
            GlobalProperties.getProperties().setProperty("INV"+j, Boolean.toString(servoInv[j].isSelected()));
            servoTrim[j] = new JScrollBar(JScrollBar.HORIZONTAL, 0, 0, -100, 100);
            servoTrim[j].addAdjustmentListener(this);
            int ivalue = Integer.parseInt(GlobalProperties.getProperties().getProperty("TRIM" + j, Integer.toString(servoTrim[j].getValue())));
            servoTrim[j].setValue(ivalue);
            GlobalProperties.getProperties().setProperty("TRIM"+j, Integer.toString(servoTrim[j].getValue()) );
            servoEPA[j] = new JScrollBar(JScrollBar.HORIZONTAL, 100, 0, 50, 150);
            servoEPA[j].addAdjustmentListener(this);
            ivalue = Integer.parseInt(GlobalProperties.getProperties().getProperty("EPA" + j, Integer.toString(servoEPA[j].getValue())));
            servoEPA[j].setValue(ivalue);
            GlobalProperties.getProperties().setProperty("EPA"+j, Integer.toString(servoEPA[j].getValue()) );
            gbc.gridx = 0;
            servoOutputPanel.add(new JLabel(""+(j+1)),gbc);
            gbc.gridx = 1;
            servoOutputPanel.add(servoBars[j], gbc);
            gbc.gridx = 2;
            servoOutputPanel.add(servoInv[j], gbc);
            gbc.gridx = 3;
            servoOutputPanel.add(servoTrim[j], gbc);
            gbc.gridx = 4;
            servoOutputPanel.add(servoEPA[j], gbc);
        }
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridheight = GridBagConstraints.REMAINDER;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weighty = 100;
        servoOutputPanel.add(new JPanel(), gbc);
        setLayout(new BorderLayout());
        JPanel leftPanel =  new JPanel();
        leftPanel.setLayout(new GridBagLayout());
        add(leftPanel, BorderLayout.WEST);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(0,0,0,0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx=1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        //gbc.gridheight = GridBagConstraints.RELATIVE;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.weightx = 1;

        // leftPanel.add(deviceConfigPanel,gbc);
        leftPanel.add(portSettingPanel,gbc);
        leftPanel.add(wLANconnectingPanel,gbc);
        gbc.weighty = 100;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridheight = GridBagConstraints.REMAINDER;
        leftPanel.add(new JPanel(),gbc);
        add(servoOutputPanel, BorderLayout.CENTER);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == connect) {
            if (connect.getText().equals(CONNECT)) {
                //connect
                try {
                    connect(comBox.getSelectedItem().toString());
                } catch (Exception e1) {
                    JOptionPane.showMessageDialog(this,
                    e1.getMessage(),
                    "Error!",
                    JOptionPane.WARNING_MESSAGE);
                    return;
                }
                connect.setText(DISCONNECT);
            } else {
                workingThread.interrupt();
                try {
                    workingThread.join();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                workingThread = null;
                worker =null;
                //disconnect
                connect.setText(CONNECT);
            }
        } else if (e.getSource() == wLANconnect) {
            if (wLANconnect.getText().equals(CONNECT)) {
                udpServer4SSC myUdpServer = new udpServer4SSC();
                //connect
                try {
                    myUdpServer.connect2wLAN();
                } catch (Exception e1) {
                    JOptionPane.showMessageDialog(this,
                    e1.getMessage(), "Error!", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                wLANconnect.setText(DISCONNECT);
                for (JProgressBar progressBar: servoBars)
                    progressBar.setValue(0);
                udpWorker = new udp2SSCWorker();
                udpWorker.addListener(this);
                wLANupdateMapping();
                wLANupdateParametersMap();
                workingThread = new Thread(udpWorker);
                workingThread.start();
            } else {
                workingThread.interrupt();
                try {
                    workingThread.join();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                workingThread = null;
                udpWorker = null;
                //disconnect
                wLANconnect.setText(CONNECT);
            }
        } else if (e.getSource() == setModeBtn) {
            if (connect.getText().equals(CONNECT)) {
                JOptionPane.showMessageDialog(this,
                        "You must open serial port first.",
                        "Warning!",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                worker.sendNewParameterAndTerminate(channelsBox.getSelectedIndex() + 1, modeBox.getSelectedIndex());
                try {
                    workingThread.join();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                worker = null;
                workingThread = null;
                connect.setText(CONNECT);
                JOptionPane.showMessageDialog(this,
                        "PPM Stream Type Changed. \n" +
                                "Please restart your transmitter from Power On/Off\n" +
                                "After that you can connect do transmitter again.",
                        "Warning", JOptionPane.WARNING_MESSAGE);
            }
        } else if (e.getSource() == v2) {
             GlobalProperties.getProperties().setProperty("V2", Boolean.toString(v2.isSelected()));
             if (worker != null)
                 worker.setV2(v2.isSelected());
         } else {
             adjustmentValueChanged();
         }
    }

    private void connect ( String portName ) throws Exception
    {
        SerialPort serialPort = SerialPort.getCommPort(portName);
        if ( serialPort.isOpen() )
            throw new Exception("Port is currently in use");
        else {
            for (JProgressBar progressBar: servoBars)
                progressBar.setValue(0);
            serialPort.openPort(2000);
            if ( serialPort.isOpen()) {
                serialPort.setComPortParameters(9600, 8, 1, 0);
                worker = new Usb2PPMWorker(serialPort, serialPort.getOutputStream(), v2.isSelected());
                worker.addListener(this);
                updateMapping();
                updateParametersMap();
                workingThread = new Thread(worker);
                workingThread.start();
            } else {
                throw new Exception("Cannot open port " + portName + "!");
            }
        }
    }

    private Map<Integer, ServoParameter> servoParameterMap() {
        Map<Integer, ServoParameter> result = new TreeMap<Integer, ServoParameter>();
        for (int i = 0; i < SERVO_COUNT; i++) {
            ServoParameter parameter = new ServoParameter(servoInv[i].isSelected(), servoTrim[i].getValue(), servoEPA[i].getValue());
            result.put(i+1, parameter);
        }
        return result;
    }

    private void updateProperties() {
        for (int i = 0; i < SERVO_COUNT; i++) {
            if (servoInv[i] == null || servoTrim[i] == null || servoEPA[i] == null)
                break;
            GlobalProperties.getProperties().setProperty("INV"+i, Boolean.toString(servoInv[i].isSelected()));
            GlobalProperties.getProperties().setProperty("TRIM"+i, Integer.toString(servoTrim[i].getValue()));
            GlobalProperties.getProperties().setProperty("EPA"+i, Integer.toString(servoEPA[i].getValue()));
        }
    }

    public void dataSent(final int channel, final int data) {
        if (channel <= SERVO_COUNT) {
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    servoBars[channel - 1].setValue(data);
                }
            });

        }
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {
        if (worker != null) {
            updateParametersMap();
        } else if (udpWorker != null) {
            wLANupdateParametersMap();
        }
        updateProperties();
    }

    public void adjustmentValueChanged() {
        if (worker != null) {
            updateParametersMap();
        } else if (udpWorker != null) {
            wLANupdateParametersMap();
        }
        updateProperties();
    }
}
