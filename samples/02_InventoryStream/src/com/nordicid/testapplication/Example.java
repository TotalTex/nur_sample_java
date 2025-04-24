package com.nordicid.testapplication;

import com.nordicid.samples.common.SamplesCommon;
//import com.nordicid.samples.common.Timestamp;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiListener;

import com.nordicid.nurapi.NurEventAutotune;
import com.nordicid.nurapi.NurEventClientInfo;
import com.nordicid.nurapi.NurEventDeviceInfo;
import com.nordicid.nurapi.NurEventEpcEnum;
import com.nordicid.nurapi.NurEventFrequencyHop;
import com.nordicid.nurapi.NurEventIOChange;
import com.nordicid.nurapi.NurEventInventory;
import com.nordicid.nurapi.NurEventNxpAlarm;
import com.nordicid.nurapi.NurEventProgrammingProgress;
import com.nordicid.nurapi.NurEventTagTrackingChange;
import com.nordicid.nurapi.NurEventTagTrackingData;
import com.nordicid.nurapi.NurEventTraceTag;
import com.nordicid.nurapi.NurEventTriggeredRead;
import com.nordicid.nurapi.NurRespReaderInfo;
import com.nordicid.nurapi.NurTag;
import com.nordicid.nurapi.NurTagStorage;
import com.nordicid.nurapi.NurSetup;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.io.*;
import java.net.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Duration;



/**
 * This example shows how to run continuous inventory in asynchronous stream.
 * - Inventory is used to read multiple tag's EPC codes in reader field of view
 */

public class Example {
	
	// We store unique read tags in this storage
	static NurTagStorage uniqueTags = new NurTagStorage();
	
	// Zwischenspeicher vor UniqueTags
	static NurTagStorage listepufferuniqueTags = new NurTagStorage();
	
	// Puffer für Diff Abgleich
	//static NurTagStorage timeoutTags = new NurTagStorage();
	
	// List zum zählen wie oft ein Tag gelesen wurde
	 static ArrayList<String> rfidchips = new ArrayList<String>();
	
	//DetectedRound notwendig um akzeptiert gelesen zu sein
	static int DetectedRounds = 0;
	
	//Anzeige Version
	static String InventoryVersionNumber = "1.9";
	
	// API access
	static NurApi api = null;	
	
	// stream event count
	static int eventCount = 0;
	
