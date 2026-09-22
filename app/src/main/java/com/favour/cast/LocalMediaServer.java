package com.favour.cast;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.util.*;

public class LocalMediaServer {
    private final ContentResolver resolver; private final Uri uri; private ServerSocket socket; private Thread thread; private volatile boolean running;
    public LocalMediaServer(Context c,Uri u){resolver=c.getContentResolver();uri=u;}
    public void start() throws IOException {socket=new ServerSocket(8765);running=true;thread=new Thread(()->{while(running){try{final Socket s=socket.accept();new Thread(()->serve(s)).start();}catch(IOException e){if(running)e.printStackTrace();}}});thread.start();}
    public String getUrl(){return "http://"+localIp()+":8765/video";}
    private String localIp(){try{Enumeration<NetworkInterface> es=NetworkInterface.getNetworkInterfaces();while(es.hasMoreElements()){NetworkInterface ni=es.nextElement();Enumeration<InetAddress> as=ni.getInetAddresses();while(as.hasMoreElements()){InetAddress a=as.nextElement();if(!a.isLoopbackAddress()&&a instanceof Inet4Address)return a.getHostAddress();}}}catch(Exception ignored){}return "127.0.0.1";}
    private void serve(Socket s){try{BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream()));String line;long start=0;while((line=r.readLine())!=null&&!line.isEmpty()){if(line.startsWith("Range:")){String v=line.substring(6).trim();if(v.startsWith("bytes=")){String n=v.substring(6).split("-")[0];try{start=Long.parseLong(n);}catch(Exception ignored){}}}}
        long length=-1;try(AssetFileDescriptor afd=resolver.openAssetFileDescriptor(uri,"r")){if(afd!=null)length=afd.getLength();}
        if(length<0)throw new IOException("Unknown media length"); long end=length-1; if(start>=length)start=0;
        OutputStream out=s.getOutputStream();String h=(start>0?"HTTP/1.1 206 Partial Content":"HTTP/1.1 200 OK")+"\r\nContent-Type: video/mp4\r\nAccept-Ranges: bytes\r\nContent-Length: "+(end-start+1)+"\r\n"+(start>0?"Content-Range: bytes "+start+"-"+end+"/"+length+"\r\n":"")+"Connection: close\r\n\r\n";out.write(h.getBytes("UTF-8"));
        try(InputStream in=resolver.openInputStream(uri)){if(in==null)throw new IOException("Cannot open video");long skip=start;while(skip>0){long n=in.skip(skip);if(n<=0)break;skip-=n;}byte[] buf=new byte[8192];long left=end-start+1;while(left>0){int n=in.read(buf,0,(int)Math.min(buf.length,left));if(n<0)break;out.write(buf,0,n);left-=n;}}
        out.flush();s.close();
    }catch(Exception e){try{s.close();}catch(Exception ignored){}}}
    public void stop(){running=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}}
}
