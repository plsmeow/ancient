package tech.onetap.util.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HWIDUtil {
    
    public static String generateHWID() {
        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            
            StringBuilder hwid = new StringBuilder();
            
            // CPU
            hwid.append(hal.getProcessor().getProcessorIdentifier().getProcessorID());
            
            // Motherboard
            hwid.append(hal.getComputerSystem().getBaseboard().getSerialNumber());
            
            // MAC Address
            if (!hal.getNetworkIFs().isEmpty()) {
                hwid.append(hal.getNetworkIFs().get(0).getMacaddr());
            }
            
            // Hash the HWID
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hwid.toString().getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "unknown";
        }
    }
    
    public static HardwareInfo getHardwareInfo() {
        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            
            String cpu = hal.getProcessor().getProcessorIdentifier().getName();
            String gpu = hal.getGraphicsCards().isEmpty() ? "Unknown" : 
                hal.getGraphicsCards().get(0).getName();
            String ram = String.format("%.2f GB", 
                hal.getMemory().getTotal() / (1024.0 * 1024.0 * 1024.0));
            
            return new HardwareInfo(cpu, gpu, ram);
        } catch (Exception e) {
            return new HardwareInfo("Unknown", "Unknown", "Unknown");
        }
    }
    
    @Data
    @AllArgsConstructor
    public static class HardwareInfo {
        private String cpu;
        private String gpu;
        private String ram;
    }
}
