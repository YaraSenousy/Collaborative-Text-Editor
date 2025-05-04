package apt.textserver.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

// Custom serializer for Color
class ColorSerializer extends JsonSerializer<Color> {
    @Override
    public void serialize(Color color, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (color == null) {
            gen.writeNull();
            return;
        }
        String hex = String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
        gen.writeString(hex);
    }
}

// Custom deserializer for Color
class ColorDeserializer extends JsonDeserializer<Color> {
    @Override
    public Color deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String hex = p.getText();
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            int rgb = Integer.parseInt(hex, 16);
            return new Color(
                    ((rgb >> 16) & 0xFF) / 255.0,
                    ((rgb >> 8) & 0xFF) / 255.0,
                    (rgb & 0xFF) / 255.0,
                    1.0
            );
        } catch (NumberFormatException e) {
            throw new IOException("Invalid hex color format: " + hex, e);
        }
    }
}

// Custom serializer for Rectangle
class RectangleSerializer extends JsonSerializer<Rectangle> {
    @Override
    public void serialize(Rectangle rectangle, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (rectangle == null) {
            gen.writeNull();
            return;
        }
        gen.writeStartObject();
        gen.writeNumberField("x", rectangle.getX());
        gen.writeNumberField("y", rectangle.getY());
        gen.writeNumberField("width", rectangle.getWidth());
        gen.writeNumberField("height", rectangle.getHeight());
        gen.writeEndObject();
    }
}

// Custom deserializer for Rectangle
class RectangleDeserializer extends JsonDeserializer<Rectangle> {
    @Override
    public Rectangle deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.getCurrentToken() == null) {
            p.nextToken();
        }
        if (p.getCurrentToken().isScalarValue()) {
            return null; // Handle null values
        }
        double x = 0, y = 0, width = 0, height = 0;
        while (p.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
            String fieldName = p.getCurrentName();
            p.nextToken(); // Move to field value
            switch (fieldName) {
                case "x":
                    x = p.getDoubleValue();
                    break;
                case "y":
                    y = p.getDoubleValue();
                    break;
                case "width":
                    width = p.getDoubleValue();
                    break;
                case "height":
                    height = p.getDoubleValue();
                    break;
                default:
                    throw new IOException("Unknown field in Rectangle: " + fieldName);
            }
        }
        return new Rectangle(x, y, width, height);
    }
}

// Jackson configuration to register the custom serializers and deserializers
@Configuration
public class JacksonConfig {
    @Bean
    public com.fasterxml.jackson.databind.Module javafxModule() {
        SimpleModule module = new SimpleModule("JavaFXModule");
        module.addSerializer(Color.class, new ColorSerializer());
        module.addDeserializer(Color.class, new ColorDeserializer());
        module.addSerializer(Rectangle.class, new RectangleSerializer());
        module.addDeserializer(Rectangle.class, new RectangleDeserializer());
        return module;
    }
}