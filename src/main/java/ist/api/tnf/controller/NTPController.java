package ist.api.tnf.controller;

//import org.apache.commons.net.ntp.*;
//import org.json.simple.JSONArray;
//import org.json.simple.parser.JSONParser;
//import org.json.simple.parser.ParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.shaded.net.minidev.json.JSONArray;
import org.apache.hadoop.shaded.org.apache.commons.net.ntp.*;
import org.apache.tomcat.util.json.JSONParser;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class NTPController {
    final NTPUDPClient client = new NTPUDPClient();
    private static final NumberFormat numberFormat = new java.text.DecimalFormat("0.00");
    
    private static List<String> ntpServers = new ArrayList<String>();
    
    
//    @RequestMapping()
//    Object check() {
//    	if(null == ntpServers || ntpServers.size() < 1) {
//        	try {     
//            	JSONParser parser = new JSONParser();
//            	JSONArray jsonArray = (JSONArray) parser.parse(new FileReader("config/ntpServer.json"));
//            	for(int i=0; i< jsonArray.size(); i++)
//            		ntpServers.add(jsonArray.get(0).toString());
//            } catch (FileNotFoundException e) {
//            	return e.getMessage();
//            } catch (IOException e) {
//				// TODO Auto-generated catch block
//            	return e.getMessage();
//			} catch (ParseException e) {
//				// TODO Auto-generated catch block
//            	return e.getMessage();
//			}
//    	}
//    	
//    	
//    	
//    	int serverIndex = 0;
//    	
//    	Object ntpResponse = null;
//    	
//    	while(null == ntpResponse && serverIndex < ntpServers.size()) {
//        	String ntpServer = ntpServers.get(serverIndex++);
//        	System.out.println(ntpServer);
//    		ntpResponse =  sendNtpTime(ntpServer);
//    	}
//		return ntpResponse;
//    }
//    
    @RequestMapping("/api/ntp")
	Object get() throws IOException, ParseException {
    	//get time server once
        if (ntpServers == null || ntpServers.isEmpty()) {
            try {
                // Using Jackson for JSON parsing
                ObjectMapper objectMapper = new ObjectMapper();
                File resource = new ClassPathResource("config/ntpServer.json").getFile();

                // Read the list of NTP servers from JSON file
                List<String> servers = objectMapper.readValue(resource, List.class);
                ntpServers.addAll(servers);

                return "NTP servers loaded successfully.";

            } catch (IOException e) {
                return "Error loading NTP config: " + e.getMessage();
            }
        }
    	
    	
    	int serverIndex = 0;
    	
    	Object ntpResponse = null;
    	
    	while(null == ntpResponse && serverIndex < ntpServers.size()) {
        	String ntpServer = ntpServers.get(serverIndex++);
        	System.out.println(ntpServer);
    		ntpResponse =  sendNtpTime(ntpServer);
    	}
		return ntpResponse;
	}
    private Object sendNtpTime(String ntpServer) {
        // We want to timeout if a response takes longer than 10 seconds
        client.setDefaultTimeout(10000);
        try {
            client.open();
            System.out.println();
            try {
                final InetAddress hostAddr = InetAddress.getByName(ntpServer);
                //System.out.println("> " + hostAddr.getHostName() + "/" + hostAddr.getHostAddress());
                final TimeInfo info = client.getTime(hostAddr);
                return processResponse(info);
            } catch (final IOException ioe) {
                ioe.printStackTrace();
            }
        } catch (final SocketException e) {
            e.printStackTrace();
        }

        client.close();
		return null;
	}
	public static TimeInfo processResponse(final TimeInfo info)
    {
        final NtpV3Packet message = info.getMessage();
        final int stratum = message.getStratum();
        final String refType;
        if (stratum <= 0) {
            refType = "(Unspecified or Unavailable)";
        } else if (stratum == 1) {
            refType = "(Primary Reference; e.g., GPS)"; // GPS, radio clock, etc.
        } else {
            refType = "(Secondary Reference; e.g. via NTP or SNTP)";
        }
        // stratum should be 0..15...
        System.out.println(" Stratum: " + stratum + " " + refType);
        final int version = message.getVersion();
        final int li = message.getLeapIndicator();
        System.out.println(" leap=" + li + ", version="
                + version + ", precision=" + message.getPrecision());

        System.out.println(" mode: " + message.getModeName() + " (" + message.getMode() + ")");
        final int poll = message.getPoll();
        // poll value typically btwn MINPOLL (4) and MAXPOLL (14)
        System.out.println(" poll: " + (poll <= 0 ? 1 : (int) Math.pow(2, poll))
                + " seconds" + " (2 ** " + poll + ")");
        final double disp = message.getRootDispersionInMillisDouble();
        System.out.println(" rootdelay=" + numberFormat.format(message.getRootDelayInMillisDouble())
                + ", rootdispersion(ms): " + numberFormat.format(disp));

        final int refId = message.getReferenceId();
        String refAddr = NtpUtils.getHostAddress(refId);
        String refName = null;
        if (refId != 0) {
            if (refAddr.equals("127.127.1.0")) {
                refName = "LOCAL"; // This is the ref address for the Local Clock
            } else if (stratum >= 2) {
                // If reference id has 127.127 prefix then it uses its own reference clock
                // defined in the form 127.127.clock-type.unit-num (e.g. 127.127.8.0 mode 5
                // for GENERIC DCF77 AM; see refclock.htm from the NTP software distribution.
                if (!refAddr.startsWith("127.127")) {
                    try {
                        final InetAddress addr = InetAddress.getByName(refAddr);
                        final String name = addr.getHostName();
                        if (name != null && !name.equals(refAddr)) {
                            refName = name;
                        }
                    } catch (final UnknownHostException e) {
                        // some stratum-2 servers sync to ref clock device but fudge stratum level higher... (e.g. 2)
                        // ref not valid host maybe it's a reference clock name?
                        // otherwise just show the ref IP address.
                        refName = NtpUtils.getReferenceClock(message);
                    }
                }
            } else if (version >= 3 && (stratum == 0 || stratum == 1)) {
                refName = NtpUtils.getReferenceClock(message);
                // refname usually have at least 3 characters (e.g. GPS, WWV, LCL, etc.)
            }
            // otherwise give up on naming the beast...
        }
        if (refName != null && refName.length() > 1) {
            refAddr += " (" + refName + ")";
        }
        System.out.println(" Reference Identifier:\t" + refAddr);

        final TimeStamp refNtpTime = message.getReferenceTimeStamp();
        System.out.println(" Reference Timestamp:\t" + refNtpTime + "  " + refNtpTime.toDateString());

        // Originate Time is time request sent by client (t1)
        final TimeStamp origNtpTime = message.getOriginateTimeStamp();
        System.out.println(" Originate Timestamp:\t" + origNtpTime + "  " + origNtpTime.toDateString());

        final long destTimeMillis = info.getReturnTime();
        // Receive Time is time request received by server (t2)
        final TimeStamp rcvNtpTime = message.getReceiveTimeStamp();
        System.out.println(" Receive Timestamp:\t" + rcvNtpTime + "  " + rcvNtpTime.toDateString());

        // Transmit time is time reply sent by server (t3)
        final TimeStamp xmitNtpTime = message.getTransmitTimeStamp();
        System.out.println(" Transmit Timestamp:\t" + xmitNtpTime + "  " + xmitNtpTime.toDateString());

        // Destination time is time reply received by client (t4)
        final TimeStamp destNtpTime = TimeStamp.getNtpTime(destTimeMillis);
        System.out.println(" Destination Timestamp:\t" + destNtpTime + "  " + destNtpTime.toDateString());

        info.computeDetails(); // compute offset/delay if not already done
        final Long offsetMillis = info.getOffset();
        final Long delayMillis = info.getDelay();
        final String delay = delayMillis == null ? "N/A" : delayMillis.toString();
        final String offset = offsetMillis == null ? "N/A" : offsetMillis.toString();

        System.out.println(" Roundtrip delay(ms)=" + delay
                + ", clock offset(ms)=" + offset); // offset in ms
        return info;
    }

	/*
    @RequestMapping("/yearly/requestByIP")
    Object getYearlyRequestByIP(){    	
    	return sparkService.getYearlyRequestByIP();
    }
    @RequestMapping("/yearly/requestByUserIP")
    Object getYearlyRequestByUserIP(){
    	return sparkService.getYearlyRequest("srcip");
    	//return sparkService.getYearlyRequestByUserIP();
    }
    @Cacheable("yearlyRequestByCountry")
    @RequestMapping("/yearly/requestByCountry")
    Object getYearlyRequestByCountry(){
    	return sparkService.getYearlyRequest("srccountry");
    	//return sparkService.getYearlyRequestByCountry();
    }
    */
    
}
