package apt.textserver.model;

import com.sun.javafx.geom.Rectangle;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;

@Getter
@Setter
public class User {
    String userName;
    int cursorPosition;
    boolean isConnected;
    String color;
    public User(String name,int cursorPos,boolean isconnected){
        this.userName=name;
        this.cursorPosition=cursorPos;
        this.isConnected=isconnected;
    }
}
