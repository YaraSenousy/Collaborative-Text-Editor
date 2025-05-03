package apt.textclient;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    String userName;
    int cursorPosition;
    boolean isConnected;
    public User(){};
    public User(String name,int cursorPos,boolean isconnected){
        this.userName=name;
        this.cursorPosition=cursorPos;
        this.isConnected=isconnected;

    }
}
