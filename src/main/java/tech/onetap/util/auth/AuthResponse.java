package tech.onetap.util.auth;

import lombok.Data;

@Data
public class AuthResponse {
    private boolean authorized;
    private int uid;
    private String discord_username;
    private String token;
    private long expires_at;
    private int hwid_resets_left;
    private String client_version;
    private String reason;
    private String message;
    private String[] allowed_versions;
}
