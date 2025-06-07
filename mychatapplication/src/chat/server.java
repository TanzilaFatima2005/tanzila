/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package chat;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 *
 * @author Tanzila Fatima
 */
public class server extends javax.swing.JFrame {
    // ServerSocket to listen for incoming client connections on specified IP and port
          private ServerSocket serverSocket;
    // Global synchronized list of all client output streams (to send messages to clients)
   private final List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>();

    // List to track currently active client IP addresses

   private final List<String> activeClients = new CopyOnWriteArrayList<>();
    // List to hold references to ClientHandler threads managing each client
private final List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
    // Local ArrayList for client threads (optional, can merge with above)

ArrayList<ClientHandler> clientList = new ArrayList<>();
    // Flag to control server running state and stopping the server thread safely

private volatile boolean serverRunning = false;
       // Thread to run the server listening loop

private Thread serverThread;

static Socket s;
static ServerSocket ss;
static DataInputStream dis;
static DataOutputStream dout;
static PrintWriter writer;
static BufferedReader reader;
    /**
     * Creates new form server
     */
    public server() {
     initComponents();          // Initialize GUI components (text areas, buttons)
     setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
     // Add shutdown hook to clean up resources
Runtime.getRuntime().addShutdownHook(new Thread(() -> stopServer()));
    
startServerThread();       // Start server socket listener in a separate thread
    }
    
private void stopServer() {
    try {
        serverRunning = false; // Stop the accept loop

        // Close all connected client threads and sockets
        for (ClientHandler handler : clientHandlers) {
            try {
                handler.clientSocket.close();  // Close client socket
                handler.interrupt();            // Interrupt thread if blocked
                appendMessage("[INFO] Closed connection for client.");

            } catch (IOException ex) {
                appendMessage("[ERROR] Couldn't close client: " + ex.getMessage());
            }
        }
        clientHandlers.clear();

        // Close the server socket to release port and break accept()
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
            appendMessage("[INFO] Server socket closed.");
        }

        appendMessage("[INFO] Server stopped successfully.");

    } catch (IOException e) {
        appendMessage("[ERROR] Failed to stop server: " + e.getMessage());
    }
}







    // ========== ClientHandler handles communication with one client ==========
   class ClientHandler extends Thread {
       
    private final Socket clientSocket;   // The client socket this thread manages
        private BufferedReader reader;       // To read client messages
        private PrintWriter writer;          // To send messages to client
        private String clientID;             // Optional: unique identifier for client


    

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
                // Don't add to clientWriters until client ID confirmed
appendMessage("[INFO] ClientHandler initialized for " + clientSocket.getInetAddress().getHostAddress());

        } catch (IOException e) {
            appendMessage("[ERROR] ClientHandler init: " + e.getMessage());
        }
    } public void closeConnection() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
appendMessage("[INFO] Closed connection for client.");
        } catch (IOException e) {
appendMessage("[ERROR] Closing client connection: " + e.getMessage());
    }
    }
  @Override
