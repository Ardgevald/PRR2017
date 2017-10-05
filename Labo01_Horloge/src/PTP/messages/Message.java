package PTP.messages;

/**
 *
 * @author Remi
 */
public abstract class Message {
    
    public Message(byte messageType){
	this.messageType = messageType;
    }
    
    private byte messageType;
    private byte[] fullData;
    
    
    public byte[] getBytes(){
	return fullData;
    }
    
    public class SyncMessage extends Message{
	
	public SyncMessage(byte messageType) {
	    super((byte)0);
	}
	
    }
    
    public abstract class IdMessage extends Message{
	private int id;
	
	public IdMessage(byte messageType, int id){
	    super(messageType);
	    this.id = id;
	}
    }
    
    public class FollowUpMessage extends IdMessage{
	
	public FollowUpMessage(int id) {
	    super((byte)1, id);
	}
	
    }
    
    public class DelayRequestMessage extends Message{
	public DelayRequestMessage() {
	    super((byte) 2);
	}
    }
    
    public class DelayResponseMessage extends IdMessage{	
	public DelayResponseMessage(int id) {
	    super((byte) 3, id);
	}
	
    }
    
}
