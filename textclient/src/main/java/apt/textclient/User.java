package apt.textclient;

import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    String userName;
    int cursorPosition;
    boolean isConnected;
    Rectangle cursor;
    Color color;
    public User(){};
    public User(String name,int cursorPos,boolean isconnected){
        this.userName=name;
        this.cursorPosition=cursorPos;
        this.isConnected=isconnected;
    }
}
