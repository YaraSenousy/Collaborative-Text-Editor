# Collaborative Text Editor

## Overview
This is a collaborative text editing application built using JavaFX for the frontend and Spring WebSocket for the backend. It allows multiple users to edit a shared document in real-time, with features such as:
- Real-time text editing using a CRDT (Conflict-free Replicated Data Type) for consistency.
- User presence tracking (list of connected users with cursor positions).
- Commenting system (users can add comments on text ranges, which are highlighted when clicked).
- Undo/Redo functionality for text edits.
- Exporting the document as a text file.

The application consists of:
- **Client**: A JavaFX application (`SessionController`) that provides the UI for editing and commenting.
- **Server**: A Spring Boot application with WebSocket support (`WebSocketController`) for real-time communication.
- **CRDT**: A custom `CRDTTree` implementation to handle concurrent edits.

## Prerequisites
To run this application, you need the following installed on your system:
1. **Java Development Kit (JDK)**: Version 17 or higher.
2. **Maven**: For dependency management and building the project.
3. **JavaFX SDK**: Required for the client UI.
   - Download from: https://gluonhq.com/products/javafx/
   - Alternatively, use Maven to include JavaFX dependencies.
4. **Spring Boot**: The server uses Spring Boot for WebSocket communication.
   - Included as a Maven dependency.
5. **IDE**: IntelliJ IDEA, Eclipse, or any IDE with JavaFX and Maven support (optional but recommended).
6. **WebSocket Client Library**: The client uses `spring-messaging` and `spring-websocket` for WebSocket communication (included via Maven).

## Setup Instructions

### 1. Clone the Repository
Clone or download the project to your local machine.

### 2. Configure Dependencies
Ensure you have a `pom.xml` file with the necessary dependencies.
Run mvn install to download dependencies.

## Running the Application
### 1. Start the Server
1. Navigate to the project directory.
2. Run the Spring Boot server with the following command:
   - mvn spring-boot:run
3. The server will start on localhost:8080 and expose a WebSocket endpoint at ws://localhost:8080/ws.

### 2. Start the Client
1. Ensure the server is running.
2. Run the JavaFX client with the following command:
   - mvn javafx:run
3. If using IntelliJ:
   - Naviate to the main JavaFX application class and click the run button.


## This project was a part of the Advanced Programming Techniques course in the Cairo University, Faculty of Engineering

## Credits: 

* George Ayman
* Jana Meriden
* Moaaz Emam
* Yara Senousy


    
