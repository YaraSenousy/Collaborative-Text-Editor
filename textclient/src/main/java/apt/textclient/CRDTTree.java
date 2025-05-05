package apt.textclient;
import lombok.Getter;
import lombok.Setter;

import javax.net.ssl.SSLSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ConcurrentHashMap<String, Node> nodeMap = new ConcurrentHashMap<>();
    private final Map<String, List<Node>> orphanedNodes = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    //private final Queue<Node> pendingNodes = new LinkedList<>();
    public CRDTTree() {
        this.root = new Node("system", 0, "", 'R',0);
        nodeMap.put(root.id, root);
    }


    public void insert(Node newNode) {
        lock.writeLock().lock();
        try {
            // Process the new node
            if (!tryInsert(newNode)) {
                orphanedNodes.computeIfAbsent(newNode.getParentId(), k -> new ArrayList<>()).add(newNode);
            }

            // Check if the newly inserted node's id is a parentId for any orphaned nodes
            if (nodeMap.containsKey(newNode.getId())) {
                processOrphanedNodes(newNode.getId());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean tryInsert(Node newNode) {
        Node existingNode = nodeMap.get(newNode.id);
        if (existingNode != null) {
            if (existingNode.isDeleted) {
                existingNode.isDeleted = false;
                return true;
            } else {
                return true;
            }
        }

        Node parent = nodeMap.get(newNode.parentId);
        if (parent == null) {
            return false; // Parent not found, defer insertion
        }

        parent.children.add(newNode);
        nodeMap.put(newNode.id, newNode);
        return true;
    }

    private void processOrphanedNodes(String parentId) {
        List<Node> orphans = orphanedNodes.remove(parentId);
        if (orphans != null) {
            for (Node orphan : orphans) {
                if (!tryInsert(orphan)) {
                    // If the parent still isn't found (unlikely since we check nodeMap), re-add to orphanedNodes
                    orphanedNodes.computeIfAbsent(orphan.getParentId(), k -> new ArrayList<>()).add(orphan);
                } else {
                    // Recursively check if this newly inserted node is a parent for other orphans
                    processOrphanedNodes(orphan.getId());
                }
            }
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
            return nodes;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return null;
        }
    }

    public Node getRoot() { return root;
    }
}