public void run() {
    try {
        String msg;
        int malformedCount = 0;

        while ((msg = reader.readLine()) != null) {
            msg = msg.trim();

            // 🛑 1. Handle empty message
            if (msg.isEmpty()) {
                sendErrorResponse("Empty message received.");
                if (++malformedCount > 5) {
                    writer.println("msg:Too many empty messages. Disconnecting.");
                    break;
                }
                continue;
            }

            // 🛑 2. Handle exit
            if ("exit".equalsIgnoreCase(msg) || msg.toLowerCase().startsWith("msg:exit")) {
                writer.println("msg:Goodbye! Connection closed.");
                appendMessage("[INFO] Client requested disconnection.");
                break;
            }

            // 🛑 3. Message must contain colon
            if (!msg.contains(":")) {
                sendErrorResponse("Malformed message. Use format: msg:<your_message>");
                if (++malformedCount > 5) {
                    writer.println("msg:Too many malformed messages. Disconnecting.");
                    break;
                }
                continue;
            }

            // ✅ 4. Safe split and validate
            String[] parts = msg.split(":", 2);
            String msgType = parts[0].trim().toLowerCase();
            String msgContent = parts.length == 2 ? parts[1].trim() : "";

            if (msgContent.isEmpty()) {
                sendErrorResponse("Connection closed.");
                if (++malformedCount > 5) {
                    writer.println("msg:Too many invalid messages. Disconnecting.");
                    break;
                }
                continue;
            }

            // ✅ 5. Handle known message type
            if ("msg".equals(msgType)) {

                if ("__empty__".equalsIgnoreCase(msgContent)) {
                    appendMessage("[WARNING] Client sent flagged empty message.");
                    writer.println("msg:[Warning] You sent an empty message.");
                    continue;
                }

                if ("__invalid__".equalsIgnoreCase(msgContent)) {
                    appendMessage("[WARNING] Client sent flagged invalid message.");
                    writer.println("msg:[Warning] You sent an invalid message.");
                    continue;
                }

                // ✅ 6. Server-side validation
                if (isValidMessageContent(msgContent)) {
                    String clientIP = clientSocket.getInetAddress().getHostAddress();
                    appendMessage("[INFO Client [" + clientIP + "]: " + msgContent);

                    // Smart reply
                    String reply = generateSmartReply(msgContent);
                    appendMessage("YOU: " + reply);
                    writer.println("msg:" + reply); // ✅ Send reply to client
                } else {
                    sendErrorResponse("Message contains unsupported or unsafe content.");
                    if (++malformedCount > 5) {
                        writer.println("msg:Too many suspicious messages. Disconnecting.");
                        break;
                    }
                }

            } else {
                // 🛑 7. Unknown message type
                sendErrorResponse("Unknown message type: '" + msgType + "'");
                appendMessage("[WARNING] Unknown message type from " + clientSocket.getInetAddress().getHostAddress() + ": " + msgType);

                if (++malformedCount > 5) {
                    writer.println("msg:Too many unknown message types. Disconnecting.");
                    appendMessage("[WARNING] Malformed message received from " + clientSocket.getInetAddress().getHostAddress() + ": " + msg);
                    break;
                }
            }
        }

    } catch (IOException e) {
        appendMessage("[WARNING] Client disconnected or I/O error: " + e.getMessage());

    } finally {
        try {
            String clientIP = clientSocket.getInetAddress().getHostAddress();
            activeClients.remove(clientIP);
            clientWriters.remove(writer);
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
            appendMessage("[INFO] Client disconnected: " + clientIP);
            appendMessage("[SESSION] Active clients: " + activeClients.size());
        } catch (IOException e) {
            appendMessage("[ERROR] Failed to close client socket: " + e.getMessage());
        }
    }
}

/// Updated Content Validator
private boolean isValidMessageContent(String content) {
    content = content.toLowerCase(); // Make case-insensitive
    return content.matches("^[a-z0-9 ?!.,]+$"); // Allow only safe printable characters
}

//  Standardized Error Sender
private void sendErrorResponse(String errorMsg) {
    writer.println("msg:[Error] " + errorMsg);
    appendMessage("[WARNING] " + errorMsg); // Replaced logEvent with appendMessage
}



private void cleanupClientResources() {
    try {
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        activeClients.remove(clientIP);
        clientWriters.remove(writer);

        if (!clientSocket.isClosed()) {
            clientSocket.close();
        }

        appendMessage("[INFO] Client disconnected: " + clientIP);
        appendMessage("[INFO] Active clients: " + activeClients.size());

    } catch (IOException e) {
        appendMessage("[ERROR] During client cleanup: " + e.getMessage());
    }
}


