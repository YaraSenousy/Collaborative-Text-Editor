import java.util.ArrayList;
import java.util.List;
public class App {
    public static void main(String[] args) throws Exception {
        CRDTTree document = new CRDTTree();
        Node node1 = new Node("alice", 1, document.root.id, 'A', 0);
        

        document.insert(node1);
        Node node2 = new Node("alice", 3, document.root.id, 'b', 0);

        document.insert(node2);
        Node node3 = new Node("alice", 4, "alice-3", ' ', 0);
        document.insert(node3);
        Node node4 = new Node("bob", 4, "alice-3", 'E', 0);
        document.insert(node4);

        
        List<Character> list = document.traverse();
        for (char c : list){
            System.out.print(c);
        }
        System.out.println();

        document.delete("bob-4");
        list = document.traverse();
        for (char c : list){
            System.out.print(c);
        }
        System.out.println();
        Node node5 = new Node("bob", 5, "bob-4", 's', 0);
        document.insert(node5);
        list = document.traverse();
        for (char c : list){
            System.out.print(c);
        }
        System.out.println();
        document.export("file1");

        CRDTTree document2 = new CRDTTree();
        ArrayList<Node> nodes = document2.importFile("a", 0, "file");
        
        for (Node c : nodes){
            System.out.print(c.content);
        }
    }
}
