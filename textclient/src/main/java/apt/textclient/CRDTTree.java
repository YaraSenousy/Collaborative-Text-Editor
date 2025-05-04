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
                System.out.println("Parent not found for node " + newNode.getId() + ", adding to orphaned nodes");
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
                System.out.println("Node exists but is deleted, reviving: " + newNode.getId());
                existingNode.isDeleted = false;
                return true;
            } else {
                System.out.println("Node already exists and not deleted, skipping: " + newNode.getId());
                return true;
            }
        }

        Node parent = nodeMap.get(newNode.parentId);
        if (parent == null) {
            return false; // Parent not found, defer insertion
        }

        System.out.println("Attempting to insert node: content=" + newNode.getContent() + ", id=" + newNode.getId() + ", parentId=" + newNode.getParentId());
        parent.children.add(newNode);
        nodeMap.put(newNode.id, newNode);
        System.out.println("Inserted node: " + newNode.getContent() + " under parent " + parent.getId());
        return true;
    }

    private void processOrphanedNodes(String parentId) {
        List<Node> orphans = orphanedNodes.remove(parentId);
        if (orphans != null) {
            for (Node orphan : orphans) {
                System.out.println("Processing orphaned node: content=" + orphan.getContent() + ", id=" + orphan.getId() + ", parentId=" + orphan.getParentId());
                if (!tryInsert(orphan)) {
                    // If the parent still isn't found (unlikely since we check nodeMap), re-add to orphanedNodes
                    System.out.println("Parent still not found for node " + orphan.getId() + ", re-adding to orphaned nodes");
                    orphanedNodes.computeIfAbsent(orphan.getParentId(), k -> new ArrayList<>()).add(orphan);
                } else {
                    // Recursively check if this newly inserted node is a parent for other orphans
                    processOrphanedNodes(orphan.getId());
                }
            }
        }
    }
//public void insert(Node newNode) {
//    lock.writeLock().lock();
//    try {
//        // Process the new node
//        if (!tryInsert(newNode)) {
//            System.out.println("Parent not found for node " + newNode.getId() + ", adding to pending queue");
//            pendingNodes.add(newNode);
//        }
//
//        // Retry inserting pending nodes
//        Queue<Node> stillPending = new LinkedList<>();
//        while (!pendingNodes.isEmpty()) {
//            Node pendingNode = pendingNodes.poll();
//            if (!tryInsert(pendingNode)) {
//                stillPending.add(pendingNode);
//            }
//        }
//        pendingNodes.addAll(stillPending);
//    } finally {
//        lock.writeLock().unlock();
//    }
//}
//
//    private boolean tryInsert(Node newNode) {
//        Node existingNode = nodeMap.get(newNode.id);
//        if (existingNode != null) {
//            if (existingNode.isDeleted) {
//                System.out.println("Node exists but is deleted, reviving: " + newNode.getId());
//                existingNode.isDeleted = false;
//                return true;
//            } else {
//                System.out.println("Node already exists and not deleted, skipping: " + newNode.getId());
//                return true;
//            }
//        }
//
//        Node parent = nodeMap.get(newNode.parentId);
//        if (parent == null) {
//            return false; // Parent not found, defer insertion
//        }
//
//        System.out.println("Attempting to insert node: content=" + newNode.getContent() + ", id=" + newNode.getId() + ", parentId=" + newNode.getParentId());
//        parent.children.add(newNode);
//        nodeMap.put(newNode.id, newNode);
//        System.out.println("Inserted node: " + newNode.getContent() + " under parent " + parent.getId());
//        return true;
//    }


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