package org.adv25.ADVNTRIP.Network;

import org.adv25.ADVNTRIP.Clients.Client;
import org.adv25.ADVNTRIP.Clients.Passwords.None;
import org.adv25.ADVNTRIP.Databases.DAO.ReferenceStationDAO;
import org.adv25.ADVNTRIP.Databases.Models.ReferenceStationModel;
import org.adv25.ADVNTRIP.Servers.ReferenceStation;
import org.adv25.ADVNTRIP.Servers.Caster;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Hashtable;

public class ConnectHandler extends Thread {
    final static private Logger logger = LogManager.getLogger(ConnectHandler.class.getName());

    private Caster caster;
    private SocketChannel clientChannel;
    private ByteBuffer bb = ByteBuffer.allocate(1024);

    public ConnectHandler(SocketChannel clientChannel, Caster caster) throws IOException {
        this.caster = caster;
        this.clientChannel = clientChannel;
        this.clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
    }

    private String requestLine;
    private Hashtable<String, String> requestHeaders = new Hashtable<>();
    private StringBuffer messageBody = new StringBuffer();

    @Override
    public void run() {
        try {
            logger.info("Caster " + caster.getPort() + " has new connection " + clientChannel.getRemoteAddress().toString());

            clientChannel.read(bb);
            bb.flip();
            System.out.println(new String(bb.array()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bb.array())));

            requestLine = reader.readLine();

            String line = reader.readLine();
            while (line.length() > 0) {
                appendHeaderParameter(line);
                line = reader.readLine();
            }

            String bodyLine = reader.readLine();
            while (bodyLine != null) {
                messageBody.append(bodyLine).append("\r\n");
                bodyLine = reader.readLine();
            }

            if (requestLine.matches("GET [\\S]+ HTTP[\\S]+")) {
                logger.info("Caster " + caster.getPort() + " | " + requestLine + " " + clientChannel.getRemoteAddress().toString());
                Client client = new Client(clientChannel, requestLine, requestHeaders, messageBody);
                caster.newClient(client);
            }

            if (requestLine.matches("SOURCE [\\S]+ [\\S]+")) {
                //parsing
                String acc = requestLine.split(" ")[2];
                String pass = requestLine.split(" ")[1];

                //compare
                ReferenceStationDAO dao = new ReferenceStationDAO();
                ReferenceStationModel model = dao.read(acc);
                None compare = new None();

                if (!compare.Compare(model.getPassword(), pass)) {
                    logger.warn(acc + " bad password.");
                    throw new SecurityException();
                }

                ReferenceStation bs = ReferenceStation.getBase(model.getId());
                if (bs != null) {
                    bs.setNewSocket(clientChannel);
                }
                bb.clear();
                bb.put(Client.OK_MESSAGE);
                bb.flip();
                clientChannel.write(bb);
            }

        } catch (SecurityException ex) {
            try {
                clientChannel.write(ByteBuffer.wrap(Client.BAD_MESSAGE));
                clientChannel.close();
            } catch (IOException e) {
                logger.warn("Can't send bad password message.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                clientChannel.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void appendHeaderParameter(String header) {
        int idx = header.indexOf(":");
        if (idx == -1) {
            return;
        }
        requestHeaders.put(header.substring(0, idx), header.substring(idx + 1));
    }


}
