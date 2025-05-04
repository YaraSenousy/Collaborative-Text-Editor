package apt.textclient;

import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Cursor {
    Color color;
    Rectangle rect;

    public Cursor() {

    }

    public Cursor(Color color, Rectangle rect){
        this.color=color;
        this.rect=rect;
    }
}
