package com.favour.cast;

import java.io.*;import java.net.*;
/** Lightweight HTTP server for future local-file casting. */
public class LocalMediaServer { private ServerSocket socket; public void stop(){try{if(socket!=null)socket.close();}catch(Exception ignored){}} }