	// stream event count atomic
	static AtomicInteger eventCountAtomic = new AtomicInteger(0);
	
	
	public static void main(String[] args) {
		String addr = "192.168.2.243";
		String mysql = "192.168.2.3:3306";
		
		
		if (args.length > 0)
		{
			addr = args[0];
			
			if ((args.length > 1) && (args[1] != ""))
			{
				mysql = args[1]; 
			}
		}
		Integer scan_laenge = 10;
		Integer Stop = 0;
		Integer Connect = 0;
		Integer Dbconnect = 0;
		
		
		
		
		Connection con = null;
		Statement stmt= null;  
		ResultSet rs = null;	
		System.out.println(String.format("Version: '%s'", InventoryVersionNumber));
while (Stop == 0)
		{	
		

			//eventCount = 0;
			//eventCountAtomic.set(0);
			
			try{  //192.168.116.128
				  //192.168.2.3
					if (Dbconnect == 0)
					{
						Class.forName("com.mysql.jdbc.Driver");  
						con=DriverManager.getConnection(  
						"jdbc:mysql://"+mysql+"/digiwash1","root","CATCAT");  
						stmt=con.createStatement();  
						Dbconnect = 1;	
					}
				}
			catch(Exception e)
			{
				System.out.println(e);
				Dbconnect = 0;
			}  		
			
			
			
			try {
				// Create and connect new NurApi object
				// To change connection parameters, please modify SamplesCommon.java
				if (Connect == 0)
				{
					api = SamplesCommon.createAndConnectNurApi(addr);
					api.setSetupReadTimeout(1000);
					System.out.println(api.getFileVersion());
					
					
					api.setListener(apiListener);
					

					Connect = 1;
				}
			} 
			catch (Exception ex) {
				System.out.println(ex);
//				ex.printStackTrace();
//				return;
				Connect = 0;
			}
			
			try {
				// Clear tag storage
				if ((Connect == 1) && (Dbconnect == 1))
				{	
						
					NurRespReaderInfo info = api.getReaderInfo();
					//api.setPerAntennaPower(antPower);
					stmt.executeUpdate(String.format("Insert ignore into Uhf_reader (ReaderID) VALUES ('%s')",info.altSerial));
					rs=stmt.executeQuery(String.format("select Fertig,Scan_laenge_in_sek,Read_Timeout_in_ms,Antennenpower,Antennen,Beep_bei_neuem_Chip,vorscan,IP_UDP_Server,QFaktor,InventorySession,InventoryTarget,inventoryrssimin,detectedrounds from Uhf_reader Where ReaderID = '%s' Limit 1",info.altSerial));  
					rs.first();
					int scan_fertig = rs.getInt(1);
					scan_laenge = rs.getInt(2);
					int read_timeout_ms = rs.getInt(3);
					int antennen_power = rs.getInt(4);
					String strantennen = rs.getString(5);
					int beep_bei_chip = rs.getInt(6);
					int vorscan = rs.getInt(7);
					String ip = rs.getString(8);
					int QFaktor = rs.getInt(9);
					int InventorySession = rs.getInt(10);
					int InventoryTarget  = rs.getInt(11);
					int InventoryRssiMin = rs.getInt(12);
					DetectedRounds = rs.getInt(13);
					String antennen_array[] = strantennen.split(";"); 
//					uniqueTags.clear();
					api.setSetupTxLevel(antennen_power);
					api.setSetupInventoryQ(QFaktor);
					api.setSetupInventorySession(InventorySession);
					api.setSetupInventoryTarget(InventoryTarget);
					//api.setSetupReadRssiFilter(0, 0);
					api.setSetupInventoryRssiFilter(InventoryRssiMin, 0);
					
					System.out.println(String.format("TX Level %s ",antennen_power));
					System.out.println(String.format("QFaktor %s ",QFaktor));
					System.out.println(String.format("InventorySession %s ",InventorySession));
					System.out.println(String.format("InventoryTarget %s ",InventoryTarget));
					System.out.println(String.format("read_timeout %s ",read_timeout_ms));
					System.out.println(String.format("rssi filter inventory min %s ",InventoryRssiMin));
					System.out.println(String.format("detectrounds %s ",DetectedRounds));
					
					int antennenflag = 0;
					int anzahl1 = 0;
					while (anzahl1 <= antennen_array.length -1 ) 
					{
						switch(antennen_array[anzahl1])
						{
						 case "0" : antennenflag |= NurApi.ANTENNAMASK_ALL;break;
						 case "1" : antennenflag |= NurApi.ANTENNAMASK_1;break;
						 case "2" : antennenflag |= NurApi.ANTENNAMASK_2;break;
						 case "3" : antennenflag |= NurApi.ANTENNAMASK_3;break;
						}
						anzahl1 ++;
								
					}
					api.setSetupAntennaMaskEx(antennenflag);
					//api.setSetupSelectedAntenna(-1);   // alle antennen benutzen

					// 1 = Intern
					// 2 = Aux1
					// 3 = Aux2
					// 4 = Aux3		
					if (vorscan == 1)
					{
						api.setSetupTxLevel(12);
						api.setSetupAntennaMaskEx(NurApi.ANTENNAMASK_1);
						read_timeout_ms = 1;
						
					}
				
					

					/*
					NurTagStorage apiStorageblub = api.getStorage();
					System.out.println(String.format("api size before %s", apiStorageblub.size() ));
					for (int n=0; n<apiStorageblub.size(); n++)
					{
						NurTag tagStatistik = apiStorageblub.get(n);
						int CountAusRFIDListe = Collections.frequency(rfidchips, tagStatistik.getEpcString());
						//System.out.println(String.format("# Tag Statistik '%s' RSSI %d '%s' Count '%s' Akzeptiert '%b' ", tagStatistik.getEpcString(), tagStatistik.getRssi(),tagStatistik.getAntennaId(),tagStatistik.getUpdateCount(),tagStatistik.getUpdateCount()>DetectedRounds ));
						System.out.println(String.format("# Tag Statistik '%s' RSSI %d '%s' Count '%s'  Countrfidliste '%s'  Akzeptiert '%b' EventcountClassic '%d' EventCountAtomic '%d' ", tagStatistik.getEpcString(), tagStatistik.getRssi(),tagStatistik.getAntennaId(),tagStatistik.getUpdateCount(),CountAusRFIDListe,((CountAusRFIDListe/eventCount)*100)>DetectedRounds,eventCount,eventCountAtomic.get() ));
					}
					
					for (int n=0; n<listepufferuniqueTags.size(); n++)
					{
						NurTag tagausListePuffer = listepufferuniqueTags.get(n);
						int CountAusRFIDListePuffer = Collections.frequency(rfidchips, tagausListePuffer.getEpcString());
						System.out.println(String.format("# Tag StatistikPuffer '%s' RSSI %d '%s' Count '%s'  Countrfidliste '%s'  Akzeptiert '%b' EventcountClassic '%d' EventCountAtomic '%d' ", tagausListePuffer.getEpcString(), tagausListePuffer.getRssi(),tagausListePuffer.getAntennaId(),tagausListePuffer.getUpdateCount(),CountAusRFIDListePuffer,((CountAusRFIDListePuffer/eventCount)*100)>DetectedRounds,eventCount,eventCountAtomic.get() ));
						
					}
					*/
					eventCount = 0;
					eventCountAtomic.set(0);
					api.clearIdBuffer(true);
					//NurTagStorage apiStorageblub2 = api.getStorage();
					//System.out.println(String.format("api size after %s", apiStorageblub2.size() ));
					
					//System.out.println(rfidchips.size());
					
					//System.out.println(rfidchips.size());
					
					uniqueTags.clear();
					listepufferuniqueTags.clear();
					rfidchips.clear();
					if ((scan_fertig == 0) || (vorscan == 1))
					{
/*
						if (scan_laenge > 0)
						{
							System.out.println(String.format("Starting inventory stream for %s secs",scan_laenge * 1000));
						}
						else
						{
							System.out.println(String.format("Starting inventory stream for %s secs",500));							
						}
*/						
						// Start inventory stream, see inventoryStreamEvent() below
						ZoneId MyTimeZone = ZoneOffset.UTC;
						Instant InventoryStart = Instant.now();
						api.startInventoryStream();
						
						
						// Let it run for 30 sec 30000
						if ((scan_laenge > 0) && (vorscan == 0))
						{
						//	System.out.println("Scan gestartet");
							Thread.sleep(scan_laenge * 1);
							api.stopInventoryStream();
							System.out.println("Scan zwischen");
						}
						else
						{
						//	System.out.println("Scan gestartet");
							if (vorscan == 1)
							{Thread.sleep(300);}
							else
							{Thread.sleep(500);}							
						}
						
//						System.out.println(String.format("Total %d stream events", eventCount));
//						System.out.println(String.format("Inventoried total %d unique tags", uniqueTags.size()));
						String str = "";
						str = "Insert into rfidbuffer (rfid,IDAntenne,rssi,ts) VALUES ";

						String sql = "('%s',left('%s_%s',45),%s,%s)";
						int anzahl = 0;
						boolean JAoderNein = false;						
						listepufferpruefen();
						while (anzahl <= uniqueTags.size() -1 ) 
						{
							
							long diff = 0;
							Timestamp timestampchip = null;
							Timestamp timestamp = null;
							if (uniqueTags.get(anzahl).getUserdata() != null)
							{
								String str1 = String.valueOf(uniqueTags.get(anzahl).getUserdata());
								SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
							    Date parsedDate = dateFormat.parse(str1);
							    timestampchip = new Timestamp(parsedDate.getTime());
							    timestamp = new Timestamp(System.currentTimeMillis());
							    diff = System.currentTimeMillis() - parsedDate.getTime();
							    if (diff >= read_timeout_ms) 
							    {
									System.out.println(String.format("Tag gelöscht: ZeitChip: %s Zeit: %s DiffMs: %d Chip: %s",timestampchip,timestamp,diff,uniqueTags.get(anzahl).getEpcString()));							    	
							    		//System.out.println(String.format("uniqueSize %s",uniqueTags.size()));							    	
									JAoderNein = uniqueTags.remove(anzahl);
									System.out.println(JAoderNein);
									//Thread.sleep(1000);
								//	apiStorage.removeTag(uniqueTags.get(anzahl).getEpcString());
								//	uniqueTags.removeTag(uniqueTags.get(anzahl).getEpcString());
									//System.out.println(String.format("uniqueSize %s",uniqueTags.size()));							    	
							    }
							}
							else
							if (uniqueTags.get(anzahl).getUserdata() == null)
							{								
								if (anzahl > 0) {str = str + " , ";}
								//System.out.println(String.format("Tag hinzugefügt: ZeitChip: %s Zeit: %s DiffMs: %d Chip: %s  DetectedCount '%d'",timestampchip,timestamp,diff,uniqueTags.get(anzahl).getEpcString(),uniqueTags.get(anzahl).getUpdateCount()));
								Instant ChipTimeStamp = InventoryStart.plus(Duration.ofMillis(uniqueTags.get(anzahl).getTimestamp()));
								LocalDateTime ldt = LocalDateTime.ofInstant(ChipTimeStamp, MyTimeZone);
								DateTimeFormatter mysqlFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
								String mysqlDateTime = ldt.format(mysqlFmt);
								uniqueTags.get(anzahl).setUserdata(new Timestamp(System.currentTimeMillis()));
								str = str + String.format(sql, uniqueTags.get(anzahl).getEpcString(),uniqueTags.get(anzahl).getAntennaId(),info.altSerial,uniqueTags.get(anzahl).getRssi(), "\"" + mysqlDateTime + "\"" );
								anzahl ++;
								if (beep_bei_chip == 1) {api.beep();}								
							}
						}
						
						if (anzahl > 0)
						{
//							if (scan_laenge > 0)
							{str = str + " ON DUPLICATE KEY UPDATE IDAntenne = Values(IDAntenne),rssi = values(rssi),ts = values(ts),Verarbeitet = 0";}
							try
							{
								//System.out.println(str);
								stmt.executeUpdate(str);
								
							}
							catch (Exception e)
							{
    							System.out.println(str);
							}
							
						}
//						uniqueTags.clear();
					//	SendUDPMySQLBereit("127.0.0.1",8888 );
						System.out.println("Scan beendet");
						if ((scan_laenge > 0) && (vorscan == 0))
						{
							stmt.executeUpdate(String.format("Update Uhf_reader Set Fertig = 1 Where ReaderID = '%s'",info.altSerial));
						}
						if (ip != "")
						{
						  BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
						  DatagramSocket clientSocket = new DatagramSocket();
						  InetAddress IPAddress = InetAddress.getByName(ip);
						  byte[] sendData = new byte[1024];
						  String sentence = info.altSerial;
						  sendData = sentence.getBytes();

						  

						  DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 6666);
						  clientSocket.send(sendPacket);
						 // DatagramPacket sendPacket2 = new DatagramPacket(sendData, sendData.length, IPAddress, 1122);
						  //clientSocket.send(sendPacket2);
						  
						  
						  clientSocket.close();
						  
						}
					}
				}
				
			}
			catch (Exception ex) {
				System.out.println(ex);
				Connect = 0;
				Dbconnect = 0;
				
			}
	        if (Connect == 0)
	        {
				try {
					// Disconnect the connection
					api.disconnect();
				} catch (Exception e) {
					e.printStackTrace();
				}
				// Dispose the NurApi
				api.dispose();
	        }
	        if (Dbconnect == 0)
	        {
			try{  
				con.close(); 
				con = null;
				}
			catch(Exception e){ System.out.println(e);}
	        }
		}
	}
	
	public static void SendUDPMySQLBereit(String aIP,int aPort)  {
		  DatagramSocket SocketSendUDPMySQLBereit = null;
		try {
			SocketSendUDPMySQLBereit = new DatagramSocket();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		  InetAddress MyNewIPAddressSendUDPMySQLBereit = null;
		try {
			MyNewIPAddressSendUDPMySQLBereit = InetAddress.getByName(aIP);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		  
		  String Befehl = "o";		  
		  DatagramPacket PacketSendUDPMySQLBereit = new DatagramPacket(Befehl.getBytes(), Befehl.length(), MyNewIPAddressSendUDPMySQLBereit, aPort);
		  try {
			SocketSendUDPMySQLBereit.send(PacketSendUDPMySQLBereit);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		  SocketSendUDPMySQLBereit.close();		
	}
	
	public static void listepufferpruefen() {
		for (int n=0; n<listepufferuniqueTags.size(); n++) {
			NurTag TagListePufferPruefen = listepufferuniqueTags.get(n);
			int WieOftGelesen = Collections.frequency(rfidchips, TagListePufferPruefen.getEpcString());
			double WieOftGelesenDouble = WieOftGelesen;
			double eventCountDouble = eventCount;
			double ProzentualZuRunden = (WieOftGelesenDouble/eventCountDouble)*100;
			double DetectedRoundsDouble = DetectedRounds;
			boolean HaeufigGenugGelesen = ProzentualZuRunden>DetectedRoundsDouble;
			if (HaeufigGenugGelesen) 
			{		
				uniqueTags.addTag(TagListePufferPruefen);
				System.out.println(String.format("# Tag StatistikListePuffer '%s' RSSI %d '%s' Count '%s'  Countrfidliste '%s' Prozentual '%f' Akzeptiert '%b' EventcountClassic '%d' EventCountAtomic '%d' ",
						TagListePufferPruefen.getEpcString(), TagListePufferPruefen.getRssi(),TagListePufferPruefen.getAntennaId(),TagListePufferPruefen.getUpdateCount(),WieOftGelesen,ProzentualZuRunden,true,eventCount,eventCountAtomic.get() ));
			}
			else
			{
				System.out.println(String.format("# Tag StatistikListePuffer '%s' RSSI %d '%s' Count '%s'  Countrfidliste '%s' Prozentual '%f'  Akzeptiert '%b' EventcountClassic '%d' EventCountAtomic '%d' ",
						TagListePufferPruefen.getEpcString(), TagListePufferPruefen.getRssi(),TagListePufferPruefen.getAntennaId(),TagListePufferPruefen.getUpdateCount(),WieOftGelesen,ProzentualZuRunden,false,eventCount,eventCountAtomic.get() ));
			}
			
			
		}
	}
	
	
	static NurApiListener apiListener = new NurApiListener() {
		
		@Override
		public void triggeredReadEvent(NurEventTriggeredRead arg0) {
		}
		
		@Override
		public void traceTagEvent(NurEventTraceTag arg0) {
		}
		
		@Override
		public void programmingProgressEvent(NurEventProgrammingProgress arg0) {
		}
		
		@Override
		public void logEvent(int arg0, String arg1) {
		}
		
		// This event is fired when ever reader completes inventory round and tags are read 
		// NOTE: Depending on reader settings, tag amount and environment this event maybe fired very frequently
		@Override
		public void inventoryStreamEvent(NurEventInventory arg0) {
			eventCount++;
			eventCountAtomic.getAndIncrement();
			NurTagStorage apiStorage = api.getStorage();
			
			// When accessing api owned storage from event, we need to lock it
				synchronized (apiStorage) 
				{
					try
					{
						// Add inventoried tags to our unique tag storage
						for (int n=0; n<apiStorage.size(); n++) 
						{
							NurTag tag = apiStorage.get(n);							
							//int occurrences = tag.getUpdateCount();
							rfidchips.add(tag.getEpcString());
							int occurrences2 = Collections.frequency(rfidchips, tag.getEpcString());
							//if (occurrences2<2)
							//System.out.println(String.format("# Tag Info '%s' RSSI %d '%s' CountUpdatedApi '%s' COuntrfidchipliste '%s' ", tag.getEpcString(), tag.getRssi(),tag.getAntennaId(),occurrences,occurrences2 ));
							//if (occurrences2>DetectedRounds) 
							{
								//System.out.println(String.format("# Tag Info '%s' RSSI %d '%s' Count '%s'", tag.getEpcString(), tag.getRssi(),tag.getAntennaId(),occurrences ));
								
							
								
							
							listepufferuniqueTags.addTag(tag);
							
							/*
							if (uniqueTags.addTag(tag)) 
							{
								
										//System.out.println(String.format(uniqueTags.size() + "# New unique tag '%s' RSSI %d '%s' updated: '%s'", tag.getEpcString(), tag.getRssi(),tag.getAntennaId(),tag.getUpdateCount()));
										//System.out.println(String.format("# bla  RSSI  '%s'", occurrences));
								
								
							}
							*/
							}
							
						}

						
						  DatagramSocket mySocket = new DatagramSocket();
						  byte[] ChipCount = new byte[1024];
						  int ChipCountNew = apiStorage.size();
						  String ChipCountStr = new String();
						  ChipCountStr = ChipCountStr.valueOf(ChipCountNew);
						  ChipCount = ChipCountStr.getBytes();
						  String MyLocalIP = new String();
						  MyLocalIP = "127.0.0.1";
						  InetAddress MyNewIPAddress = InetAddress.getByName(MyLocalIP);
						  DatagramPacket sendCount = new DatagramPacket(ChipCount, ChipCount.length, MyNewIPAddress, 1123);
						  mySocket.send(sendCount);
						  mySocket.close();
						

					}					
					catch (Exception e)
					{
				
					}
										
				}


			// If stream stopped, restart
			if (arg0.stopped) {
				try {
					System.out.println("Restarting stream");
					api.startInventoryStream();
				} catch (Exception e) {
					e.printStackTrace();
					
				}
			}
		}
		
		@Override
		public void inventoryExtendedStreamEvent(NurEventInventory arg0) {
		}
		
		@Override
		public void frequencyHopEvent(NurEventFrequencyHop arg0) {
		}
		
		@Override
		public void disconnectedEvent() {
		}
		
		@Override
		public void deviceSearchEvent(NurEventDeviceInfo arg0) {
		}
		
		@Override
		public void debugMessageEvent(String arg0) {
		}
		
		@Override
		public void connectedEvent() {
		}
		
		@Override
		public void clientDisconnectedEvent(NurEventClientInfo arg0) {
		}
		
		@Override
		public void clientConnectedEvent(NurEventClientInfo arg0) {
		}
		
		@Override
		public void bootEvent(String arg0) {
		}
		
		@Override
		public void IOChangeEvent(NurEventIOChange arg0) {
		}

		@Override
		public void autotuneEvent(NurEventAutotune arg0) {
		}

		@Override
		public void epcEnumEvent(NurEventEpcEnum arg0) {
		}

		@Override
		public void nxpEasAlarmEvent(NurEventNxpAlarm arg0) {
		}

		@Override
		public void tagTrackingChangeEvent(NurEventTagTrackingChange arg0) {
		}

		@Override
		public void tagTrackingScanEvent(NurEventTagTrackingData arg0) {
		}
	};
}
