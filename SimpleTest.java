import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.types.YArray;
import com.triibiotech.yjs.types.YMap;
import com.triibiotech.yjs.types.YText;

public class SimpleTest {
    public static void main(String[] args) {
        System.out.println("Starting simple Yjs4j test...");
        
        try {
            // Test basic Doc creation
            Doc doc = new Doc();
            System.out.println("✅ Doc created successfully");
            
            // Test YArray
            YArray<String> array = doc.getArray("test-array");
            array.push("item1", "item2");
            System.out.println("✅ YArray operations successful");
            
            // Test YMap
            YMap<String> map = doc.getMap("test-map");
            map.set("key1", "value1");
            System.out.println("✅ YMap operations successful");
            
            // Test YText
            YText text = doc.getText("test-text");
            text.insert(0, "Hello World");
            System.out.println("✅ YText operations successful");
            
            // Test toJSON
            Object json = doc.toJSON();
            System.out.println("✅ toJSON successful: " + json);
            
            System.out.println("🎉 All basic tests passed!");
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}