package apt.textclient;
import lombok.Getter;
import lombok.Setter;

import javax.net.ssl.SSLSession;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@Getter
@Setter
public class CRDTTree {
    public final Node root;
    private final Map<String, Node> nodeMap = new HashMap<>();
    //private final Map<String, List<Node>> orphans = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CRDTTree() {
        this.root = new Node("system", 0, "", 'R',0);
        nodeMap.put(root.id, root);
    }


    public void insert(Node newNode) {
        lock.writeLock().lock();
        try {

            System.out.println("Attempting to insert node: " + newNode.getContent() + ", id: " + newNode.getId() + ", parentId: " + newNode.getParentId());
            Node existingNode = nodeMap.get(newNode.id);
            if (existingNode != null) {
                if (existingNode.isDeleted) {
                    System.out.println("Node exists but is deleted, reviving: " + newNode.getId());
                    existingNode.isDeleted = false;
                    return;
                } else {
                    System.out.println("Node already exists and not deleted, skipping: " + newNode.getId());
                    return;
                }
            }

            Node parent = nodeMap.get(newNode.parentId);
            if (parent == null) {
                System.out.println("Parent not found for node " + newNode.getId() + ", re-parenting to root");
                parent = root;
                newNode.setParentId(root.getId());
            }

            parent.children.add(newNode);
            nodeMap.put(newNode.id, newNode);
            System.out.println("Inserted node: " + newNode.getContent() + " under parent " + newNode.getParentId());
        } finally {
            lock.writeLock().unlock();
        }
    }


    public void delete(String nodeId) {
        lock.writeLock().lock();
        try {
            Node node = nodeMap.get(nodeId);
            if (node != null) {
                node.isDeleted = true;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Character> traverse() {
        lock.readLock().lock();
        try {
            List<Character> result = new ArrayList<>();
            traverseDFS(root, result);
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void traverseDFS(Node node, List<Character> result) {
        if (node == null) return;
        for (Node child : node.children) {
            if (!child.isDeleted)
            {
                result.add(child.content);
            }
            traverseDFS(child, result);
        }
    }

    public boolean export(String filename){
        List<Character> list = this.traverse();
        lock.readLock().lock();
        try{
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
                for (char c : list) {
                    writer.write(c);
                }
                writer.flush();
                System.out.println("Successfully wrote to file.");
                return true;
            } catch (IOException e) {
                System.err.println("Error writing to file: " + e.getMessage());
                return false;
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    public ArrayList <Node> importFile(String userId, long initialTimestamp, String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String parentId = this.root.id;
            long timestamp = initialTimestamp;
            ArrayList<Node> nodes = new ArrayList<>();
            int charCode;
            while ((charCode = reader.read()) != -1) { // Read each character
                char c = (char) charCode;
                Node newNode = new Node(userId, timestamp++, parentId, c, 0);
                nodes.add(newNode); // Add to the list of nodes
                this.insert(newNode);
                // Update parent to the newly inserted node
                parentId = userId + "-" + (timestamp - 1);
            }
            System.out.println("Successfully inserted from " + filename);
            return nodes;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return null;
        }
    }

    public Node getRoot() { return root;
    }
}