// 🔍 Generate basic smart replies based on keywords
private String generateSmartReply(String userMsg) {
    if (userMsg.contains("hello") || userMsg.contains("hi")) {
        return "Hello! How can I assist you today?";
    } else if (userMsg.contains("how are you")) {
        return "I'm just a server, but I'm running fine!";
    } else if (userMsg.contains("bye")) {
        return "Goodbye! Have a great day!";
    } else if (userMsg.contains("time")) {
        return "Server time is: " + java.time.LocalTime.now().toString();
    } else if (userMsg.contains("date")) {
        return "Today is: " + java.time.LocalDate.now().toString();
    } else {
        return "Thank you for your message!";
    }
}
}











    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        msg_send = new javax.swing.JButton();
        msg_text = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        msg_area = new javax.swing.JTextArea();
        stop_server = new javax.swing.JButton();

        msg_send.setText("send");
        msg_send.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                msg_sendActionPerformed(evt);
            }
        });

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel1.setText("Server");

        msg_area.setColumns(20);
        msg_area.setRows(5);
        jScrollPane1.setViewportView(msg_area);

        stop_server.setText("Stop");
        stop_server.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stop_serverActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(26, 26, 26)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(stop_server)
                        .addGap(18, 18, 18))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addGap(34, 363, Short.MAX_VALUE))))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 218, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(stop_server)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void msg_sendActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_msg_sendActionPerformed
        // TODO add your handling code here:
            // GUI action when server user presses "send" button

      // Manual sending disabled
    // You can show a message or just do nothing
    
    }//GEN-LAST:event_msg_sendActionPerformed

    private void stop_serverActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stop_serverActionPerformed
        // TODO add your handling code here:
           stopServer();

    }//GEN-LAST:event_stop_serverActionPerformed

    /**
     * @param args the command line arguments
     */
        // ... (GUI init code omitted for brevity, but unchanged)

    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(server.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(server.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(server.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(server.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
                // Launch the GUI form in the Event Dispatch Thread

        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new server().setVisible(true);
            }
        }); 
    }

    

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private static javax.swing.JTextArea msg_area;
    private javax.swing.JButton msg_send;
    private javax.swing.JTextField msg_text;
    private javax.swing.JButton stop_server;
    // End of variables declaration//GEN-END:variables

   // ========== 1. Binding to specific IP + port and listening ==========
private void startServerThread() {
    serverRunning = true;

    new Thread(() -> {
        try {
            serverSocket = new ServerSocket(1234, 0, InetAddress.getByName("127.0.0.1"));

            appendMessage("[INFO] Server started on 127.0.0.1:1234");

            boolean waitingShown = false;  // To control one-time waiting message

            while (!serverSocket.isClosed()) {

                if (!waitingShown) {
                    appendMessage("[INFO] Waiting for client to connect...");
                    waitingShown = true;

                    // Small delay to allow GUI to repaint before blocking
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                Socket clientSocket = serverSocket.accept();  // Blocks here
                String clientIP = clientSocket.getInetAddress().getHostAddress();

                activeClients.add(clientIP);
                appendMessage("[INFO] Client connected: " + clientIP);
                appendMessage("[SESSION] Active clients: " + activeClients.size());

                ClientHandler clientThread = new ClientHandler(clientSocket);
                clientHandlers.add(clientThread);
                clientThread.start();
            }

        } catch (IOException e) {
            if (serverRunning) {
                appendMessage("[ERROR] " + e.getMessage());
            } else {
                appendMessage("[INFO] Server stopped accepting connections.");
            }
        }
    }).start();
}


// ========== Helper method to check if message is valid ==========
 private boolean messageSahiHai(String msg) {
    if (msg == null || msg.trim().isEmpty()) {
        return false;
    }

    msg = msg.trim();

    // Allow only messages starting with "msg:" or exact "exit"
    if (!(msg.startsWith("msg:") || msg.equalsIgnoreCase("exit"))) {
        return false;
    }

    // Limit length to 512 chars
    if (msg.length() > 512) {
        return false;
    }

    // Only printable ASCII, tab, newline allowed
    return msg.chars().allMatch(ch -> (ch >= 32 && ch <= 126) || ch == '\n' || ch == '\t');
}




// Thread-safe method to append message to JTextArea in the GUI
private static void appendMessage(String msg) {
    SwingUtilities.invokeLater(() -> {
        msg_area.append(msg + "\n");
    });
}


public void logEvent(String level, String message) {
    String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    String logMsg = "[" + timeStamp + "] [" + level + "] " + message + "\n";

    // Append safely on GUI thread
    SwingUtilities.invokeLater(() -> {
        msg_area.append(logMsg);
    });

    // Append to log file
    try (FileWriter fw = new FileWriter("server.log", true)) {
        fw.write(logMsg);
    } catch (IOException e) {
        // Log file write failure also shown on GUI, use invokeLater to be thread safe
        SwingUtilities.invokeLater(() -> {
            msg_area.append("[ERROR] Failed to write to log file: " + e.getMessage() + "\n");
        });
    }
}

}


