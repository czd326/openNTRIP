package org.adv25.ADVNTRIP.Tools;

import org.adv25.ADVNTRIP.Databases.Models.ReferenceStationModel;
import org.adv25.ADVNTRIP.Servers.ReferenceStation;
import org.adv25.ADVNTRIP.Spatial.Point_lla;
import org.adv25.ADVNTRIP.Tools.RTCM.MSG1005;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class AnalyzeTasks {
    final static private Logger logger = LogManager.getLogger(AnalyzeTasks.class.getName());
    ReferenceStation referenceStation;
    ReferenceStationModel model;
    MessagePool messagePool;


    public AnalyzeTasks(ReferenceStation referenceStation, MessagePool messagePool) {
        this.referenceStation = referenceStation;
        this.model = referenceStation.getModel();
        this.messagePool = messagePool;

    }

    public TimerTask rtcmVersion = new TimerTask() {
        @Override
        public void run() {
            int format30 = 0;
            int format31 = 0;
            int format32 = 0;
            int format33 = 0;

            for (Integer msg : messagePool.bytePool.keySet()) {

                if (1001 <= msg && msg <= 1013) {
                    format30++;
                } else if ((1001 <= msg && msg <= 1039) || (1057 <= msg && msg <= 1068) || (4001 <= msg && msg <= 4095)) {
                    format31++;
                } else if (1044 <= msg && msg <= 1045 || 1071 <= msg && msg <= 1230) {
                    format32++;
                } else if (1 <= msg && msg <= 100 || msg == 1042 || msg == 1046) {
                    format33++;
                }
            }

            if (format33 != 0) {
                model.setFormat("RTCM 3.3");
            } else if (format32 != 0) {
                model.setFormat("RTCM 3.2");
            } else if (format31 != 0) {
                model.setFormat("RTCM 3.1");
            } else if (format30 != 0) {
                model.setFormat("RTCM 3.0");
            }

            model.update();

            logger.debug(referenceStation.getName() + ": update RTCM Version");
        }
    };

    public TimerTask navSystems = new TimerTask() {
        @Override
        public void run() {
            String navSystems = "";
            int[] GPS = {1001, 1002, 1003, 1004, 1015, 1016, 1017, 1019, 1030, 1034, 1057, 1058, 1059, 1060, 1061, 1062, 1071, 1072, 1073,
                    1074, 1075, 1076, 1077};

            for (int i : GPS) {
                if (messagePool.msgNumberPack.contains(i)) {
                    navSystems += "+GPS";
                    break;
                }
            }

            int[] GLONASS = {1009, 1010, 1011, 1012, 1020, 1031, 1035, 1037, 1038, 1039, 1063, 1064, 1065, 1066, 1067, 1068, 1081, 1082,
                    1083, 1084, 1085, 1086, 1087, 1230};

            for (int i : GLONASS) {
                if (messagePool.msgNumberPack.contains(i)) {
                    navSystems += "+GLO";
                    break;
                }
            }

            int[] GAL = {1091, 1092, 1093, 1094, 1095, 1096, 1097};

            for (int i : GAL) {
                if (messagePool.msgNumberPack.contains(i)) {
                    navSystems += "+GAL";
                    break;
                }
            }

            if (navSystems.length() > 0) {
                model.setNav_system(navSystems.substring(1));
                model.update();
            }

            logger.debug(referenceStation.getName() + ": update Nav Systems");
        }
    };

    public TimerTask carrier = new TimerTask() {
        @Override
        public void run() {
            int carrier = 0;
            int[] L1 = {1001, 1002, 1009, 1010, 1071, 1081, 1091};
            for (int i : L1) {
                if (messagePool.msgNumberPack.contains(i)) {
                    carrier = 1;
                    break;
                }
            }

            int[] L2 = {1003, 1004, 1011, 1012, 1072, 1073, 1074, 1076, 1077, 1082, 1083, 1084, 1086, 1087,
                    1092, 1093, 1094, 1096, 1097, 1230};
            for (int i : L2) {
                if (messagePool.msgNumberPack.contains(i)) {
                    carrier = 2;
                    break;
                }
            }

            model.setCarrier(carrier);
            model.update();
            logger.debug(referenceStation.getName() + ": update Carrier");
        }
    };

    public TimerTask position = new TimerTask() {
        @Override
        public void run() {
            double[] lla;

            if (messagePool.bytePool.containsKey(1005)) {

                MSG1005 msg = new MSG1005(messagePool.bytePool.get(1005));
                lla = lla = ecef2lla(msg.getECEFX(), msg.getECEFY(), msg.getECEFZ());

            } else if (messagePool.bytePool.containsKey(1006)) {

                MSG1005 msg = new MSG1005(messagePool.bytePool.get(1006));
                lla = lla = ecef2lla(msg.getECEFX(), msg.getECEFY(), msg.getECEFZ());

            } else {
                return;
            }

            Point_lla position = new Point_lla(lla[0], lla[1]);

            model.setLla(position);
            model.update();
            logger.debug(referenceStation.getName() + ": update position " + position.toString());
        }
    };

    public TimerTask positionMetaInfo = new TimerTask() {
        @Override
        public void run() {
            String rawJson = osmApi(model.getLla());

            String[] identifier = {"suburb", "village", "city", "county", "state"};

            JSONParser parser = new JSONParser();

            try {
                JSONObject json = (JSONObject) parser.parse(rawJson);
                json = (JSONObject) json.get("address");

                for (String match : identifier) {
                    if (json.containsKey(match)) {
                        model.setIdentifier((String) json.get(match));
                        break;
                    }
                }

                if (json.containsKey("country_code")) {
                    String iso = iso2CountryCodeToIso3CountryCode((String) json.get("country_code"));
                    model.setCountry(iso);
                    model.update();
                }

            } catch (ParseException ex) {
                ex.printStackTrace();
            }
        }
    };

    public TimerTask FormatDetails = new TimerTask() {
        @Override
        public void run() {
            model.setFormat_details(messagePool.toString());
            model.update();
        }
    };

    //gist.github.com/1536056
    // WGS84 ellipsoid constants
    private final double a = 6378137; // radius
    private final double e = 8.1819190842622e-2;  // eccentricity
    private final double asq = Math.pow(a, 2);
    private final double esq = Math.pow(e, 2);

    private double[] ecef2lla(double x, double y, double z) {

        double b = Math.sqrt(asq * (1 - esq));
        double bsq = Math.pow(b, 2);
        double ep = Math.sqrt((asq - bsq) / bsq);
        double p = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
        double th = Math.atan2(a * z, b * p);

        double lon = Math.atan2(y, x);
        double lat = Math.atan2((z + Math.pow(ep, 2) * b * Math.pow(Math.sin(th), 3)), (p - esq * a * Math.pow(Math.cos(th), 3)));
        double N = a / (Math.sqrt(1 - esq * Math.pow(Math.sin(lat), 2)));
        double alt = p / Math.cos(lat) - N;

        lon = lon % (2 * Math.PI);

        double[] response = {lat * 180 / Math.PI, lon * 180 / Math.PI, alt};

        return response;
    }

    public String osmApi(Point_lla point) {
        double lat = point.getLat().doubleValue();
        double lon = point.getLon().doubleValue();

        try {
            String url = "https://nominatim.openstreetmap.org/reverse.php?lat=" + lat + "&lon=" + lon + "&format=json&accept-language=en&zoom=14";

            URL obj = new URL(url);

            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36");

            int responseCode = con.getResponseCode();

            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return response.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Country code
    private String iso2CountryCodeToIso3CountryCode(String iso2CountryCode) {
        Locale locale = new Locale("", iso2CountryCode);
        return locale.getISO3Country();
    }

}
