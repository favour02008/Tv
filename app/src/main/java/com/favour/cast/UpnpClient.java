package com.favour.cast;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public final class UpnpClient {
    public static class Renderer {
        public final String name, location, control;
        Renderer(String n,String l,String c){name=n;location=l;control=c;}
    }
    public static ArrayList<Renderer> discover(int timeoutMs) throws Exception {
        ArrayList<Renderer> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        DatagramSocket s=new DatagramSocket(); s.setSoTimeout(500);
        String q="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n";
        byte[] b=q.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(b,b.length,InetAddress.getByName("239.255.255.250"),1900));
        long end=System.currentTimeMillis()+timeoutMs;
        while(System.currentTimeMillis()<end){
            try{
                byte[] x=new byte[8192]; DatagramPacket p=new DatagramPacket(x,x.length); s.receive(p);
                String h=new String(p.getData(),0,p.getLength(),StandardCharsets.UTF_8); String loc=header(h,"location");
                if(loc==null||!seen.add(loc)) continue;
                String xml=get(loc); Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                String name=text(d,"friendlyName"), svc=service(d,"urn:schemas-upnp-org:service:AVTransport:1");
                if(svc!=null) out.add(new Renderer(name==null?loc:name,loc,svc));
            }catch(SocketTimeoutException ignored){}
        }
        s.close(); return out;
    }
    private static String header(String h,String n){for(String l:h.split("\\r?\\n")) if(l.toLowerCase(Locale.US).startsWith(n.toLowerCase(Locale.US)+":")) return l.substring(l.indexOf(':')+1).trim(); return null;}
    private static String get(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);try(InputStream in=c.getInputStream()){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private static String text(Document d,String tag){NodeList n=d.getElementsByTagName(tag);return n.getLength()>0?n.item(0).getTextContent():null;}
    private static String service(Document d,String type){NodeList n=d.getElementsByTagName("service");for(int i=0;i<n.getLength();i++){Element e=(Element)n.item(i);Node st=e.getElementsByTagName("serviceType").item(0);Node cu=e.getElementsByTagName("controlURL").item(0);if(st!=null&&cu!=null&&type.equals(st.getTextContent()))return cu.getTextContent();}return null;}
    private static String resolve(String base,String path)throws Exception{return new URL(new URL(base),path).toString();}
    private static String controlUrl(Renderer r)throws Exception{return resolve(r.location,r.control);}
    public static void setUri(Renderer r,String uri)throws Exception{soap(r,"SetAVTransportURI","<InstanceID>0</InstanceID><CurrentURI>"+esc(uri)+"</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>");}
    public static void play(Renderer r)throws Exception{action(r,"Play");}
    public static void action(Renderer r,String a)throws Exception{soap(r,a,a.equals("Play")?"<InstanceID>0</InstanceID><Speed>1</Speed>":"<InstanceID>0</InstanceID>");}
    private static void soap(Renderer r,String action,String args)throws Exception{
        String body="<?xml version=\"1.0\" encoding=\"utf-8\"?>"+"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:"+action+" xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"+args+"</u:"+action+"></s:Body></s:Envelope>";
        byte[] b=body.getBytes(StandardCharsets.UTF_8); HttpURLConnection c=(HttpURLConnection)new URL(controlUrl(r)).openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(5000); c.setReadTimeout(5000); c.setRequestProperty("Content-Type","text/xml; charset=\"utf-8\""); c.setRequestProperty("SOAPACTION","\"urn:schemas-upnp-org:service:AVTransport:1#"+action+"\""); c.setFixedLengthStreamingMode(b.length);
        try(OutputStream o=c.getOutputStream()){o.write(b);} int code=c.getResponseCode(); if(code>=400) throw new IOException("TV returned HTTP "+code); c.disconnect();
    }
    private static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
