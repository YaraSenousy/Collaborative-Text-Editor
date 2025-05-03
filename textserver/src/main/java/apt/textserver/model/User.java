package apt.textserver.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    String userName;
    int cursorPosition;
    public User(String name,int cursorPos){
        this.userName=name;
        this.cursorPosition=cursorPos;
    }
}
