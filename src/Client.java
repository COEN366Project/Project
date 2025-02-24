
import java.io.IOException;
import java.net.*;
import java.util.*;


public class Client {

    public static void main(String args[]) throws IOException {

        Scanner sc = new Scanner(System.in);
        DatagramSocket ds = new DatagramSocket();
        InetAddress ip = InetAddress.getLocalHost();

        while(true){

            String msg = sc.nextLine();

            byte[] buffer = msg.getBytes();

            DatagramPacket send = new DatagramPacket(buffer, buffer.length,ip,5000);
            ds.send(send);

            if(msg.equals("x")){
                break;
            }

        }
        
        ds.close();
        sc.close();

    }
}
