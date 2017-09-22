
package utils;

/**
 *
 */
public class Message {

   byte[] buffer;
   
   public Message(String m) {
      buffer = m.getBytes();
   }

   public Message(byte[] buffer) {
      this.buffer = buffer;
   }
   
   
   
}
