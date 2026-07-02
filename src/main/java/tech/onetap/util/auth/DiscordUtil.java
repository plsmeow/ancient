package tech.onetap.util.auth;

import lombok.Data;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.IPCUser;

/**
 * Discord utility for getting current user information
 * Uses meteordevelopment DiscordIPC library
 */
public class DiscordUtil {
    private static final long APP_ID = 1459904567465476160L;
    
    /**
     * Get Discord info from running Discord client via IPC
     */
    public static DiscordInfo getDiscordInfo() {
        System.out.println("[OneTap Auth] Connecting to Discord IPC...");
        
        try {
            // Start Discord IPC connection
            boolean connected = DiscordIPC.start(APP_ID, null);
            
            if (!connected) {
                throw new Exception("Failed to connect to Discord");
            }
            
            // Wait for connection to establish and receive READY event
            Thread.sleep(1000);
            
            // Get current user from Discord IPC
            IPCUser user = DiscordIPC.getUser();
            
            // Stop connection (we only needed user info)
            DiscordIPC.stop();
            
            if (user != null && user.id != null) {
                System.out.println("[OneTap Auth] ✓ Successfully connected to Discord!");
                System.out.println("[OneTap Auth] - ID: " + user.id);
                System.out.println("[OneTap Auth] - Username: " + user.username);
                
                return new DiscordInfo(user.id, user.username, user.avatar);
            }
            
            throw new Exception("User info not received from Discord");
            
        } catch (Exception e) {
            System.err.println("[OneTap Auth] Error connecting to Discord: " + e.getMessage());
            
            // Make sure to stop connection on error
            try {
                DiscordIPC.stop();
            } catch (Exception ignored) {}
        }
        
        // Discord not running or connection failed
        System.err.println("[OneTap Auth] ✗ Failed to connect to Discord!");
        System.err.println("[OneTap Auth]");
        System.err.println("[OneTap Auth] Please make sure:");
        System.err.println("[OneTap Auth] 1. Discord is running");
        System.err.println("[OneTap Auth] 2. You are logged in to Discord");
        System.err.println("[OneTap Auth] 3. Discord is not running in browser (desktop app required)");
        System.err.println("[OneTap Auth]");
        System.err.println("[OneTap Auth] Then restart OneTap client.");
        
        return null;
    }
    
    @Data
    public static class DiscordInfo {
        private final String id;
        private final String username;
        private final String avatar;
    }
}
